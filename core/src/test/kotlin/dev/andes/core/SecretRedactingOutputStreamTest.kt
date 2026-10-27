package dev.andes.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class SecretRedactingOutputStreamTest {
    @Test fun fragmentedWritesAndFlushesCannotLeakLaunchSecrets() {
        val target = ByteArrayOutputStream()
        val filter = SecretRedactingOutputStream(target, listOf("private-access-token", "PlayerName"))
        filter.write("access=private-access-".toByteArray()); filter.flush()
        assertEquals(0, target.size())
        filter.write("token user=PlayerName\n".toByteArray()); filter.flush()
        assertEquals("access=[REDACTED] user=[REDACTED]\n", target.toString("UTF-8"))
        assertFalse(filter.toString().contains("private-access-token"))
    }
    @Test fun oversizedAndUnterminatedLinesAreWithheld() {
        val target = ByteArrayOutputStream()
        val filter = SecretRedactingOutputStream(target, listOf("secret"))
        filter.write(("x".repeat(17000) + "secret\n").toByteArray())
        filter.write("unfinished secret".toByteArray()); filter.close()
        assertEquals("[oversize game log line withheld]\n", target.toString("UTF-8"))
        target.write('!'.code) // caller's stream remains usable
        assertFalse(target.toString("UTF-8").contains("secret"))
    }
}
