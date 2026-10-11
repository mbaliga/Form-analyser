package xyz.mdhv.formanalyser.app.domain

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SafExportPublisherTest {
    private lateinit var directory: java.nio.file.Path

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("crocodyl-saf-export-")
    }

    @After
    fun tearDown() {
        Files.walk(directory).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }

    @Test
    fun `archive is complete before SAF destination is opened`() {
        val staging = File(directory.toFile(), "export.partial")
        val destination = ByteArrayOutputStream()
        var destinationOpened = false
        var cleanupCalled = false

        val result =
            SafExportPublisher.publish(
                stagingFile = staging,
                openDestination = {
                    assertArrayEquals("complete archive".toByteArray(), staging.readBytes())
                    destinationOpened = true
                    destination
                },
                removeIncompleteDestination = { cleanupCalled = true },
                writeArchive = { output ->
                    output.write("complete archive".toByteArray())
                    "signed"
                },
            )

        assertEquals("signed", result)
        assertTrue(destinationOpened)
        assertFalse(cleanupCalled)
        assertArrayEquals("complete archive".toByteArray(), destination.toByteArray())
        assertFalse(staging.exists())
        assertTrue(Files.list(directory).use { it.toList().isEmpty() })
    }

    @Test
    fun `archive generation failure never opens or deletes SAF destination`() {
        val staging = File(directory.toFile(), "export.partial")
        var destinationOpened = false
        var cleanupCalled = false

        val failure =
            runCatching {
                SafExportPublisher.publish(
                    stagingFile = staging,
                    openDestination = {
                        destinationOpened = true
                        ByteArrayOutputStream()
                    },
                    removeIncompleteDestination = { cleanupCalled = true },
                    writeArchive = { output ->
                        output.write("incomplete".toByteArray())
                        error("simulated archive failure")
                    },
                )
            }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertFalse(destinationOpened)
        assertFalse(cleanupCalled)
        assertFalse(staging.exists())
        assertTrue(Files.list(directory).use { it.toList().isEmpty() })
    }

    @Test
    fun `destination failure triggers cleanup and removes private staging`() {
        val staging = File(directory.toFile(), "export.partial")
        var cleanupCalled = false
        val brokenDestination =
            object : OutputStream() {
                override fun write(value: Int) {
                    throw IOException("simulated SAF write failure")
                }

                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    throw IOException("simulated SAF write failure")
                }
            }

        val failure =
            runCatching {
                SafExportPublisher.publish(
                    stagingFile = staging,
                    openDestination = { brokenDestination },
                    removeIncompleteDestination = { cleanupCalled = true },
                    writeArchive = { output ->
                        output.write("complete archive".toByteArray())
                        "signed"
                    },
                )
            }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(cleanupCalled)
        assertFalse(staging.exists())
        assertTrue(Files.list(directory).use { it.toList().isEmpty() })
    }

    @Test
    fun `startup removes orphaned staging but preserves active in-process export`() {
        val cache = directory.toFile()
        val orphan = File(cache, ".croc-export-orphan.partial").apply { writeText("stale") }
        val staging = SafExportPublisher.newStagingFile(cache)
        val destination = ByteArrayOutputStream()

        val message =
            SafExportPublisher.publish(
                stagingFile = staging,
                openDestination = {
                    SafExportPublisher.clearOrphanedStaging(cache)
                    assertFalse(orphan.exists())
                    assertTrue(staging.exists())
                    destination
                },
                removeIncompleteDestination = {},
                writeArchive = { output ->
                    output.write("complete".toByteArray())
                    "signed"
                },
            )

        assertEquals("signed", message)
        assertArrayEquals("complete".toByteArray(), destination.toByteArray())
        assertFalse(staging.exists())
        assertTrue(Files.list(directory).use { it.toList().isEmpty() })
    }
}
