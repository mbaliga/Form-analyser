package xyz.mdhv.crocodyl.engine.integration

import xyz.mdhv.crocodyl.engine.baseline.BaselineModel
import xyz.mdhv.crocodyl.engine.model.FeatureVector

/** Stable seam for adopting the shared advanced-search implementation when its source is selected. */
interface AdvancedSearchPort {
    fun search(query: String, filters: Map<String, String> = emptyMap()): List<SearchHit>
}

data class SearchHit(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val canonicalUrl: String? = null,
)

/** Baseline is already local and sport-agnostic; adapters can expose it to search/coach surfaces. */
interface BaselineAnalysisPort {
    fun analyse(features: FeatureVector, baseline: BaselineModel): BaselineAnalysis
}

data class BaselineAnalysis(
    val zScores: Map<String, Double>,
    val baselineReady: Boolean,
    val sampleCount: Long,
)

/** Nooz adapter boundary. Network fetching, attribution and freshness belong outside the engine. */
interface SportNewsFeedPort {
    fun latest(sportId: String, limit: Int = 20): List<NewsItem>
}

data class NewsItem(
    val id: String,
    val sportId: String,
    val title: String,
    val summary: String,
    val sourceName: String,
    val canonicalUrl: String,
    val publishedAtEpochMs: Long?,
)
