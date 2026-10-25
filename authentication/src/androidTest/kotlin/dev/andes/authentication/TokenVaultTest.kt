package dev.andes.authentication

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TokenVaultTest {
    @Test fun encryptedRoundTripTamperDetectionAndLogout() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vault = TokenVault(context, "instrumentation-test")
        try {
            vault.write("private-test-refresh-token")
            val file = File(context.noBackupFilesDir, "instrumentation-test.enc")
            assertFalse(String(file.readBytes()).contains("private-test-refresh-token"))
            assertEquals("private-test-refresh-token", vault.read())
            val bytes = file.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertThrows(Exception::class.java) { vault.read() }
        } finally { vault.clear() }
        assertNull(vault.read())
    }
}
