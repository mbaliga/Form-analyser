package xyz.mdhv.formanalyser.app.domain

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.exchange.AndroidKeyProvider
import xyz.mdhv.formanalyser.exchange.CrocEnvelope
import xyz.mdhv.formanalyser.exchange.CrocPayloadEntry
import xyz.mdhv.formanalyser.exchange.CrocRejection
import xyz.mdhv.formanalyser.exchange.CrocVerdict
import xyz.mdhv.formanalyser.exchange.CrocVerifier

/**
 * Opens a `.croc` envelope someone sent, verifies it, and describes what is inside.
 *
 * **This slice deliberately stops at the preview.** Nothing here writes a row. Merging another
 * person's records into an athlete's history needs dedupe and conflict rules that do not exist yet,
 * and shipping a half-considered merge would break the promise that athlete history is never
 * silently altered. What the preview does buy, today, is the ability to exercise the whole
 * signature loop end to end on real devices — which is the part that cannot be tested off-device —
 * and to tell an athlete honestly whether a file they were sent is genuine.
 *
 * The envelope is read entirely into memory and never unpacked to disk. That is what makes zip
 * path-traversal a non-issue here: there is no filesystem write for a crafted entry name to reach.
 * Size caps below stop a decompression bomb from taking the process with it.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyProvider: AndroidKeyProvider,
) : ViewModel() {

    /** The last inspected envelope's verdict, or null before any file has been opened. */
    private val _verdict = MutableStateFlow<CrocVerdict?>(null)
    val verdict: StateFlow<CrocVerdict?> = _verdict

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    /** Total payload bytes read, shown in the preview so "size" is not a mystery. */
    private val _payloadBytes = MutableStateFlow(0L)
    val payloadBytes: StateFlow<Long> = _payloadBytes

    /** This device's own fingerprint, so the preview can say whether the file was addressed here. */
    private val _myFingerprint = MutableStateFlow<String?>(null)
    val myFingerprint: StateFlow<String?> = _myFingerprint

    fun load() {
        if (_myFingerprint.value != null) return
        viewModelScope.launch {
            _myFingerprint.value =
                withContext(Dispatchers.IO) {
                    runCatching { keyProvider.identity().fingerprint }.getOrNull()
                }
        }
    }

    /** Forget the last preview. Called when leaving the screen so nothing lingers on-screen. */
    fun clear() {
        _verdict.value = null
        _payloadBytes.value = 0L
    }

    /** Read and verify the envelope at [uri] (an `OpenDocument` result), off the main thread. */
    fun inspect(uri: Uri) {
        if (_busy.value) return
        _busy.value = true
        _verdict.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { readAndVerify(uri) } }
            _busy.value = false
            _verdict.value =
                result.getOrElse {
                    // An unreadable file is a rejection like any other: the athlete gets a reason,
                    // not a crash and not a blank screen.
                    CrocVerdict.Rejected(
                        CrocRejection.MALFORMED_ENVELOPE,
                        it.message ?: it.javaClass.simpleName,
                    )
                }
        }
    }

    private fun readAndVerify(uri: Uri): CrocVerdict {
        var manifestBytes: ByteArray? = null
        var signatureJson: String? = null
        val payload = ArrayList<CrocPayloadEntry>()
        var total = 0L
        var entries = 0

        val stream =
            context.contentResolver.openInputStream(uri) ?: error("cannot open that file")
        stream.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    if (++entries > MAX_ENTRIES) error("too many entries for one envelope")
                    val bytes = readBounded(zip, MAX_ENTRY_BYTES)
                    total += bytes.size
                    if (total > MAX_TOTAL_BYTES) error("envelope is larger than this import allows")
                    when {
                        entry.name == CrocEnvelope.ENTRY_MANIFEST -> manifestBytes = bytes
                        entry.name == CrocEnvelope.ENTRY_SIGNATURE ->
                            signatureJson = bytes.toString(Charsets.UTF_8)
                        else ->
                            CrocEnvelope.tableOfEntry(entry.name)?.let { table ->
                                payload.add(CrocPayloadEntry(table, bytes))
                            }
                    }
                    zip.closeEntry()
                }
            }
        }
        _payloadBytes.value = payload.sumOf { it.bytes.size.toLong() }

        val manifest =
            manifestBytes
                ?: return CrocVerdict.Rejected(
                    CrocRejection.MALFORMED_ENVELOPE,
                    "no ${CrocEnvelope.ENTRY_MANIFEST}: this is not an exchange envelope",
                )
        // A .crocbak lands here: it has a manifest but no signature, because it never claimed to
        // cross a trust boundary. Say that plainly rather than leaving "malformed" to be guessed at.
        val signature =
            signatureJson
                ?: return CrocVerdict.Rejected(
                    CrocRejection.MALFORMED_ENVELOPE,
                    "no ${CrocEnvelope.ENTRY_SIGNATURE}: unsigned, so there is nothing to check " +
                        "who it came from. A .crocbak backup looks like this — it is device " +
                        "recovery for yourself, not a file to accept from someone else.",
                )

        // pinnedSenderKey is null until there is a place to pin one. That means every sender reads
        // as FIRST_CONTACT today, which is the honest answer: this device has no memory of who it
        // has heard from, so it cannot claim a key changed. Pinning belongs with the Coach
        // relationship model, not bolted onto a preview screen.
        return CrocVerifier.verify(
            manifestBytes = manifest,
            signatureBytesJson = signature,
            payload = payload,
            pinnedSenderKey = null,
        )
    }

    /** Read the current zip entry, refusing anything over [limit] rather than growing to fit it. */
    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(READ_CHUNK)
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            if (out.size() + n > limit) error("an entry in this envelope is implausibly large")
            out.write(chunk, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        /**
         * MIME filter for the OpenDocument picker. Deliberately unrestricted: `.croc` has no
         * registered type, so providers report it as zip, as octet-stream, or as nothing at all
         * depending on where the file came from, and a narrower filter would grey out the very file
         * the athlete was told to open. The format check happens on the contents, where it belongs.
         */
        val OPEN_TYPES: Array<String> = arrayOf("*/*")

        private const val READ_CHUNK = 16 * 1024
        private const val MAX_ENTRY_BYTES = 16 * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
        private const val MAX_ENTRIES = 256
    }
}
