package xyz.mdhv.formanalyser.exchange

/**
 * Deterministic fingerprint formatting for a public key. Pure string math over the provided key
 * bytes — no crypto, no randomness — so it is fully unit-testable. Real key generation lives behind
 * [KeyProvider] in the Android Keystore layer.
 */
object Fingerprint {
    private val HEX = "0123456789ABCDEF".toCharArray()

    /**
     * Uppercase hex of [keyBytes], grouped into blocks of [groupSize] characters joined by
     * [separator]. Stable and injective for a given input.
     */
    fun format(keyBytes: ByteArray, groupSize: Int = 4, separator: String = "-"): String {
        require(groupSize > 0) { "groupSize must be positive" }
        val hex = StringBuilder(keyBytes.size * 2)
        for (b in keyBytes) {
            val v = b.toInt() and 0xFF
            hex.append(HEX[v ushr 4])
            hex.append(HEX[v and 0x0F])
        }
        if (hex.isEmpty()) return ""
        val out = StringBuilder()
        for (i in hex.indices) {
            if (i > 0 && i % groupSize == 0) out.append(separator)
            out.append(hex[i])
        }
        return out.toString()
    }
}

/**
 * An athlete's public-key identity — a value type wrapping the raw public-key bytes plus a stable,
 * human-comparable [fingerprint]. This is a model only: it performs no crypto. The Android layer
 * obtains real key bytes from the Keystore via [KeyProvider] and wraps them here.
 */
class PubkeyIdentity private constructor(private val bytes: ByteArray) {

    /** Defensive copy of the underlying public-key bytes. */
    val keyBytes: ByteArray get() = bytes.copyOf()

    /** Deterministic grouped-hex fingerprint of the key. */
    val fingerprint: String by lazy(LazyThreadSafetyMode.PUBLICATION) { Fingerprint.format(bytes) }

    override fun equals(other: Any?): Boolean =
        this === other || (other is PubkeyIdentity && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "PubkeyIdentity($fingerprint)"

    companion object {
        /** Wrap raw public-key bytes (copied defensively). */
        fun of(keyBytes: ByteArray): PubkeyIdentity = PubkeyIdentity(keyBytes.copyOf())
    }
}

/**
 * Seam for obtaining the athlete's public-key identity. The core keeps this an interface so tests
 * can supply deterministic keys; the Android Keystore-backed implementation is provided in the app
 * layer. Never generates or handles private keys inside the core.
 */
interface KeyProvider {
    /** The athlete's current public-key identity. */
    fun identity(): PubkeyIdentity
}
