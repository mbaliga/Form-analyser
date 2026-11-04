package xyz.mdhv.formanalyser.app.domain

import java.io.File
import java.io.OutputStream
import java.util.UUID

/**
 * Publishes a cache-backed share export as one visible file.
 *
 * The writer is given a hidden file in the destination directory. A completed archive is then
 * published with a same-directory rename. Any writer or publication failure removes both the
 * partial file and the destination so callers cannot mistake an interrupted archive for a valid
 * share export.
 */
internal object ShareExportPublisher {
    fun publish(destination: File, write: (OutputStream) -> String): String {
        val directory = requireNotNull(destination.parentFile) { "Share export needs a directory" }
        directory.mkdirs()
        val staging = File(directory, ".${UUID.randomUUID()}.partial")
        return try {
            val result = staging.outputStream().use(write)
            check(staging.renameTo(destination)) { "Could not publish share export" }
            result
        } catch (t: Throwable) {
            runCatching { staging.delete() }
            runCatching { destination.delete() }
            throw t
        }
    }
}
