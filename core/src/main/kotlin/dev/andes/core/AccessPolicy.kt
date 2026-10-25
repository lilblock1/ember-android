package dev.andes.core

import java.time.Instant
import java.util.UUID

/** No production grant issuer exists until an approved, validated Services adapter exists. */
class InstallGrant internal constructor(
    internal val sessionId: UUID,
    internal val profileId: UUID,
    internal val validUntil: Instant,
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
class MinecraftInstallationService {
    val status: OwnershipStatus = OwnershipStatus.ApprovalRequired
    fun installationBlocker(): String =
        "Minecraft API approval and a validated entitlement adapter are required. No game files have been downloaded."
}

/** In-flight credential work must never restore a session after logout/account switching. */
class SessionEpoch {
    private var generation: Long = 0
    @Synchronized fun current(): Long = generation
    @Synchronized fun invalidate(): Long = ++generation
    @Synchronized fun isCurrent(value: Long): Boolean = value == generation
}
