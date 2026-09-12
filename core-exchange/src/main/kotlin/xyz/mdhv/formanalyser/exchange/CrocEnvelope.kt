package xyz.mdhv.formanalyser.exchange

import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.mdhv.formanalyser.wellness.PrivacyClass
import xyz.mdhv.formanalyser.wellness.PrivacyRegistry

/**
 * The `.croc` **person-to-person exchange envelope** — a signed, versioned statement one human hands
 * another (athlete -> coach, coach -> athlete) over any transport they already use.
 *
 * It is deliberately NOT `.crocbak`. A `.crocbak` is device recovery: it goes back to the same
 * person on the same device, so it records *who produced it* and nothing more. A `.croc` crosses a
 * trust boundary, so everything a recipient must decide on — who signed, what consent tier the
 * sender chose, what the payload is supposed to hash to — has to be inside the signed statement,
 * and everything the recipient can check has to be checkable offline with no server and no account.
 *
 * ## On-disk layout (a zip, so any file manager and the future static web viewer can open it)
 * ```
 * manifest.json    the signed statement (a CrocManifest); THESE EXACT BYTES are what was signed
 * signature.json   a CrocSignature sidecar: suite name + base64 signature over manifest.json
 * tables/<t>.json  one JSON array of rows per consent-included logical table
 * ```
 *
 * ### Why the signature is a sidecar and not a field inside the signed document
 * A signature cannot cover itself, so a single self-contained JSON forces the verifier to *rebuild*
 * the signed bytes by re-serializing the parsed document minus the signature field. That works only
 * while both sides serialize identically forever — the moment a v2 sender adds a field a v1 reader
 * drops as unknown, re-serialization produces different bytes and every envelope fails verification
 * for a reason that has nothing to do with authenticity. Verifying over the literal bytes of
 * `manifest.json` removes that entire failure mode: an older reader still gets a valid signature on
 * a newer manifest, and can then refuse it on [CrocManifest.schemaVersion] alone — a decision it
 * makes knowingly instead of one that looks like tampering.
 */
object CrocEnvelope {
    /** Zip entry holding the signed manifest bytes. */
    const val ENTRY_MANIFEST: String = "manifest.json"

    /** Zip entry holding the signature sidecar. */
    const val ENTRY_SIGNATURE: String = "signature.json"

    /** Prefix for per-table payload entries. */
    const val ENTRY_TABLES_PREFIX: String = "tables/"

    /** File extension, without the dot. */
    const val EXTENSION: String = "croc"

    /** MIME type used when handing the file to a share sheet or a document picker. */
    const val MIME: String = "application/zip"

    /** Zip entry name for a logical table's payload. */
    fun tableEntry(logicalTable: String): String = "$ENTRY_TABLES_PREFIX$logicalTable.json"

    /** The logical table name for a `tables/…json` entry, or null if [entry] is not one. */
    fun tableOfEntry(entry: String): String? =
        if (entry.startsWith(ENTRY_TABLES_PREFIX) && entry.endsWith(".json")) {
            entry.removePrefix(ENTRY_TABLES_PREFIX).removeSuffix(".json").takeIf { it.isNotEmpty() }
        } else {
            null
        }
}

/**
 * What the envelope IS, so an importer can route it without inspecting the payload.
 *
 * Only [ATHLETE_REPORT] is produced today; the other two are the coach-side return legs and exist
 * here so the kind field is a closed vocabulary from schema v1 rather than a string that gains
 * meanings later. An unrecognised kind is preserved as text and surfaced to the human — see
 * [CrocManifest.kindOrNull].
 */
enum class CrocEnvelopeKind {
    /** Athlete -> coach: consent-filtered training data. */
    ATHLETE_REPORT,

    /** Coach -> athlete: drills/plans to complete. Never edits athlete facts. */
    COACH_ASSIGNMENT,

    /** Coach -> athlete: timestamped coach observations, stored as separate signed records. */
    COACH_OBSERVATION,
}

