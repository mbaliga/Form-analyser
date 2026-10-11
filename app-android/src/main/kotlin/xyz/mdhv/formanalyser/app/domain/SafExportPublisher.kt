package xyz.mdhv.formanalyser.app.domain

import java.io.File
import java.io.OutputStream

/**
 * Builds a complete archive privately before opening a user-selected SAF destination. If the
 * destination cannot accept the finished bytes, the caller removes the newly-created document so
 * an incomplete `.croc` is not left looking like a successful export.
 */
internal object SafExportPublisher {
    fun publish(
        stagingFile: File,
        openDestination: () -> OutputStream?,
        removeIncompleteDestination: () -> Unit,
        writeArchive: (OutputStream) -> String,
    ): String {
        var destinationTouched = false
        try {
            val message = ShareExportPublisher.publish(stagingFile, writeArchive)
            destinationTouched = true
            val destination = openDestination() ?: error("cannot open destination")
            stagingFile.inputStream().use { input ->
                destination.use { output -> input.copyTo(output) }
            }
            return message
        } catch (failure: Throwable) {
            if (destinationTouched) {
                runCatching(removeIncompleteDestination).exceptionOrNull()?.let(failure::addSuppressed)
            }
            throw failure
        } finally {
            runCatching { stagingFile.delete() }
        }
    }
}
