package xyz.mdhv.formanalyser.app.domain

import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds a complete archive privately before opening a user-selected SAF destination. If the
 * destination cannot accept the finished bytes, the caller removes the newly-created document so
 * an incomplete `.croc` is not left looking like a successful export.
 */
internal object SafExportPublisher {
    private val activeStagingPaths = ConcurrentHashMap.newKeySet<String>()

    /** Remove private staging files left behind when Android killed a previous process. */
    @Synchronized
    fun clearOrphanedStaging(cacheDirectory: File) {
        cacheDirectory.listFiles { file ->
            file.name.startsWith(".croc-export-") && file.name.endsWith(".partial")
        }?.filterNot { it.absolutePath in activeStagingPaths }
            ?.forEach { it.delete() }
    }

    /** Register before writing so an Activity recreation in this process cannot prune live output. */
    @Synchronized
    fun newStagingFile(cacheDirectory: File): File {
        clearOrphanedStaging(cacheDirectory)
        return File(cacheDirectory, ".croc-export-${UUID.randomUUID()}.partial")
            .also { activeStagingPaths += it.absolutePath }
    }

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
            activeStagingPaths -= stagingFile.absolutePath
        }
    }
}