/**
 * Checksum over the payload entries, binding NAMES as well as bytes.
 *
 * `CrocbakManifest`'s checksum digests only the concatenated table bytes in sorted order, which
 * cannot notice a table being renamed or two same-sized payloads being swapped. That is tolerable
 * for a backup you hand back to yourself; it is not tolerable across a trust boundary, where the
 * checksum is the only thing tying "the manifest says `medication_entry` has 3 rows" to the bytes
 * actually in the zip. So each entry contributes `name || 0x00 || bigEndian(len) || bytes`, over
 * entries sorted by name — a framing no reordering or renaming can collide with.
 */
object PayloadChecksum {
    /** Algorithm prefix carried in the manifest, matching the `.crocbak` convention. */
    const val PREFIX: String = "sha256:"

    /** Compute the algorithm-prefixed checksum of [entries] (order-independent). */
    fun of(entries: List<CrocPayloadEntry>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (entry in entries.sortedBy { it.logicalTable }) {
            digest.update(entry.logicalTable.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            val len = entry.bytes.size.toLong()
            val lenBytes = ByteArray(8) { i -> ((len ushr (8 * (7 - i))) and 0xFF).toByte() }
            digest.update(lenBytes)
            digest.update(entry.bytes)
        }
        return PREFIX + digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * One table's serialized rows inside an envelope. A carrier, not a value type — [bytes] is held by
 * reference because payloads can be large and nothing in this module mutates or compares them.
 */
class CrocPayloadEntry(val logicalTable: String, val bytes: ByteArray)

/**
 * The signed statement at the root of a `.croc`.
 *
 * Everything a recipient's decision depends on lives here, inside the signature: who signed, what
 * they claim to be sending, under which consent tier, and what the payload must hash to. Anything
 * outside the signature is a hint, not evidence.
 *
 * [createdAtMs] is PASSED IN by the caller — this module never reads a clock — so the whole build
 * pipeline stays deterministic and unit-testable, exactly as `CrocbakManifest` does.
 *
 * @property schemaVersion version of this manifest schema; a reader refuses versions it lacks.
 * @property appVersion Crocodyl version that produced the envelope (diagnostics, not authority).
 * @property createdAtMs epoch-millis creation time, supplied by the caller.
 * @property kindName [CrocEnvelopeKind] name; kept as text so an unknown future kind survives parse.
 * @property senderPubkeyB64 base64 X.509 SubjectPublicKeyInfo of the sender's public key. Inside the
 *   signature on purpose: if the key lived only in the sidecar, anyone could re-sign a copied
 *   manifest with their own key and the document would still claim to be from the original sender.
 * @property senderFingerprint [PubkeyIdentity.fingerprint] of [senderPubkeyB64]; redundant by
 *   construction and re-derived on verify, so a doctored card that says one thing and carries
 *   another is caught rather than displayed.
 * @property senderLabel self-asserted display name ("Anna, club coach"). NEVER authority — it is the
 *   handle a recipient pins a key against (see [TofuTrust]), and it is shown as the sender's claim.
 * @property recipientFingerprint optional fingerprint of the intended recipient; lets an importer
 *   say "this was not addressed to this device" instead of silently ingesting someone else's file.
 * @property tierName the [ExportTier] the sender chose.
 * @property includedTables logical tables present in `tables/`, per the [ConsentFilter] decision.
 * @property rowCounts optional per-table row counts, for the import preview's "merge effects".
 * @property payloadChecksum [PayloadChecksum] over every payload entry.
 * @property signatureAlgorithm the [SignatureAlgorithms] suite; signed, so it cannot be downgraded.
 */
@Serializable
data class CrocManifest(
    val schemaVersion: Int,
    val appVersion: String,
    val createdAtMs: Long,
    val kindName: String,
    val senderPubkeyB64: String,
    val senderFingerprint: String,
    val senderLabel: String? = null,
    val recipientFingerprint: String? = null,
    val tierName: String,
    val includedTables: List<String>,
    val rowCounts: Map<String, Long>? = null,
    val payloadChecksum: String,
    val signatureAlgorithm: String,
) {
    /** Serialize to the canonical JSON form. */
    fun serialize(json: Json = DEFAULT_JSON): String = json.encodeToString(this)

    /** The kind as an enum, or null if this envelope was written by a newer schema than we know. */
    fun kindOrNull(): CrocEnvelopeKind? =
        CrocEnvelopeKind.entries.firstOrNull { it.name == kindName }

    /** The tier as an enum, or null if unrecognised (which verification treats as fatal). */
    fun tierOrNull(): ExportTier? = ExportTier.entries.firstOrNull { it.name == tierName }

    /** Decoded sender public key bytes, or null if [senderPubkeyB64] is not valid base64. */
    fun senderPubkeyBytesOrNull(): ByteArray? =
        try {
            Base64.getDecoder().decode(senderPubkeyB64)
        } catch (_: IllegalArgumentException) {
            null
        }

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1

        /**
         * Schema versions this build will accept on import. Widened only when a reader genuinely
         * understands the older/newer shape — never as a convenience.
         */
        val SUPPORTED_SCHEMA_VERSIONS: IntRange = 1..1

        /**
         * Stable, forgiving JSON, matching `CrocbakManifest.DEFAULT_JSON`. `ignoreUnknownKeys` is
         * safe here precisely because verification runs over the raw bytes and not over a
         * re-serialization of the parsed object — see the note on [CrocEnvelope].
         */
        val DEFAULT_JSON: Json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            prettyPrint = false
        }

        /** Parse a manifest from JSON. Throws on malformed input; callers use [CrocVerifier]. */
        fun deserialize(text: String, json: Json = DEFAULT_JSON): CrocManifest =
            json.decodeFromString(text)

        /**
         * Build a manifest from a [ConsentDecision], mirroring `CrocbakManifest.fromDecision` so the
         * two formats can never disagree about what the consent ceremony decided. [createdAtMs] is
         * still caller-supplied.
         */
        fun fromDecision(
            appVersion: String,
            createdAtMs: Long,
            kind: CrocEnvelopeKind,
            sender: PubkeyIdentity,
            senderLabel: String? = null,
            recipientFingerprint: String? = null,
            tier: ExportTier,
            decision: ConsentDecision,
            payloadChecksum: String,
            rowCounts: Map<String, Long>? = null,
            signatureAlgorithm: String = SignatureAlgorithms.ECDSA_P256_SHA256,
        ): CrocManifest =
            CrocManifest(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                appVersion = appVersion,
                createdAtMs = createdAtMs,
                kindName = kind.name,
                senderPubkeyB64 = Base64.getEncoder().encodeToString(sender.keyBytes),
                senderFingerprint = sender.fingerprint,
                senderLabel = senderLabel,
                recipientFingerprint = recipientFingerprint,
                tierName = tier.name,
                includedTables = decision.included.sorted(),
                rowCounts = rowCounts,
                payloadChecksum = payloadChecksum,
                signatureAlgorithm = signatureAlgorithm,
            )
    }
}

/**
 * The unsigned sidecar written to `signature.json`.
 *
 * [algorithm] is duplicated from the signed manifest for a reason: a reader needs to know how to
 * verify BEFORE it trusts anything, and the signed copy is what actually binds. The two disagreeing
 * is [CrocRejection.ALGORITHM_MISMATCH] — a downgrade attempt becomes a visible failure instead of a
 * silent success.
 */
@Serializable
data class CrocSignature(val algorithm: String, val signatureB64: String) {
    /** Serialize to the canonical JSON form. */
    fun serialize(json: Json = CrocManifest.DEFAULT_JSON): String = json.encodeToString(this)

