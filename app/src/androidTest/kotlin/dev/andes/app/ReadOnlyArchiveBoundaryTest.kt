package dev.andes.app

import androidx.test.platform.app.InstrumentationRegistry
import dev.ember.platform.JarFileSystems
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.ReadOnlyFileSystemException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReadOnlyArchiveBoundaryTest {
    @Test fun androidCanReadAndWalkOriginalArchiveFixtureWithoutExtracting() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = File.createTempFile("jarfs-fixture-", ".zip", context.cacheDir)
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("nested/hello.txt")); zip.write("Original archive fixture".toByteArray()); zip.closeEntry()
            }
            val uri = URI.create("jar:" + archive.toURI().toASCIIString())
            val provider = JarFileSystems.installedProviders().single { it.scheme == "jar" }
            assertEquals("dev.ember.platform.ReadOnlyJarProvider", provider.javaClass.name)
            JarFileSystems.newFileSystem(uri, emptyMap<String, String>()).use { fs ->
                val path = fs.getPath("/nested/hello.txt")
                assertTrue(fs.isReadOnly)
                assertEquals("Original archive fixture", String(Files.readAllBytes(path)))
                assertEquals(path, JarFileSystems.get(path.toUri()))
                Files.walk(fs.getPath("/")).use { assertEquals(3L, it.count()) }
                assertThrows(ReadOnlyFileSystemException::class.java) { Files.write(path, byteArrayOf(1)) }
            }
        } finally { archive.delete() }
    }
}
