package xyz.mdhv.formanalyser.exchange

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A signed, transport-neutral person-to-person Crocodyl exchange envelope. */
@Serializable
data class CrocEnvelope(
    val schemaVersion: Int,
    val createdAtMs: Long,
    val senderPublicKeyBase64: String,
    val senderFingerprint: String,
    val payloadType: String,
    val payloadSha256: String,
    val payloadBase64: String,
    val signatureAlgorithm: String,
    val signatureBase64: String,
) {
    fun payload(): ByteArray = Base64.getDecoder().decode(payloadBase64)

    /** Verify fingerprint, payload digest and ECDSA signature before exposing imported data. */
    fun verify(): Boolean = runCatching {
        if (schemaVersion != CURRENT_SCHEMA_VERSION || signatureAlgorithm != SIGNATURE_ALGORITHM) return false
        val publicBytes = Base64.getDecoder().decode(senderPublicKeyBase64)
        if (Fingerprint.format(publicBytes) != senderFingerprint) return false
        val bytes = payload()
        if (!MessageDigest.isEqual(sha256(bytes).toByteArray(), payloadSha256.toByteArray())) return false
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicBytes))
        Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initVerify(publicKey)
            update(signingBytes())
            verify(Base64.getDecoder().decode(signatureBase64))
        }
    }.getOrDefault(false)

    fun serialize(): String = JSON.encodeToString(this)

    private fun signingBytes(): ByteArray =
        JSON.encodeToString(
            UnsignedEnvelope(
                schemaVersion,
                createdAtMs,
                senderPublicKeyBase64,
                senderFingerprint,
                payloadType,
                payloadSha256,
                payloadBase64,
                signatureAlgorithm,
            )
        ).toByteArray()

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        const val BACKUP_PAYLOAD = "application/vnd.crocodyl.crocbak+zip"
        private val JSON = Json { encodeDefaults = true; ignoreUnknownKeys = true }

        fun create(
            createdAtMs: Long,
            senderPublicKey: ByteArray,
            payloadType: String,
            payload: ByteArray,
            signer: (ByteArray) -> ByteArray,
        ): CrocEnvelope {
            val draft =
                CrocEnvelope(
                    CURRENT_SCHEMA_VERSION,
                    createdAtMs,
                    Base64.getEncoder().encodeToString(senderPublicKey),
                    Fingerprint.format(senderPublicKey),
                    payloadType,
                    sha256(payload),
                    Base64.getEncoder().encodeToString(payload),
                    SIGNATURE_ALGORITHM,
                    "",
                )
            return draft.copy(signatureBase64 = Base64.getEncoder().encodeToString(signer(draft.signingBytes())))
        }

        fun deserialize(text: String): CrocEnvelope = JSON.decodeFromString(text)

        private fun sha256(bytes: ByteArray): String =
            "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

@Serializable
private data class UnsignedEnvelope(
    val schemaVersion: Int,
    val createdAtMs: Long,
    val senderPublicKeyBase64: String,
    val senderFingerprint: String,
    val payloadType: String,
    val payloadSha256: String,
    val payloadBase64: String,
    val signatureAlgorithm: String,
)