    /** Decoded signature bytes, or null if [signatureB64] is not valid base64. */
    fun bytesOrNull(): ByteArray? =
        try {
            Base64.getDecoder().decode(signatureB64)
        } catch (_: IllegalArgumentException) {
            null
        }

    companion object {
        /** Parse a sidecar from JSON. Throws on malformed input; callers use [CrocVerifier]. */
        fun deserialize(text: String, json: Json = CrocManifest.DEFAULT_JSON): CrocSignature =
            json.decodeFromString(text)
    }
}

/**
 * A manifest and its signature, bound to the EXACT bytes that were signed.
 *
 * The writer must put [manifestBytes] in the zip verbatim. Re-serializing the manifest at write time
 * would reintroduce precisely the drift the sidecar design exists to prevent, so the sealing step
 * hands back the bytes rather than trusting the caller to reproduce them.
 */
class SealedCroc(
    val manifest: CrocManifest,
    val manifestBytes: ByteArray,
    val signature: CrocSignature,
)

/** Signs manifests. The only place in `core-exchange` that touches a [Signer]. */
object CrocSigning {

    /**
     * Serialize [manifest], sign those bytes with [signer], and return both so they cannot diverge.
     *
     * Requires the manifest to name the same suite the signer produces: the manifest is a statement
     * about how to verify it, and a statement the signer cannot honour is a programming error here,
     * not a runtime condition to be discovered by the recipient.
     */
    fun seal(
        manifest: CrocManifest,
        signer: Signer,
        json: Json = CrocManifest.DEFAULT_JSON,
    ): SealedCroc {
        require(manifest.signatureAlgorithm == signer.algorithm) {
            "manifest declares ${manifest.signatureAlgorithm}, signer produces ${signer.algorithm}"
        }
        val bytes = manifest.serialize(json).toByteArray(Charsets.UTF_8)
        val sig = signer.sign(bytes)
        return SealedCroc(
            manifest = manifest,
            manifestBytes = bytes,
            signature =
                CrocSignature(
                    algorithm = signer.algorithm,
                    signatureB64 = Base64.getEncoder().encodeToString(sig),
                ),
        )
    }
}

/**
 * Why an envelope was refused. Every value is something a human can be told plainly, because the
 * import preview shows the reason rather than a generic "invalid file" — a coach whose envelope
 * bounces needs to know whether to resend it or to worry.
 */
enum class CrocRejection {
    /** `manifest.json` or `signature.json` is missing, empty, or not parseable. */
    MALFORMED_ENVELOPE,

