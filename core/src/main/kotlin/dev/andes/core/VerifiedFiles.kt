package dev.andes.core

import java.io.File
import java.security.MessageDigest

/** Utility only; not connected to proprietary download endpoints. */
object VerifiedFiles {
    fun resolve(root: File, relative: String): File {
        require(relative.isNotBlank() && !relative.contains('\\') && !relative.contains(':')) { "Invalid relative path" }
        require(!relative.startsWith('/') && relative.split('/').none { it == ".." || it == "." || it.isEmpty() }) {
            "Unsafe path"
        }
        val canonicalRoot = root.canonicalFile.toPath()
        val target = File(root, relative).canonicalFile
        require(target.toPath().startsWith(canonicalRoot) && target.toPath() != canonicalRoot) { "Path leaves installation" }
        return target
    }

    fun verify(file: File, size: Long, expectedSha1: String): Boolean {
        require(size >= 0 && expectedSha1.matches(Regex("[0-9a-f]{40}"))) { "Invalid trusted metadata" }
        if (!file.isFile || file.length() != size) return false
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                digest.update(bytes, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        return MessageDigest.isEqual(actual.toByteArray(Charsets.US_ASCII), expectedSha1.toByteArray(Charsets.US_ASCII))
    }
}
