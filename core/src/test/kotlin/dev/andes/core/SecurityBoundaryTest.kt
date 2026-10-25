package dev.andes.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.time.Instant
import java.util.UUID

class SecurityBoundaryTest {
    @Test fun microsoftLoginAloneNeverGrantsInstall() {
        assertThrows(IllegalStateException::class.java) {
            InstallAccessPolicy().requireAccess(null, UUID.randomUUID(), Instant.now())
        }
        assertEquals(OwnershipStatus.ApprovalRequired, MinecraftInstallationService().status)
    }
    @Test fun grantsExpireAndCannotCrossAccounts() {
        val session = UUID.randomUUID()
        val expiry = Instant.parse("2026-01-01T00:00:00Z")
        val grant = InstallGrant(session, UUID.randomUUID(), expiry)
        val policy = InstallAccessPolicy()
        policy.requireAccess(grant, session, expiry.minusSeconds(1))
        assertThrows(IllegalStateException::class.java) { policy.requireAccess(grant, session, expiry) }
        assertThrows(IllegalStateException::class.java) { policy.requireAccess(grant, UUID.randomUUID(), expiry.minusSeconds(1)) }
        assertThrows(IllegalStateException::class.java) { policy.requireAccess(grant, null, expiry.minusSeconds(1)) }
    }
    @Test fun logoutRejectsLateRefreshAndOldAccountCallbacks() {
        val epoch = SessionEpoch()
        val old = epoch.current()
        epoch.invalidate()
        assertFalse(epoch.isCurrent(old))
        assertTrue(epoch.isCurrent(epoch.current()))
    }
    @Test fun rejectsArchiveAndWindowsTraversal() {
        val root = Files.createTempDirectory("path-test").toFile()
        try {
            listOf("../world", "a/../../world", "/tmp/file", "C:/secret", "a\\..\\secret", "a//b", ".", "").forEach {
                assertThrows(it, IllegalArgumentException::class.java) { VerifiedFiles.resolve(root, it) }
            }
            assertEquals(root.resolve("assets/ab/file").canonicalFile, VerifiedFiles.resolve(root, "assets/ab/file"))
        } finally { root.deleteRecursively() }
    }
    @Test fun rejectsTruncatedAndCorruptedArtifacts() {
        val file = Files.createTempFile("integrity-test", ".part").toFile()
        try {
            file.writeText("abc")
            val hash = "a9993e364706816aba3e25717850c26c9cd0d89d"
            assertTrue(VerifiedFiles.verify(file, 3, hash))
            assertFalse(VerifiedFiles.verify(file, 4, hash))
            file.writeText("abd")
            assertFalse(VerifiedFiles.verify(file, 3, hash))
            assertThrows(IllegalArgumentException::class.java) { VerifiedFiles.verify(file, 3, "invalid") }
        } finally { file.delete() }
    }
}