    /** Written by a schema this build does not understand. Not tampering — a version gap. */
    UNSUPPORTED_SCHEMA_VERSION,

    /** The sidecar names a different suite than the signed manifest does: a downgrade attempt. */
    ALGORITHM_MISMATCH,

    /** Neither side names a suite this build can verify. */
    UNSUPPORTED_ALGORITHM,

    /** The sender public key is not base64, not X.509 SPKI, or not on P-256. */
    MALFORMED_PUBLIC_KEY,

    /** The signature is not base64 or not a parseable signature. */
    MALFORMED_SIGNATURE,

    /** The manifest's stated fingerprint is not the fingerprint of the key it carries. */
    FINGERPRINT_MISMATCH,

    /** The signature does not verify. The manifest has been altered, or it was never signed. */
    SIGNATURE_INVALID,

    /** The zip's `tables/` entries are not the set the signed manifest claims. */
    PAYLOAD_TABLE_SET_MISMATCH,

    /** The payload bytes do not hash to the checksum the signed manifest claims. */
    PAYLOAD_CHECKSUM_MISMATCH,

    /** The manifest names a tier this build does not know, so its consent claim is unreadable. */
    UNSUPPORTED_TIER,

    /**
     * The manifest claims to carry a PRIVATE table. Nothing may import it, no matter who signed it.
     * A validly signed envelope can still be one no honest build would ever produce.
     */
    PRIVATE_TABLE_PRESENT,

    /** The manifest claims a table its own declared tier could not include. */
    TIER_CONTRADICTION,

    /** The manifest claims a table absent from [PrivacyRegistry]; unclassified means unimportable. */
    UNKNOWN_TABLE_CLAIMED,
}

/** The outcome of verifying an envelope: either a statement we can act on, or a refusal. */
sealed interface CrocVerdict {

