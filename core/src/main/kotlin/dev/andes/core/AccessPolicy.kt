package dev.andes.core

import java.time.Instant
import java.util.UUID

/** Issued only after signed purchased Java entitlements and same-token profile validation. */
class InstallGrant internal constructor(
    internal val sessionId: UUID,
    internal val profileId: UUID,
    val validUntil: Instant,
)

sealed interface OwnershipStatus {
    data object Unverified : OwnershipStatus
    data object Missing : OwnershipStatus
    data object ServiceUnavailable : OwnershipStatus
    data object Restricted : OwnershipStatus
    data object ApprovalRequired : OwnershipStatus
}

class InstallAccessPolicy {
    fun requireAccess(grant: InstallGrant?, activeSession: UUID?, now: Instant) {
        check(grant != null && activeSession != null && grant.sessionId == activeSession && now < grant.validUntil) {
            "Installation requires a current verified Java Edition entitlement and profile."
        }
    }
}

/** Deliberately exposes no game download operation while the Services contract is unverified. */
class MinecraftInstallationService(private val serviceConnectionConfigured: Boolean = false) {
    val status: OwnershipStatus = if (serviceConnectionConfigured) OwnershipStatus.Unverified else OwnershipStatus.ApprovalRequired
    fun installationBlocker(): String =
        if (serviceConnectionConfigured)
            "Installation is not implemented yet. Java Edition ownership must be verified before game files can be downloaded."
        else "Minecraft API approval and a validated entitlement adapter are required. No game files have been downloaded."
}

/** In-flight credential work must never restore a session after logout/account switching. */
class SessionEpoch {
    private var generation: Long = 0
    @Synchronized fun current(): Long = generation
    @Synchronized fun invalidate(): Long = ++generation
    @Synchronized fun isCurrent(value: Long): Boolean = value == generation
}
