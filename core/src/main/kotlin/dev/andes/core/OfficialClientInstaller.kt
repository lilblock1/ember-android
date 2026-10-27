package dev.andes.core

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Research client acquisition, not a complete game installation. No libraries/assets/runtime.
 * Every proprietary request, read and commit requires the current in-memory account grant.
 */
class OfficialClientInstaller(private val directory: File) {
    /** Rule-free Java dependencies for the startup experiment, including Guava and its helper.
     * Desktop natives, other libraries and game assets are intentionally unresolved.
     */
    fun downloadInitializationLibraries(grant: () -> InstallGrant?, session: UUID, active: () -> Boolean) {
        fun access() { check(active()); InstallAccessPolicy().requireAccess(grant(), session, Instant.now()) }
        access()
        val manifest = JSONObject(String(metadata("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"), Charsets.UTF_8))
        val versions = manifest.getJSONArray("versions")
        val entry = (0 until versions.length()).map { versions.getJSONObject(it) }.single { it.getString("id") == "1.20.4" }
        val bytes = metadata(entry.getString("url"))
        require(digest(bytes) == entry.getString("sha1"))
        val libraries = JavaLibraryPlan.select(JSONObject(String(bytes, Charsets.UTF_8)))
        val records = org.json.JSONArray()
        val objects = File(directory, "initialization-libraries")
        check(objects.mkdirs() || objects.isDirectory)
        for (library in libraries) {
            require(!library.has("rules") && !library.has("natives"))
            val artifact = library.getJSONObject("downloads").getJSONObject("artifact")
            val hash = artifact.getString("sha1")
            val size = artifact.getLong("size")
            require(hash.matches(Regex("[a-f0-9]{40}")) && size in 1..64L * 1024 * 1024)
            val uri = checkedUrl(artifact.getString("url"), "libraries.minecraft.net")
            require(uri.path.endsWith(".jar"))
            access()
            val target = File(objects, "$hash.jar")
            if (!target.isFile || target.length() != size || hash(target) != hash) {
                val staging = File.createTempFile("library-", ".partial", objects)
                try {
                    val connection = uri.toURL().openConnection() as HttpURLConnection
                    try {
                        configure(connection); require(connection.responseCode == 200)
                        var received = 0L
                        connection.inputStream.use { input -> staging.outputStream().use { output ->
                            val block = ByteArray(65536)
                            while (true) {
                                access(); val count = input.read(block); if (count < 0) break
                                received += count; require(received <= size); output.write(block, 0, count)
                            }
                            output.fd.sync()
                        } }
                        require(received == size && hash(staging) == hash)
                    } finally { connection.disconnect() }
                    access()
                    Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } finally { staging.delete() }
            }
            records.put(JSONObject().put("name", library.getString("name")).put("sha1", hash).put("size", size))
        }
        require(records.length() == libraries.size)
        access()
        File(objects, "verified.json").writeText(records.toString(2))
    }

    fun downloadResearchClient(grant: () -> InstallGrant?, session: UUID,
        active: () -> Boolean, progress: (Long, Long) -> Unit): File {
        fun access() {
            check(active()) { "Download cancelled." }
            InstallAccessPolicy().requireAccess(grant(), session, Instant.now())
        }
        access()
        val manifest = JSONObject(String(metadata("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"), Charsets.UTF_8))
        val versions = manifest.getJSONArray("versions")
        val entry = (0 until versions.length()).map { versions.getJSONObject(it) }.single { it.getString("id") == "1.20.4" }
        val bytes = metadata(entry.getString("url"))
        require(digest(bytes) == entry.getString("sha1")) { "Version metadata hash mismatch." }
        val version = JSONObject(String(bytes, Charsets.UTF_8))
        require(version.getString("id") == "1.20.4")
        val client = version.getJSONObject("downloads").getJSONObject("client")
        val size = client.getLong("size")
        require(size in 1..128L * 1024 * 1024)
        val sha1 = client.getString("sha1")
        require(sha1.matches(Regex("[a-f0-9]{40}")))
        val url = checkedUrl(client.getString("url"), "piston-data.mojang.com")
        require(url.path == "/v1/objects/$sha1/client.jar")
        check(directory.mkdirs() || directory.isDirectory)
        val destination = File(directory, "1.20.4-client.jar")
        if (destination.isFile && destination.length() == size && hash(destination) == sha1) {
            access(); progress(size, size); return destination
        }
        val staging = File.createTempFile("client-", ".partial", directory)
        try {
            access()
            val connection = url.toURL().openConnection() as HttpURLConnection
            try {
                configure(connection)
                require(connection.responseCode == 200) { "Official client download failed." }
                require(connection.contentLengthLong == -1L || connection.contentLengthLong == size)
                val digest = MessageDigest.getInstance("SHA-1")
                var received = 0L
                connection.inputStream.use { input -> staging.outputStream().use { output ->
                    val block = ByteArray(65536)
                    while (true) {
                        access()
                        val count = input.read(block)
                        if (count < 0) break
                        received += count
                        require(received <= size) { "Client download exceeds trusted size." }
                        output.write(block, 0, count); digest.update(block, 0, count)
                        progress(received, size)
                    }
                    output.fd.sync()
                } }
                require(received == size && hex(digest.digest()) == sha1) { "Client integrity check failed." }
            } finally { connection.disconnect() }
            access()
            Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            // Public JSON metadata is separate from the original client; no game files are modified.
            File(directory, "1.20.4.json").writeBytes(bytes)
            return destination
        } finally { staging.delete() }
    }

    private fun metadata(address: String): ByteArray {
        val uri = checkedUrl(address, "piston-meta.mojang.com")
        require(uri.path.endsWith(".json"))
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            configure(connection)
            require(connection.responseCode == 200)
            val result = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val block = ByteArray(8192)
                while (true) {
                    val count = input.read(block)
                    if (count < 0) break
                    if (result.size() + count > 8 * 1024 * 1024) throw IOException("Metadata exceeds limit")
                    result.write(block, 0, count)
                }
            }
            return result.toByteArray()
        } finally { connection.disconnect() }
    }
    private fun checkedUrl(value: String, host: String): URI = URI(value).also {
        require(it.scheme == "https" && it.host == host && it.port in listOf(-1, 443) &&
            it.rawUserInfo == null && it.rawQuery == null && it.rawFragment == null)
    }
    private fun configure(connection: HttpURLConnection) {
        connection.instanceFollowRedirects = false; connection.useCaches = false
        connection.connectTimeout = 15000; connection.readTimeout = 20000
        connection.setRequestProperty("Accept-Encoding", "identity")
    }
    private fun digest(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-1").digest(bytes))
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val block = ByteArray(65536)
            while (true) { val count = input.read(block); if (count < 0) break; digest.update(block, 0, count) }
        }
        return hex(digest.digest())
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