    /**
     * The signature is valid and every self-consistency check passed.
     *
     * [trust] is a SEPARATE axis: a `Verified` envelope with [SenderTrust.PINNED_MISMATCH] is
     * cryptographically sound and must still be quarantined, because "validly signed" and "signed by
     * who you think" are different claims.
     */
    data class Verified(
        val manifest: CrocManifest,
        val sender: PubkeyIdentity,
        val trust: SenderTrust,
    ) : CrocVerdict {
        /** Whether an importer may proceed without a fresh human decision about the sender. */
        val requiresQuarantine: Boolean
            get() = trust == SenderTrust.PINNED_MISMATCH
    }

    /** Refused. [detail] is diagnostic text for the preview, never a reason to relax [reason]. */
    data class Rejected(val reason: CrocRejection, val detail: String) : CrocVerdict
}

/**
 * The one place an incoming `.croc` is judged. Pure and fail-closed: every unknown is a refusal, and
 * nothing about the payload is believed until the signature over the manifest has verified.
 *
 * Check order matters and is deliberate:
 * 1. parse and version-gate — we must know the shape before we can reason about it;
 * 2. suite agreement and key/signature well-formedness — we must know HOW to verify;
 * 3. fingerprint self-consistency — the displayed identity must be the one that signed;
 * 4. the signature itself — until this passes, every field below is an attacker's free text;
 * 5. payload set and checksum — the bytes must be the bytes the signed statement described;
 * 6. the consent re-check — the manifest's own claims must satisfy [ConsentFilter] on THIS device,
 *    so a build that got the privacy law wrong, or a hand-edited envelope, cannot import a PRIVATE
 *    table just because a valid key signed it. The recipient enforces the invariant locally rather
 *    than trusting the sender to have enforced it.
 */
object CrocVerifier {

