package xyz.mdhv.formanalyser.app.data

import android.content.Context
import androidx.room.withTransaction
import java.util.UUID
import xyz.mdhv.formanalyser.scoring.*

class ScoringRepository(context: Context) {
    private val db = AppDatabase.get(context.applicationContext)
    private val scoring = db.scoringDao()
    private val features = db.athleteFeatureDao()
    private val athletes = db.athleteDao()
    private val rigs = db.rigDao()

    data class Snapshot(val session: ScoreSessionEntity, val card: Scorecard)
    suspend fun currentAthlete(): AthleteEntity? = athletes.firstOrNull()
    suspend fun resumeActive(): Snapshot? { val athlete=athletes.firstOrNull()?:return null; val active=scoring.activeForAthlete(athlete.id)?:return null; return snapshot(active.id) }
    suspend fun quickStart(): Snapshot { val athlete=athletes.firstOrNull()?:error("No athlete profile"); scoring.activeForAthlete(athlete.id)?.let{return snapshot(it.id)}; val template=scoring.latestPinned(athlete.id)?:scoring.latest(athlete.id); return start(template?.toRoundDefinition()?:RoundPack.WA_RECURVE_70M_72,template?.pinned==true) }
    suspend fun start(round:RoundDefinition,pinned:Boolean=false):Snapshot { val athlete=athletes.firstOrNull()?:error("No athlete profile"); val rig=rigs.activeForAthlete(athlete.id); val now=System.currentTimeMillis(); val e=ScoreSessionEntity(UUID.randomUUID().toString(),athlete.id,rig?.id,roundId=round.id,roundName=round.name,distanceMeters=round.distanceMeters,targetFaceCm=round.targetFaceCm,arrowsPerEnd=round.arrowsPerEnd,endCount=round.endCount,scoringKind=round.scoringKind.name,faceLayout=round.faceLayout.name,startedAt=now,updatedAt=now,pinned=pinned); scoring.upsertSession(e); return Snapshot(e,Scorecard(round)) }
    suspend fun snapshot(sessionId:String):Snapshot=db.withTransaction { val s=scoring.session(sessionId)?:error("Score session not found: $sessionId"); Snapshot(s,buildCard(s,scoring.activeArrows(sessionId),scoring.opponentEnds(sessionId))) }
    suspend fun recordNumeric(sessionId:String,score:ArrowScore)=record(sessionId,score,null,ScoreSource.MANUAL_NUMERIC)
    suspend fun recordPlot(sessionId:String,point:PlotPoint)=record(sessionId,null,point,ScoreSource.MANUAL_PLOT)
    private suspend fun record(sessionId:String,numericScore:ArrowScore?,plot:PlotPoint?,source:ScoreSource,authority:AuthorityState=AuthorityState.HUMAN_CONFIRMED,resolution:ObservationResolution=ObservationResolution.SHOT_CONFIRMED):Snapshot=db.withTransaction { val cur=snapshotUnlocked(sessionId); check(cur.session.status=="ACTIVE"); val score=numericScore?:scoreFromPlot(plot?:error("Plot point required"),cur.card.round.faceLayout); if(cur.card.round.faceLayout!=FaceLayout.SINGLE&&score.points in 1..5) error("Triple-face scoring accepts 6-10 or miss"); val next=cur.card.record(UUID.randomUUID().toString(),score,plot,source,authority,resolution); scoring.upsertArrow(next.arrows.last().toEntity(sessionId,System.currentTimeMillis())); scoring.updateFrom(next,sessionId); Snapshot(scoring.session(sessionId)!!,next) }
    suspend fun undo(sessionId:String):Snapshot=db.withTransaction { val cur=snapshotUnlocked(sessionId); val last=cur.card.arrows.lastOrNull()?:return@withTransaction cur; scoring.retractArrow(last.id,System.currentTimeMillis()); val next=cur.card.undoLast(); scoring.updateFrom(next,sessionId); Snapshot(scoring.session(sessionId)!!,next) }
    suspend fun setOpponentEndTotal(sessionId:String,endIndex:Int,total:Int):Snapshot=db.withTransaction { val cur=snapshotUnlocked(sessionId); scoring.upsertOpponentEnd(ScoreOpponentEndEntity(sessionId,endIndex,total,System.currentTimeMillis())); val next=cur.card.withOpponentEndTotal(endIndex,total); scoring.updateFrom(next,sessionId); Snapshot(scoring.session(sessionId)!!,next) }
    suspend fun setShootOffWinner(sessionId:String,winner:SetMatchSummary.Winner):Snapshot=db.withTransaction { val cur=snapshotUnlocked(sessionId); val next=cur.card.withShootOffWinner(winner); scoring.setShootOffWinner(sessionId,winner.name,System.currentTimeMillis()); scoring.updateFrom(next,sessionId); Snapshot(scoring.session(sessionId)!!,next) }
    suspend fun togglePinned(sessionId:String):Snapshot=db.withTransaction { val s=scoring.session(sessionId)?:error("Score session not found"); scoring.setPinned(sessionId,!s.pinned,System.currentTimeMillis()); snapshotUnlocked(sessionId) }
    suspend fun updateContext(sessionId:String,sightMark:String?,venue:String?,conditions:String?,trainingIntent:String?):Snapshot=db.withTransaction { scoring.updateContext(sessionId,sightMark.clean(),venue.clean(),conditions.clean(),trainingIntent.clean(),System.currentTimeMillis()); snapshotUnlocked(sessionId) }
    suspend fun finish(sessionId:String):Pair<Snapshot,Int?> = db.withTransaction { val cur=snapshotUnlocked(sessionId); val pb=if(cur.card.round.scoringKind==ScoringKind.SET_MATCH)null else scoring.previousBest(cur.session.athleteId,cur.session.roundId,sessionId); scoring.finish(sessionId,cur.card.isComplete(),System.currentTimeMillis()); snapshotUnlocked(sessionId) to pb }

