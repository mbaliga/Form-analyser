package xyz.mdhv.formanalyser.app.domain

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShareExportPublisherTest {
    private lateinit var directory: java.nio.file.Path

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("crocodyl-share-export-")
    }

    @After
    fun tearDown() {
        Files.walk(directory).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }

    @Test
    fun successfulWritePublishesOnlyTheFinalArchive() {
        val destination = directory.resolve("crocodyl-export.crocbak").toFile()

        val message =
            ShareExportPublisher.publish(destination) { output ->
                output.write("archive".toByteArray())
                "complete"
            }

        assertEquals("complete", message)
        assertEquals("archive", destination.readText())
        assertEquals(listOf(destination.toPath()), Files.list(directory).use { it.toList() })
    }

    @Test
    fun failedWriteRemovesPartialAndFinalArtifacts() {
        val destination = directory.resolve("crocodyl-export.crocbak").toFile()
        destination.writeText("stale")

        val failure =
            runCatching {
                ShareExportPublisher.publish(destination) { output ->
                    output.write("partial".toByteArray())
                    error("simulated archive failure")
                }
            }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertFalse(destination.exists())
        assertTrue(Files.list(directory).use { it.toList().isEmpty() })
    }
}
