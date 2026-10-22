package xyz.mdhv.crocodyl.performance

import dev.aarso.performance.PerformanceAnalyzer
import dev.aarso.performance.PerformanceReport
import dev.aarso.search.CoverageRecencyScorer
import dev.aarso.search.EvalContext
import dev.aarso.search.FieldName
import dev.aarso.search.SearchDoc
import dev.aarso.session.LocalSessionLedger
import dev.aarso.session.ParticipantId
import dev.aarso.session.SessionId
import dev.aarso.session.SessionRole
import java.io.File

/** Beta-only report runner; it never runs as part of the production APK. */
fun main(args: Array<String>) {
    val output = args.getOrNull(0)?.let(::File) ?: File("build/reports/beta/performance-report.json")
    val revision = args.getOrNull(1) ?: "unresolved"
    val corpus = (0 until 1_000).map { index ->
        SearchDoc(
            id = dev.aarso.search.DocId("doc-$index"),
            timestampMillis = 1_700_000_000_000L + index,
            text = mapOf(
                FieldName("title") to "archery fencing training session $index",
                FieldName("content") to "local first form analysis equipment coaching report",
            ),
        )
    }
    val scorer = CoverageRecencyScorer(mapOf(FieldName("title") to 2.0))
    val queryTerms = listOf("fencing", "training")
    val stats = PerformanceAnalyzer().measure("shared-search.rank", warmupIterations = 5, measuredIterations = 30) {
        scorer.rank(corpus, queryTerms, EvalContext(nowMillis = 1_700_000_000_000L))
    }
    val sessionLedger = LocalSessionLedger(SessionId("beta"), ParticipantId("host"), SessionRole.HOST)
    var sessionSequence = 0L
    val sessionStats = PerformanceAnalyzer().measure("shared-session.event", warmupIterations = 5, measuredIterations = 30) {
        sessionLedger.localEvent(sessionSequence, "event-$sessionSequence", "score:1")
        sessionSequence += 1
    }
    val report = PerformanceReport("crocodyl-beta", revision, listOf(stats, sessionStats))
    output.parentFile?.mkdirs()
    output.writeText(report.toJson() + "\n")
    println(report.toJson())
    println("Wrote beta performance report to ${output.absolutePath}")
}
