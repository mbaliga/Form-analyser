package xyz.mdhv.formanalyser.app.domain

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import xyz.mdhv.formanalyser.app.awaitUntil
import xyz.mdhv.formanalyser.app.exchange.AndroidKeyProvider
import xyz.mdhv.formanalyser.exchange.ConsentFilter
import xyz.mdhv.formanalyser.exchange.CrocEnvelope
import xyz.mdhv.formanalyser.exchange.CrocEnvelopeKind
import xyz.mdhv.formanalyser.exchange.CrocManifest
import xyz.mdhv.formanalyser.exchange.CrocPayloadEntry
import xyz.mdhv.formanalyser.exchange.CrocRejection
import xyz.mdhv.formanalyser.exchange.CrocSigning
import xyz.mdhv.formanalyser.exchange.CrocVerdict
import xyz.mdhv.formanalyser.exchange.ExportTier
import xyz.mdhv.formanalyser.exchange.PayloadChecksum
import xyz.mdhv.formanalyser.exchange.PubkeyIdentity
import xyz.mdhv.formanalyser.exchange.SealedCroc
import xyz.mdhv.formanalyser.exchange.SignatureAlgorithms
import xyz.mdhv.formanalyser.exchange.SigningKeyProvider

/**
 * Exercises [ImportViewModel.inspect] against real `.croc` bytes built with `core-exchange`'s own
 * production seal/verify code — the same real signature loop
 * [xyz.mdhv.formanalyser.exchange.CrocEnvelopeTest] exercises on plain JVM crypto, here read back
 * through the Android-facing zip/`ContentResolver` path this ViewModel actually owns
 * ([ImportViewModel.readAndVerify]'s zip parsing, entry-size caps, and the malformed/`.crocbak`
 * special-casing are untested anywhere else).
 *
 * [AndroidKeyProvider] is mocked, not real: it is only ever touched by [ImportViewModel.load] (this
 * device's own fingerprint for the preview banner), which none of these tests call, and its real
 * implementation needs the Android Keystore, which Robolectric does not provide. A `file://` [Uri]
 * stands in for the `OpenDocument` result the real screen would hand [ImportViewModel.inspect] —
 * `ContentResolver.openInputStream` opens a plain `FileInputStream` for that scheme without needing
 * a registered `ContentProvider`, so no Robolectric shadow beyond the default one is required.
 *
 * Not device-verified — nothing in this environment can run an Android test; CI's Robolectric run
 * (`android.yml`) is the judge, same caveat as every other Android-layer test here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ImportViewModelTest {

    private lateinit var context: Context
    private lateinit var viewModel: ImportViewModel

    /**
     * A [SigningKeyProvider] over an in-memory P-256 keypair — the exact test double
     * `core-exchange`'s own `CrocEnvelopeTest` uses as a stand-in for `AndroidKeyProvider`'s real
     * Keystore-backed one. Duplicated here rather than shared because core-exchange's test sources
     * are not exposed to app-android's test compilation.
     */
    private class FakeSigningKeyProvider(private val pair: KeyPair) : SigningKeyProvider {
        override val algorithm: String = SignatureAlgorithms.ECDSA_P256_SHA256

        override fun identity(): PubkeyIdentity = PubkeyIdentity.of(pair.public.encoded)

        override fun sign(bytes: ByteArray): ByteArray =
            Signature.getInstance(SignatureAlgorithms.ECDSA_P256_SHA256).run {
                initSign(pair.private)
                update(bytes)
                sign()
            }
    }

    private fun newSigner(): FakeSigningKeyProvider =
        FakeSigningKeyProvider(
            KeyPairGenerator.getInstance("EC")
                .apply { initialize(ECGenParameterSpec("secp256r1")) }
                .generateKeyPair()
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        viewModel = ImportViewModel(context, mock())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun writeZip(name: String, entries: Map<String, ByteArray>): Uri {
        val file = File(context.cacheDir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((entryName, bytes) in entries) {
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return Uri.fromFile(file)
    }

    /** A genuinely signed, single-table SHAREABLE_ONLY envelope — mirrors CrocEnvelopeTest's helper. */
    private fun sealValidEnvelope(): Pair<SealedCroc, List<CrocPayloadEntry>> {
        val signer = newSigner()
        val payload = listOf(CrocPayloadEntry("session", "[]".toByteArray()))
        val decision =
            ConsentFilter.filter(payload.map { it.logicalTable }.toSet(), ExportTier.SHAREABLE_ONLY, emptySet())
        val manifest =
            CrocManifest.fromDecision(
                appVersion = "0.6.0-test",
                createdAtMs = 1_700_000_000_000L,
                kind = CrocEnvelopeKind.ATHLETE_REPORT,
                sender = signer.identity(),
                senderLabel = "Test athlete",
                tier = ExportTier.SHAREABLE_ONLY,
                decision = decision,
                payloadChecksum = PayloadChecksum.of(payload),
                rowCounts = payload.associate { it.logicalTable to 1L },
            )
        return CrocSigning.seal(manifest, signer) to payload
    }

    @Test
    fun aValidlySignedEnvelope_isAcceptedAndItsPayloadSizeIsReported() {
        val (sealed, payload) = sealValidEnvelope()
        val uri =
            writeZip(
                "valid.croc",
                mapOf(
                    CrocEnvelope.ENTRY_MANIFEST to sealed.manifestBytes,
                    CrocEnvelope.ENTRY_SIGNATURE to sealed.signature.serialize().toByteArray(),
                    CrocEnvelope.tableEntry("session") to payload.single().bytes,
                ),
            )

        viewModel.inspect(uri)
        val verdict = viewModel.verdict.awaitUntil { it != null }

        assertTrue("expected Verified, was $verdict", verdict is CrocVerdict.Verified)
        assertEquals(payload.single().bytes.size.toLong(), viewModel.payloadBytes.value)
    }

    @Test
    fun aCrocbakShapedFile_isRejectedWithItsOwnExplanationRatherThanAGenericOne() {
        // A manifest with no signature — exactly what ExportViewModel's .crocbak writer produces,
        // since a backup to yourself never crosses a trust boundary. See
        // ImportViewModel.readAndVerify's KDoc for why this earns its own message.
        val uri = writeZip("backup.crocbak", mapOf(CrocEnvelope.ENTRY_MANIFEST to "{}".toByteArray()))

        viewModel.inspect(uri)
        val verdict = viewModel.verdict.awaitUntil { it != null } as CrocVerdict.Rejected

        assertEquals(CrocRejection.MALFORMED_ENVELOPE, verdict.reason)
        assertTrue(verdict.detail.contains(".crocbak", ignoreCase = true))
    }

    @Test
    fun garbageInput_isRejectedNeverCrashes() {
        val file = File(context.cacheDir, "garbage.croc")
        file.writeBytes(ByteArray(32) { it.toByte() }) // not even a zip
        val uri = Uri.fromFile(file)

        viewModel.inspect(uri)
        val verdict = viewModel.verdict.awaitUntil { it != null }

        assertTrue(verdict is CrocVerdict.Rejected)
    }

    @Test
    fun aTamperedPayload_failsTheChecksumRatherThanBeingSilentlyAccepted() {
        val (sealed, _) = sealValidEnvelope()
        val uri =
            writeZip(
                "tampered.croc",
                mapOf(
                    CrocEnvelope.ENTRY_MANIFEST to sealed.manifestBytes,
                    CrocEnvelope.ENTRY_SIGNATURE to sealed.signature.serialize().toByteArray(),
                    // The signed manifest's checksum was computed over "[]"; swap in different bytes
                    // so the zip's actual payload no longer matches what was signed.
                    CrocEnvelope.tableEntry("session") to "[{\"injected\":true}]".toByteArray(),
                ),
            )

        viewModel.inspect(uri)
        val verdict = viewModel.verdict.awaitUntil { it != null } as CrocVerdict.Rejected

        assertEquals(CrocRejection.PAYLOAD_CHECKSUM_MISMATCH, verdict.reason)
    }
}
