package xyz.mdhv.formanalyser.exchange

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The manifest at the root of a `.crocbak` archive. Describes what the export contains and who
 * produced it, so a reader can validate the archive before ingesting it.
 *
 * All time is [createdAtMs]: it is PASSED IN by the caller (never read from the clock inside this
 * module) to keep the whole pipeline deterministic and unit-testable.
 *
 * @property schemaVersion version of this manifest schema.
 * @property appVersion Crocodyl app version that produced the archive.
 * @property createdAtMs epoch-millis creation time, supplied by the caller.
 * @property athletePubkeyFingerprint [PubkeyIdentity.fingerprint] of the producing athlete.
 * @property tierName the [ExportTier] name used to filter this archive.
 * @property includedTables tables actually included, per the [ConsentFilter] decision.
 * @property rowCounts optional per-table row counts (logical table name -> rows).
 * @property contentChecksum checksum over the archive payload (algorithm-prefixed, e.g. "sha256:…").
 */
@Serializable
data class CrocbakManifest(
    val schemaVersion: Int,
    val appVersion: String,
    val createdAtMs: Long,
    val athletePubkeyFingerprint: String,
    val tierName: String,
    val includedTables: List<String>,
    val rowCounts: Map<String, Long>? = null,
    val contentChecksum: String,
) {
    /** Serialize to canonical JSON. */
    fun serialize(json: Json = DEFAULT_JSON): String = json.encodeToString(this)

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1

        /** Stable, forgiving JSON: pretty output off, unknown keys ignored, defaults encoded. */
        val DEFAULT_JSON: Json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            prettyPrint = false
        }

        /** Parse a manifest from JSON. */
        fun deserialize(text: String, json: Json = DEFAULT_JSON): CrocbakManifest =
            json.decodeFromString(text)

        /**
         * Convenience builder that stamps [CURRENT_SCHEMA_VERSION] and pulls tier/tables from a
         * [ConsentDecision]. [createdAtMs] is still caller-supplied.
         */
        fun fromDecision(
            appVersion: String,
            createdAtMs: Long,
            athletePubkeyFingerprint: String,
            tier: ExportTier,
            decision: ConsentDecision,
            contentChecksum: String,
            rowCounts: Map<String, Long>? = null,
        ): CrocbakManifest = CrocbakManifest(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            appVersion = appVersion,
            createdAtMs = createdAtMs,
            athletePubkeyFingerprint = athletePubkeyFingerprint,
            tierName = tier.name,
            includedTables = decision.included.sorted(),
            rowCounts = rowCounts,
            contentChecksum = contentChecksum,
        )
    }
}
