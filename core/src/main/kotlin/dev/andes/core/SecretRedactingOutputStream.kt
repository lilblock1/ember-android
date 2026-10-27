package dev.andes.core

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** Bounded line redaction for the isolated game startup probe. Fragmented writes/flushes
 * cannot emit partial secrets. Oversize lines and unterminated partial lines are dropped.
 * Does not close the caller's underlying system stream.
 */
class SecretRedactingOutputStream(private val output: OutputStream, secrets: Collection<String>) : OutputStream() {
    private val secrets = secrets.filter { it.isNotEmpty() }.sortedByDescending { it.length }
    private val line = ByteArrayOutputStream()
    private var dropping = false
    @Synchronized override fun write(value: Int) {
        if (value == 10) {
            if (dropping) output.write("[oversize game log line withheld]\n".toByteArray(Charsets.UTF_8))
            else {
                var text = line.toString(Charsets.UTF_8.name())
                for (secret in secrets) text = text.replace(secret, "[REDACTED]")
                output.write((text + "\n").toByteArray(Charsets.UTF_8))
            }
            line.reset(); dropping = false
        } else if (!dropping) {
            if (line.size() >= 16384) { line.reset(); dropping = true }
            else line.write(value)
        }
    }
    @Synchronized override fun write(bytes: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        for (index in offset until offset + length) write(bytes[index].toInt() and 255)
    }
    @Synchronized override fun flush() { output.flush() }
    @Synchronized override fun close() { line.reset(); dropping = false; output.flush() }
    override fun toString() = "SecretRedactingOutputStream([REDACTED])"
}
