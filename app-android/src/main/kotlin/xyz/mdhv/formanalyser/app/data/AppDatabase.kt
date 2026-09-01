package xyz.mdhv.formanalyser.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities =
        [
            AthleteEntity::class,
            SessionEntity::class,
            RigEntity::class,
            ShotEntity::class,
            // Phase 2 — wellness + life layer
            CheckinEntity::class,
            SorenessEntity::class,
            RestDayEntity::class,
            HiatusEntity::class,
            MoodEntity::class,
            LifeEventEntity::class,
            CycleEntity::class,
            MedicationEntity::class,
            EventEntity::class,
            // Phase 3 — body layer
            PainLogEntity::class,
            InjuryEntity::class,
            PhysioPlanEntity::class,
            PhysioExerciseEntity::class,
            PhysioSessionEntity::class,
            DocumentEntity::class,
            ScoreSessionEntity::class,
            ScoreArrowEntity::class,
            ScoreOpponentEndEntity::class,
            GoalEntity::class,
            InterventionEntity::class,
            SessionDefaultEntity::class,
            SessionContextEntity::class,
            TrainingPlanEntity::class,
            ScoreCandidateEntity::class,
            ObserverScoreEventEntity::class,
        ],
    version = 8,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun athleteDao(): AthleteDao

    abstract fun sessionDao(): SessionDao

    abstract fun rigDao(): RigDao

    abstract fun shotDao(): ShotDao

    abstract fun wellnessDao(): WellnessDao

    abstract fun bodyDao(): BodyDao

    abstract fun scoringDao(): ScoringDao

    abstract fun athleteFeatureDao(): AthleteFeatureDao

    companion object {
        private const val DB_NAME = "form-analyser.db"
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance
                ?: synchronized(this) {
                    instance ?: openResilient(context.applicationContext).also { instance = it }
                }

        private fun build(app: Context) =
            Room.databaseBuilder(app, AppDatabase::class.java, DB_NAME)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                )
                // No fallbackToDestructiveMigration: a missing migration path should surface as a
                // thrown exception into the catch below (and get backed up + recorded), not vanish
                // into Room's own silent wipe. See openResilient's kdoc for the full rationale.
                .build()

        /**
         * Open the database, forcing the (potentially migrating) SQLite open to happen *here* under
         * a guard instead of deep inside a coroutine where it would take the whole app down. The
         * app has been installed over itself across many incrementally-schema'd builds, so a legacy
         * DB with no clean migration path — or a migration that runs but throws, or fails Room's
         * post-migration validation — is a real possibility.
         *
         * Recovery is deliberately **not silent**: per the Phased Implementation Plan's review ("a
         * production build must never silently delete athlete history"), a failure here backs up
         * the raw database file via [DbRecovery.backUpBeforeReset] *before* deleting anything, then
         * records the event with [DbRecovery.markReset] so the UI can tell the athlete, after the
         * fact, that a reset happened and where the backup lives. A blocking pre-open confirmation
         * isn't possible — this runs synchronously before any Activity/Compose context exists — so
         * "preserve the bytes, then disclose" is the practical version of "never silently delete"
         * at this point in the app lifecycle. Real users on a correct migration chain never hit
         * this branch.
         */
        private fun openResilient(app: Context): AppDatabase {
            val db = build(app)
            return try {
                db.openHelper.writableDatabase // triggers open + migrations now
                db
            } catch (t: Throwable) {
                runCatching { db.close() }
                val backup = DbRecovery.backUpBeforeReset(app, DB_NAME)
                DbRecovery.markReset(app, backup)
                app.deleteDatabase(DB_NAME)
                build(app).also { it.openHelper.writableDatabase }
            }
        }

        /**
         * V1 → V2 (Phase 1): athlete profile columns, the rig table, session rig/handedness
         * columns, and a backfill giving every existing athlete a default active rig (tuning seeded
         * from their most recent session's draw weight) with sessions repointed to it. Column names
         * are camelCase to match Room's default derivation for the incumbent entities.
         */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE athletes ADD COLUMN handedness TEXT NOT NULL DEFAULT 'RH'"
                    )
                    db.execSQL("ALTER TABLE athletes ADD COLUMN drawLengthMm INTEGER")
                    db.execSQL(
                        "ALTER TABLE athletes ADD COLUMN avatarSeed INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL("ALTER TABLE athletes ADD COLUMN club TEXT")
                    db.execSQL("ALTER TABLE athletes ADD COLUMN pubkey TEXT")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `rig` (`id` TEXT NOT NULL, `athleteId` TEXT NOT NULL, `name` TEXT NOT NULL, `bowType` TEXT NOT NULL, `tuningJson` TEXT, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_rig_athleteId` ON `rig` (`athleteId`)"
                    )
                    db.execSQL("ALTER TABLE sessions ADD COLUMN rigId TEXT")
                    db.execSQL("ALTER TABLE sessions ADD COLUMN handednessOverride TEXT")
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_sessions_rigId` ON `sessions` (`rigId`)"
                    )
                    db.execSQL(
                        "UPDATE athletes SET avatarSeed = (abs(random()) % 1000000000) + 1 WHERE avatarSeed = 0"
                    )
                    val ids = mutableListOf<String>()
                    db.query("SELECT id FROM athletes").use { c ->
                        while (c.moveToNext()) ids.add(c.getString(0))
                    }
                    val now = System.currentTimeMillis()
                    for (aid in ids) {
                        var marked: Double? = null
                        db.query(
                                "SELECT drawWeightLbs FROM sessions WHERE athleteId = ? ORDER BY startedAtEpochMs DESC LIMIT 1",
                                arrayOf<Any?>(aid),
                            )
                            .use { c ->
                                if (c.moveToFirst() && !c.isNull(0)) marked = c.getDouble(0)
                            }
                        val tuning = marked?.let { "{\"v\":0,\"markedLbs\":$it}" }
                        val rid = "rig_$aid"
                        db.execSQL(
                            "INSERT INTO rig (id, athleteId, name, bowType, tuningJson, active, createdAt) VALUES (?,?,?,?,?,1,?)",
                            arrayOf<Any?>(rid, aid, "My bow", "RECURVE", tuning, now),
                        )
                        db.execSQL(
                            "UPDATE sessions SET rigId = ? WHERE athleteId = ?",
                            arrayOf<Any?>(rid, aid),
                        )
                    }
                }
            }
        /**
         * V2 → V3 (Phase 2): wellness + life-layer tables and session check-in/duration columns.
         */
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `checkin` (`id` TEXT NOT NULL, `athleteId` TEXT NOT NULL, `ts` INTEGER NOT NULL, `kind` TEXT NOT NULL, `skipped` INTEGER NOT NULL, `energy` INTEGER, `sleep` INTEGER, `motivation` INTEGER, `rpe` REAL, `feel` INTEGER, `note` TEXT, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_checkin_athleteId` ON `checkin` (`athleteId`)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_checkin_ts` ON `checkin` (`ts`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `soreness` (`checkinId` TEXT NOT NULL, `regionId` TEXT NOT NULL, PRIMARY KEY(`checkinId`, `regionId`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `rest_day` (`date` TEXT NOT NULL, `planned` INTEGER NOT NULL, `note` TEXT, PRIMARY KEY(`date`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `hiatus` (`id` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT, `lifeEventId` TEXT, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `mood_entry` (`id` TEXT NOT NULL, `ts` INTEGER NOT NULL, `mood` INTEGER NOT NULL, `tagsJson` TEXT NOT NULL, `note` TEXT, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_mood_entry_ts` ON `mood_entry` (`ts`)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `life_event` (`id` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT, `category` TEXT NOT NULL, `impact` INTEGER NOT NULL, `title` TEXT NOT NULL, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `cycle_entry` (`id` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT, `flow` INTEGER, `symptomsJson` TEXT NOT NULL, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `medication_entry` (`id` TEXT NOT NULL, `ts` INTEGER NOT NULL, `name` TEXT NOT NULL, `dose` TEXT, `schedule` TEXT, `taken` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_medication_entry_ts` ON `medication_entry` (`ts`)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `event` (`id` TEXT NOT NULL, `ts` INTEGER NOT NULL, `title` TEXT NOT NULL, `icon` TEXT, `tagsJson` TEXT NOT NULL, PRIMARY KEY(`id`))"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_ts` ON `event` (`ts`)")
                    db.execSQL("ALTER TABLE sessions ADD COLUMN preCheckinId TEXT")
                    db.execSQL("ALTER TABLE sessions ADD COLUMN postCheckinId TEXT")
                    db.execSQL("ALTER TABLE sessions ADD COLUMN durationAutoS INTEGER")
                    db.execSQL("ALTER TABLE sessions ADD COLUMN durationS INTEGER")
                    db.execSQL("ALTER TABLE sessions ADD COLUMN arrowsActual INTEGER")
                }
            }
        /** V3 → V4 (Phase 3): body layer — pain, injuries, physio, encrypted documents. */
        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `pain_log` (`id` TEXT NOT NULL, `athleteId` TEXT NOT NULL, `ts` INTEGER NOT NULL, `regionId` TEXT NOT NULL, `intensity` INTEGER NOT NULL, `tagsJson` TEXT NOT NULL, `injuryId` TEXT, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_pain_log_athleteId` ON `pain_log` (`athleteId`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_pain_log_regionId` ON `pain_log` (`regionId`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_pain_log_ts` ON `pain_log` (`ts`)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `injury` (`id` TEXT NOT NULL, `athleteId` TEXT NOT NULL, `onset` TEXT NOT NULL, `regionsJson` TEXT NOT NULL, `severity` INTEGER NOT NULL, `mechanism` TEXT NOT NULL, `status` TEXT NOT NULL, `resolvedDate` TEXT, `notes` TEXT, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_injury_athleteId` ON `injury` (`athleteId`)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `physio_plan` (`id` TEXT NOT NULL, `athleteId` TEXT NOT NULL, `title` TEXT NOT NULL, `targetRegionsJson` TEXT NOT NULL, `scheduleJson` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT, `source` TEXT NOT NULL, `notes` TEXT, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_physio_plan_athleteId` ON `physio_plan` (`athleteId`)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `physio_exercise` (`id` TEXT NOT NULL, `planId` TEXT NOT NULL, `name` TEXT NOT NULL, `sets` INTEGER NOT NULL, `reps` INTEGER, `holdS` INTEGER, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_physio_exercise_planId` ON `physio_exercise` (`planId`)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `physio_session` (`id` TEXT NOT NULL, `planId` TEXT NOT NULL, `ts` INTEGER NOT NULL, `completedJson` TEXT NOT NULL, `note` TEXT, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_physio_session_planId` ON `physio_session` (`planId`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_physio_session_ts` ON `physio_session` (`ts`)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `document` (`id` TEXT NOT NULL, `athleteId` TEXT NOT NULL, `ts` INTEGER NOT NULL, `title` TEXT NOT NULL, `mime` TEXT NOT NULL, `encPath` TEXT NOT NULL, `sha256` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `injuryId` TEXT, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_document_athleteId` ON `document` (`athleteId`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_document_injuryId` ON `document` (`injuryId`)"
                    )
                }
            }
        /**
         * V4 → V5 (0.6.0): interruption-safe manual Recurve scoring — `score_session`,
         * `score_arrow` and `score_opponent_end`. Arrows are append-only: undo retracts (`active =
         * 0`) rather than deleting, so a scorecard keeps its full audit trail for later
         * correction/provenance work.
         */
        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `score_session` (`id` TEXT NOT NULL, `athleteId` TEXT NOT NULL, `rigId` TEXT, `linkedFormSessionId` TEXT, `roundId` TEXT NOT NULL, `roundName` TEXT NOT NULL, `distanceMeters` INTEGER NOT NULL, `targetFaceCm` INTEGER NOT NULL, `arrowsPerEnd` INTEGER NOT NULL, `endCount` INTEGER NOT NULL, `scoringKind` TEXT NOT NULL, `faceLayout` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `completedAt` INTEGER, `status` TEXT NOT NULL, `roundComplete` INTEGER NOT NULL, `total` INTEGER NOT NULL, `xCount` INTEGER NOT NULL, `athleteSetPoints` INTEGER, `opponentSetPoints` INTEGER, `shootOffWinner` TEXT, `pinned` INTEGER NOT NULL, `sightMark` TEXT, `venue` TEXT, `conditions` TEXT, `trainingIntent` TEXT, `privacyClass` TEXT NOT NULL, PRIMARY KEY(`id`))"
                    )
                    listOf(
                            "athleteId",
                            "rigId",
                            "startedAt",
                            "status",
                            "roundId",
                            "linkedFormSessionId",
                        )
                        .forEach { c ->
                            db.execSQL(
                                "CREATE INDEX IF NOT EXISTS `index_score_session_$c` ON `score_session` (`$c`)"
                            )
                        }
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `score_arrow` (`id` TEXT NOT NULL, `scoreSessionId` TEXT NOT NULL, `endIndex` INTEGER NOT NULL, `arrowIndex` INTEGER NOT NULL, `points` INTEGER NOT NULL, `isX` INTEGER NOT NULL, `plotX` REAL, `plotY` REAL, `plotFaceIndex` INTEGER, `source` TEXT NOT NULL, `authority` TEXT NOT NULL, `resolution` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `active` INTEGER NOT NULL, `retractedAt` INTEGER, `supersedesArrowId` TEXT, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_score_arrow_scoreSessionId` ON `score_arrow` (`scoreSessionId`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_score_arrow_scoreSessionId_endIndex_arrowIndex_active` ON `score_arrow` (`scoreSessionId`,`endIndex`,`arrowIndex`,`active`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_score_arrow_supersedesArrowId` ON `score_arrow` (`supersedesArrowId`)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `score_opponent_end` (`scoreSessionId` TEXT NOT NULL, `endIndex` INTEGER NOT NULL, `total` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`scoreSessionId`,`endIndex`))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_score_opponent_end_scoreSessionId` ON `score_opponent_end` (`scoreSessionId`)"
                    )
                }
            }
        /**
         * V5 → V6: the athlete-feature layer — `goal`, `intervention`, `training_plan`, the
         * `session_default`/`session_context` pair behind explainable Train autofill, and
         * `score_candidate`/`observer_score_event`, which hold *proposed* scores from End Scan and
         * Live Observer. Candidates are advisory only: nothing here may alter an authoritative
         * score until a human confirms it.
         */
        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `goal` (`id` TEXT NOT NULL,`athleteId` TEXT NOT NULL,`metric` TEXT NOT NULL,`title` TEXT NOT NULL,`targetValue` REAL NOT NULL,`unit` TEXT NOT NULL,`direction` TEXT NOT NULL,`aggregation` TEXT NOT NULL,`startAtMs` INTEGER NOT NULL,`targetAtMs` INTEGER,`baselineValue` REAL,`scopeKey` TEXT,`state` TEXT NOT NULL,`createdAtMs` INTEGER NOT NULL,`updatedAtMs` INTEGER NOT NULL,PRIMARY KEY(`id`))"
                    )
                    listOf("athleteId", "state", "targetAtMs").forEach { c ->
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_$c` ON `goal` (`$c`)")
                    }
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `intervention` (`id` TEXT NOT NULL,`athleteId` TEXT NOT NULL,`atMs` INTEGER NOT NULL,`kind` TEXT NOT NULL,`title` TEXT NOT NULL,`note` TEXT,`rigId` TEXT,PRIMARY KEY(`id`))"
                    )
                    listOf("athleteId", "atMs", "kind").forEach { c ->
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_intervention_$c` ON `intervention` (`$c`)"
                        )
                    }
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `session_default` (`id` TEXT NOT NULL,`athleteId` TEXT NOT NULL,`disciplineId` TEXT,`rigId` TEXT,`venue` TEXT,`distanceMeters` INTEGER,`targetFaceCm` INTEGER,`arrowCount` INTEGER,`roundId` TEXT,`trainingIntent` TEXT,`pinnedFieldsCsv` TEXT NOT NULL,`updatedAtMs` INTEGER NOT NULL,PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_session_default_athleteId` ON `session_default` (`athleteId`)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `session_context` (`sessionId` TEXT NOT NULL,`athleteId` TEXT NOT NULL,`disciplineId` TEXT NOT NULL,`rigId` TEXT,`venue` TEXT,`distanceMeters` INTEGER,`targetFaceCm` INTEGER,`arrowCountPlanned` INTEGER,`roundId` TEXT,`trainingIntent` TEXT,`startedAtMs` INTEGER NOT NULL,PRIMARY KEY(`sessionId`))"
                    )
                    listOf("athleteId", "startedAtMs", "rigId").forEach { c ->
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_session_context_$c` ON `session_context` (`$c`)"
                        )
                    }
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `training_plan` (`id` TEXT NOT NULL,`athleteId` TEXT NOT NULL,`title` TEXT NOT NULL,`phase` TEXT NOT NULL,`focus` TEXT NOT NULL,`startDate` TEXT NOT NULL,`endDate` TEXT,`weeklyArrowTarget` INTEGER,`intensity` TEXT NOT NULL,`recoveryNotes` TEXT,`state` TEXT NOT NULL,`createdAtMs` INTEGER NOT NULL,`updatedAtMs` INTEGER NOT NULL,PRIMARY KEY(`id`))"
                    )
                    listOf("athleteId", "state", "startDate").forEach { c ->
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_training_plan_$c` ON `training_plan` (`$c`)"
                        )
                    }
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `score_candidate` (`id` TEXT NOT NULL,`scoreSessionId` TEXT NOT NULL,`endIndex` INTEGER NOT NULL,`candidateIndex` INTEGER NOT NULL,`points` INTEGER NOT NULL,`isX` INTEGER NOT NULL,`plotX` REAL,`plotY` REAL,`plotFaceIndex` INTEGER,`confidence` REAL,`source` TEXT NOT NULL,`status` TEXT NOT NULL,`resolution` TEXT NOT NULL,`createdAtMs` INTEGER NOT NULL,`resolvedAtMs` INTEGER,PRIMARY KEY(`id`))"
                    )
                    listOf("scoreSessionId", "endIndex", "status").forEach { c ->
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_score_candidate_$c` ON `score_candidate` (`$c`)"
                        )
                    }
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `observer_score_event` (`id` TEXT NOT NULL,`scoreSessionId` TEXT NOT NULL,`atMs` INTEGER NOT NULL,`ring` INTEGER NOT NULL,`isX` INTEGER NOT NULL,`sector` TEXT,`inputMode` TEXT NOT NULL,`status` TEXT NOT NULL,`declaredText` TEXT,`resolution` TEXT NOT NULL,`correctionOfId` TEXT,PRIMARY KEY(`id`))"
                    )
                    listOf("scoreSessionId", "atMs", "status").forEach { c ->
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_observer_score_event_$c` ON `observer_score_event` (`$c`)"
                        )
                    }
                }
            }

        /**
         * V6 → V7: retraction instead of destruction.
         *
         * A capture session feeds the streak, the load series, the calendar grid and Progress's
         * volume and stability trends; a scorecard feeds `previousBest`, `bestPerRound`, the score
         * trend and the PB list. Hard-deleting either rewrites months of derived history with
         * nothing left on the device to explain why the numbers moved — which is exactly what
         * "never silently delete athlete history" forbids, even when the athlete asked. One
         * nullable timestamp per table keeps the bytes, makes every query that should ignore a
         * retracted row say so explicitly, and makes the decision reversible.
         *
         * Two ALTERs and no new table, deliberately. A tombstone ledger would carry human-readable
         * labels drawn from rows of different privacy classes, and [PrivacyRegistry] classifies by
         * table, so there would be no honest class to register it under. A column inherits its
         * row's class for free.
         *
         * `ALTER TABLE ADD COLUMN` with no NOT NULL and no default is the one shape SQLite performs
         * without rewriting the table, so this cannot fail part-way through on a large history.
         */
        val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `sessions` ADD COLUMN `deletedAt` INTEGER")
                    db.execSQL("ALTER TABLE `score_session` ADD COLUMN `deletedAt` INTEGER")
                }
            }

        /**
         * V7 → V8: the same retraction column on the three remaining tables an athlete can be
         * expected to want one row removed from.
         *
         * `checkin` feeds readiness, the streak, the calendar dots and — through `postCheckinId` →
         * `rpe` — the load model. `pain_log` feeds the eight-week body heat map and the region
         * signals the coach reads. `injury` feeds the atlas overlay, the Body tab badge, readiness,
         * the coach factsheet and the auto-link stamped on every subsequent pain entry, and both
         * `pain_log.injuryId` and `document.injuryId` point at it. Dropping any of those rows would
         * leave dangling references and derived numbers no one can account for.
         */
        val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    listOf("checkin", "pain_log", "injury").forEach {
                        db.execSQL("ALTER TABLE `$it` ADD COLUMN `deletedAt` INTEGER")
                    }
                }
            }
    }
}
