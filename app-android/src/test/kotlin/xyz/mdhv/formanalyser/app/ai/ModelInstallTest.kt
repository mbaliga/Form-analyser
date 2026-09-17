package xyz.mdhv.formanalyser.app.ai

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelInstallTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `model import rejects a non-model extension`() {
        val source = tempSource("not-a-model.pdf", ByteArray(ModelInstall.MIN_MODEL_BYTES.toInt()))
        try {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                ModelInstall.install(context, Uri.fromFile(source), displayName = "not-a-model.pdf")
            }
        } finally {
            source.delete()
        }
    }

    @Test
    fun `model import rejects an empty file`() {
        val source = tempSource("empty.task", ByteArray(0))
        try {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                ModelInstall.install(context, Uri.fromFile(source))
            }
        } finally {
            source.delete()
        }
    }

    @Test
    fun `model import publishes a digest-named private copy`() {
        val bytes = ByteArray(ModelInstall.MIN_MODEL_BYTES.toInt()) { (it % 251).toByte() }
        val source = tempSource("gemma.task", bytes)
        var installed: String? = null
        try {
            installed = ModelInstall.install(context, Uri.fromFile(source))
            assertTrue(ModelInstall.isInstalled(installed))
            assertTrue(installed!!.contains("gemma-"))
            assertArrayEquals(bytes, File(installed).readBytes())
            assertFalse(installed!!.substringAfterLast('/').endsWith(".partial"))
        } finally {
            installed?.let { ModelInstall.remove(context, it) }
            source.delete()
        }
    }

    @Test
    fun `model removal cannot delete a file outside the private model directory`() {
        val outside = tempSource("outside.task", ByteArray(ModelInstall.MIN_MODEL_BYTES.toInt()))
        try {
            assertFalse(ModelInstall.remove(context, outside.absolutePath))
            assertTrue(outside.exists())
        } finally {
            outside.delete()
        }
    }

    private fun tempSource(name: String, bytes: ByteArray): File =
        File(context.cacheDir, "${UUID.randomUUID()}-$name").apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }
}