    /**
     * @param manifestBytes the literal bytes of `manifest.json`, as read from the zip.
     * @param signatureBytesJson the literal text of `signature.json`.
     * @param payload every `tables/…json` entry found in the zip.
     * @param verifier signature suite implementation; defaults to the reference P-256 verifier.
     * @param pinnedSenderKey key previously pinned for this sender's label, or null on first contact.
     * @param supportedSchemaVersions schema versions this build will accept.
     */
    fun verify(
        manifestBytes: ByteArray,
        signatureBytesJson: String,
        payload: List<CrocPayloadEntry>,
        verifier: Verifier = EcdsaP256Verifier,
        pinnedSenderKey: ByteArray? = null,
        supportedSchemaVersions: IntRange = CrocManifest.SUPPORTED_SCHEMA_VERSIONS,
    ): CrocVerdict {
        // 1. Parse. Hostile input is ordinary input here, so nothing below may throw out of verify().
        val manifest =
            try {
                CrocManifest.deserialize(manifestBytes.toString(Charsets.UTF_8))
            } catch (e: Exception) {
                return reject(CrocRejection.MALFORMED_ENVELOPE, "manifest.json: ${e.message}")
            }
        val sidecar =
            try {
                CrocSignature.deserialize(signatureBytesJson)
            } catch (e: Exception) {
                return reject(CrocRejection.MALFORMED_ENVELOPE, "signature.json: ${e.message}")
            }

        if (manifest.schemaVersion !in supportedSchemaVersions) {
            return reject(
                CrocRejection.UNSUPPORTED_SCHEMA_VERSION,
                "schema v${manifest.schemaVersion}, this build reads v$supportedSchemaVersions",
            )
        }

        // 2. Agree on the suite before trusting either copy of its name.
        if (manifest.signatureAlgorithm != sidecar.algorithm) {
            return reject(
                CrocRejection.ALGORITHM_MISMATCH,
                "manifest says ${manifest.signatureAlgorithm}, sidecar says ${sidecar.algorithm}",
            )
        }
        val keyBytes =
            manifest.senderPubkeyBytesOrNull()
                ?: return reject(CrocRejection.MALFORMED_PUBLIC_KEY, "sender key is not base64")
        val sigBytes =
            sidecar.bytesOrNull()
                ?: return reject(CrocRejection.MALFORMED_SIGNATURE, "signature is not base64")

        // 3. The identity shown to the human must be the identity that signed.
        val sender = PubkeyIdentity.of(keyBytes)
        if (sender.fingerprint != manifest.senderFingerprint) {
            return reject(
                CrocRejection.FINGERPRINT_MISMATCH,
                "manifest fingerprint does not match the key it carries",
            )
        }

        // 4. The signature. Everything already read was only shape; everything after this is claim.
        when (verifier.verify(manifestBytes, sigBytes, keyBytes, manifest.signatureAlgorithm)) {
            SignatureOutcome.VALID -> Unit
            SignatureOutcome.INVALID ->
                return reject(CrocRejection.SIGNATURE_INVALID, "signature does not verify")
            SignatureOutcome.MALFORMED_PUBLIC_KEY ->
                return reject(CrocRejection.MALFORMED_PUBLIC_KEY, "sender key is not a P-256 key")
            SignatureOutcome.MALFORMED_SIGNATURE ->
                return reject(CrocRejection.MALFORMED_SIGNATURE, "signature is not well-formed")
            SignatureOutcome.UNSUPPORTED_ALGORITHM ->
                return reject(
                    CrocRejection.UNSUPPORTED_ALGORITHM,
                    "this build cannot verify ${manifest.signatureAlgorithm}",
                )
        }

        // 5. The bytes in the zip must be the bytes the signed statement described.
        val claimed = manifest.includedTables.toSet()
        val present = payload.map { it.logicalTable }.toSet()
        if (claimed != present) {
            val missing = (claimed - present).sorted()
            val extra = (present - claimed).sorted()
            return reject(
                CrocRejection.PAYLOAD_TABLE_SET_MISMATCH,
                "missing=$missing extra=$extra",
            )
        }
        val actualChecksum = PayloadChecksum.of(payload)
        if (actualChecksum != manifest.payloadChecksum) {
            return reject(
                CrocRejection.PAYLOAD_CHECKSUM_MISMATCH,
                "expected ${manifest.payloadChecksum}, computed $actualChecksum",
            )
        }

        // 6. Re-run the privacy law locally. The sender's signature says they meant to send this;
        // it says nothing about whether this device is allowed to receive it.
        val tier =
            manifest.tierOrNull()
                ?: return reject(CrocRejection.UNSUPPORTED_TIER, "unknown tier ${manifest.tierName}")
        val medicalClaimed =
            claimed.filter { PrivacyRegistry.classOf(it) == PrivacyClass.MEDICAL }.toSet()
        val decision = ConsentFilter.filter(claimed, tier, medicalGrants = medicalClaimed)
        if (decision.included != claimed) {
            val offender = (claimed - decision.included).sorted()
            val reason =
                when (decision.withheldReasonByTable[offender.first()]) {
                    WithheldReason.PRIVATE_ALWAYS_EXCLUDED -> CrocRejection.PRIVATE_TABLE_PRESENT
                    WithheldReason.UNKNOWN_TABLE -> CrocRejection.UNKNOWN_TABLE_CLAIMED
                    // NOT_IN_TIER, and MEDICAL_NEEDS_GRANT (unreachable: every claimed MEDICAL
                    // table was granted above) both mean the claim contradicts its own tier.
                    else -> CrocRejection.TIER_CONTRADICTION
                }
            return reject(reason, "tier ${manifest.tierName} cannot carry $offender")
        }

        return CrocVerdict.Verified(
            manifest = manifest,
            sender = sender,
            trust = TofuTrust.evaluate(pinnedSenderKey, keyBytes),
        )
    }

    private fun reject(reason: CrocRejection, detail: String): CrocVerdict.Rejected =
        CrocVerdict.Rejected(reason, detail)
}