    data class EndScanCandidate(val points:Int,val isX:Boolean=false,val plot:PlotPoint?=null,val confidence:Double?=null)
    suspend fun proposeEndScanCandidates(sessionId:String,endIndex:Int,candidates:List<EndScanCandidate>): List<ScoreCandidateEntity> = db.withTransaction { val now=System.currentTimeMillis(); candidates.mapIndexed { i,c -> require(c.points in 0..10); ScoreCandidateEntity(UUID.randomUUID().toString(),sessionId,endIndex,i,c.points,c.isX,c.plot?.x,c.plot?.y,c.plot?.faceIndex,c.confidence?.coerceIn(0.0,1.0),createdAtMs=now).also{features.upsertCandidate(it)} } }
    suspend fun endScanCandidates(sessionId:String,endIndex:Int)=features.candidatesForEnd(sessionId,endIndex)
    suspend fun confirmEndScanCandidate(candidateId:String,sessionId:String,endIndex:Int):Snapshot=db.withTransaction { val c=features.candidatesForEnd(sessionId,endIndex).firstOrNull{it.id==candidateId}?:error("End Scan candidate not found"); check(c.status=="PROPOSED"); val cur=snapshotUnlocked(sessionId); val plot=if(c.plotX!=null&&c.plotY!=null)PlotPoint(c.plotX,c.plotY,c.plotFaceIndex?:0) else null; val next=cur.card.record(UUID.randomUUID().toString(),ArrowScore(c.points,c.isX),plot,ScoreSource.END_SCAN,AuthorityState.HUMAN_CONFIRMED,ObservationResolution.END_ONLY); scoring.upsertArrow(next.arrows.last().toEntity(sessionId,System.currentTimeMillis())); scoring.updateFrom(next,sessionId); features.resolveCandidate(candidateId,"CONFIRMED",System.currentTimeMillis()); Snapshot(scoring.session(sessionId)!!,next) }
    suspend fun rejectEndScanCandidate(candidateId:String){features.resolveCandidate(candidateId,"REJECTED",System.currentTimeMillis())}
    suspend fun recordObserverTap(sessionId:String,ring:Int,isX:Boolean=false,sector:String?=null):Snapshot=db.withTransaction { require(ring in 0..10); val now=System.currentTimeMillis(); val e=ObserverScoreEventEntity(UUID.randomUUID().toString(),sessionId,now,ring,isX,sector,"TAP",declaredText=if(isX)"X" else if(ring==0)"M" else ring.toString()); features.upsertObserverEvent(e); val cur=snapshotUnlocked(sessionId); val next=cur.card.record(UUID.randomUUID().toString(),ArrowScore(ring,isX),null,ScoreSource.LIVE_OBSERVER,AuthorityState.HUMAN_CONFIRMED,ObservationResolution.SHOT_INFERRED); scoring.upsertArrow(next.arrows.last().toEntity(sessionId,now)); scoring.updateFrom(next,sessionId); Snapshot(scoring.session(sessionId)!!,next) }
    suspend fun observerEvents(sessionId:String)=features.observerEvents(sessionId)
    suspend fun recent(limit:Int=20):List<ScoreSessionEntity>{val a=athletes.firstOrNull()?:return emptyList(); return scoring.recent(a.id,limit)}
    private suspend fun snapshotUnlocked(sessionId:String):Snapshot { val s=scoring.session(sessionId)?:error("Score session not found: $sessionId"); return Snapshot(s,buildCard(s,scoring.activeArrows(sessionId),scoring.opponentEnds(sessionId))) }
    private fun buildCard(s:ScoreSessionEntity,a:List<ScoreArrowEntity>,o:List<ScoreOpponentEndEntity>)=Scorecard(s.toRoundDefinition(),a.map{it.toCore()},o.associate{it.endIndex to it.total},s.shootOffWinner?.let(SetMatchSummary.Winner::valueOf))
    private suspend fun ScoringDao.updateFrom(card:Scorecard,sessionId:String){val m=card.setMatchSummary(); updateSummary(sessionId,card.total,card.xCount,m?.athleteSetPoints,m?.opponentSetPoints,System.currentTimeMillis())}
}

fun ScoreSessionEntity.toRoundDefinition()=RoundDefinition(roundId,roundName,distanceMeters,targetFaceCm,arrowsPerEnd,endCount,ScoringKind.valueOf(scoringKind),FaceLayout.valueOf(faceLayout))
private fun ScoredArrow.toEntity(sessionId:String,now:Long)=ScoreArrowEntity(id,sessionId,endIndex,arrowIndex,score.points,score.isX,plot?.x,plot?.y,plot?.faceIndex,source.name,authority.name,resolution.name,now)
private fun ScoreArrowEntity.toCore()=ScoredArrow(id,endIndex,arrowIndex,ArrowScore(points,isX),if(plotX!=null&&plotY!=null)PlotPoint(plotX,plotY,plotFaceIndex?:0) else null,ScoreSource.valueOf(source),AuthorityState.valueOf(authority),ObservationResolution.valueOf(resolution))
private fun String?.clean()=this?.trim()?.takeIf{it.isNotEmpty()}
