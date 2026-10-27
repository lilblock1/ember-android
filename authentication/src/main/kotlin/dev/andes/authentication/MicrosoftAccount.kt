package dev.andes.authentication

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.andes.core.InstallGrant
import dev.andes.core.OfficialClientInstaller
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import dev.andes.core.SessionEpoch
import dev.andes.core.AuthorizationBoundary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.openid.appauth.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AccountStatus(val signedIn: Boolean = false, val busy: Boolean = false,
    val message: String = "Microsoft account not connected. Minecraft ownership is unverified.",
    val minecraft: String = "Minecraft account not connected. Ownership is unverified.",
    val needsMinecraftSignIn: Boolean = false, val ownershipVerified: Boolean = false,
    val clientDownloaded: Boolean = false, val downloadProgress: String = "")

/** Browser PKCE plus an approval-gated Xbox/XSTS/Minecraft Services chain. */
class MicrosoftAccount(context: Context) {
    private val clientDirectory = File(context.filesDir, "research-client")
    private var installGrant: InstallGrant? = null
    private var activeSession = UUID.randomUUID()
    private val flags = context.getSharedPreferences("session-control", Context.MODE_PRIVATE)
    private val credentials = TokenVault(context, "account")
    private val pending = TokenVault(context, "pending-auth")
    private val minecraftCredentials = TokenVault(context, "minecraft-session")
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val approval = ServicesApproval(BuildConfig.CLIENT_ID, BuildConfig.REGISTRATION_REFERENCE,
        BuildConfig.SERVICES_APPROVAL_REFERENCE, BuildConfig.SERVICES_OAUTH_SCOPES.split(' ').filter { it.isNotBlank() })
    private val epoch = SessionEpoch()
    private var authState = AuthState()
    private val mutable = MutableStateFlow(signedOutStatus())
    val status = mutable.asStateFlow()
    val servicesAccessEnabled: Boolean get() = approval.enabled
    val configured: Boolean get() = BuildConfig.CLIENT_ID.matches(Regex("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")) &&
        BuildConfig.REGISTRATION_REFERENCE.isNotBlank()
    private val service = AuthorizationService(context, AppAuthConfiguration.Builder()
        .setConnectionBuilder { uri ->
            require(uri.scheme == "https" && uri.host == "login.microsoftonline.com")
            (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 20_000; instanceFollowRedirects = false
            }
        }.build())

    init {
        try {
            if (!flags.getBoolean("disabled", false)) credentials.read()?.let { authState = AuthState.jsonDeserialize(it) }
            if (authState.isAuthorized && authState.lastAuthorizationResponse?.request?.clientId == BuildConfig.CLIENT_ID) {
                mutable.value = connectedStatus(authState)
                if (approval.enabled && !mutable.value.needsMinecraftSignIn && minecraftCredentials.read() != null) verifyOwnership()
            } else if (authState.isAuthorized) logout()
        } catch (_: Exception) {
            runCatching { credentials.clear() }
            runCatching { pending.clear() }
            runCatching { minecraftCredentials.clear() }
            authState = AuthState()
            mutable.value = AccountStatus(message = "Credential storage is unavailable or changed. Sign in again.")
        }
    }

    private fun grantedScopes(candidate: AuthState): String = candidate.lastTokenResponse?.scope
        ?: candidate.lastAuthorizationResponse?.request?.scope.orEmpty()

    private fun signedOutStatus(message: String = "Microsoft account not connected. Minecraft ownership is unverified.") =
        AccountStatus(message = message, minecraft = if (approval.enabled)
            "Minecraft account connection is available. Sign in with Microsoft."
        else "Minecraft Services is not configured for this build.")

    private fun connectedStatus(candidate: AuthState): AccountStatus {
        val needsConsent = approval.enabled && !approval.acceptsGrantedScopes(grantedScopes(candidate))
        return AccountStatus(true, message = "Microsoft connected. Java ownership is checked separately below.",
            minecraft = when {
                !approval.enabled -> "Minecraft Services is not configured for this build."
                needsConsent -> "Your current Microsoft session has no Xbox permission. Tap Connect Minecraft account to authorize it."
                else -> "Minecraft account connection is ready to check. Ownership is unverified."
            }, needsMinecraftSignIn = needsConsent)
    }

    @Synchronized fun begin(): Intent? {
        if (!configured) {
            mutable.value = status.value.copy(message = "Sign-in needs your own registered Microsoft public-client ID and registration evidence. See docs/AUTHENTICATION.md.")
            return null
        }
        if (status.value.busy) return null
        if (flags.getBoolean("cleanup-failed", false)) {
            mutable.value = status.value.copy(message = "Clear this app's storage before signing in; previous credential cleanup failed.")
            return null
        }
        epoch.invalidate()
        installGrant = null
        activeSession = UUID.randomUUID()
        if (runCatching { minecraftCredentials.clear() }.isFailure) {
            flags.edit().putBoolean("cleanup-failed", true).commit()
            mutable.value = status.value.copy(message = "Could not clear previous Minecraft session. Clear app storage before switching accounts.")
            return null
        }
        mutable.value = status.value.copy(minecraft = "Minecraft ownership is unverified.", ownershipVerified = false, clientDownloaded = false, downloadProgress = "")
        val configuration = AuthorizationServiceConfiguration(
            Uri.parse("https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize"),
            Uri.parse("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"))
        val request = AuthorizationRequest.Builder(configuration, BuildConfig.CLIENT_ID,
            ResponseTypeValues.CODE, REDIRECT)
            .setScopes(if (approval.enabled) approval.scopes else listOf("openid", "profile", "offline_access"))
            .setPrompt("select_account")
            .build() // AppAuth generates state, nonce and an S256 PKCE verifier/challenge.
        return try {
            pending.write(JSONObject().put("state", request.state).put("created", System.currentTimeMillis()).toString())
            val intent = service.getAuthorizationRequestIntent(request)
            mutable.value = status.value.copy(busy = true, message = "Complete Microsoft sign-in in your browser.")
            intent
        } catch (_: Exception) {
            runCatching { pending.clear() }
            mutable.value = status.value.copy(busy = false, message = "Could not open a secure browser or credential store.")
            null
        }
    }

    @Synchronized fun complete(data: Intent?) {
        val transaction = try { pending.read()?.let { JSONObject(it) } } catch (_: Exception) { null }
        val consumed = runCatching { pending.clear() }.isSuccess
        if (!consumed) {
            epoch.invalidate()
            installGrant = null
            activeSession = UUID.randomUUID()
            flags.edit().putBoolean("cleanup-failed", true).commit()
            mutable.value = status.value.copy(busy = false, message = "Could not securely consume sign-in state. Clear this app's storage before signing in again.")
            return
        }
        if (transaction == null || flags.getBoolean("cleanup-failed", false)) {
            mutable.value = status.value.copy(busy = false, message = "Sign-in expired or was cancelled. Start again.")
            return
        }
        val response = data?.let { runCatching { AuthorizationResponse.fromIntent(it) }.getOrNull() }
        val error = data?.let { runCatching { AuthorizationException.fromIntent(it) }.getOrNull() }
        if (error != null || response == null) {
            mutable.value = status.value.copy(busy = false, message = "Sign-in cancelled or rejected. You can retry.")
            return
        }
        if (!AuthorizationBoundary.accepts(transaction.optString("state", ""), response.state,
            BuildConfig.CLIENT_ID, response.request.clientId, REDIRECT.toString(), response.request.redirectUri.toString(),
            transaction.optLong("created", Long.MIN_VALUE), System.currentTimeMillis(), response.authorizationCode)) {
            mutable.value = status.value.copy(busy = false, message = "Sign-in response did not match the pending request.")
            return
        }
        val ticket = epoch.current()
        val candidate = AuthState(response, null)
        mutable.value = status.value.copy(busy = true, message = "Securing Microsoft session…")
        service.performTokenRequest(response.createTokenExchangeRequest()) { token, exception ->
            synchronized(this) {
                if (!epoch.isCurrent(ticket)) return@performTokenRequest
                candidate.update(token, exception)
                if (exception != null || !candidate.isAuthorized) {
                    mutable.value = status.value.copy(busy = false, message = "Microsoft token exchange failed or timed out. Retry sign-in.")
                } else save(candidate)
            }
        }
    }

    @Synchronized fun refresh() {
        if (!authState.isAuthorized || status.value.busy) return
        val ticket = epoch.current()
        val candidate = AuthState.jsonDeserialize(authState.jsonSerializeString())
        mutable.value = status.value.copy(busy = true, message = "Checking Microsoft session…")
        candidate.performActionWithFreshTokens(service) { access, _, exception ->
            synchronized(this) {
                if (!epoch.isCurrent(ticket)) return@performActionWithFreshTokens
                if (exception != null || access.isNullOrBlank()) {
                    if (exception?.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR && exception.error == "invalid_grant") {
                        logout()
                        mutable.value = AccountStatus(message = "Microsoft session expired or was revoked. Sign in again.")
                    } else mutable.value = status.value.copy(busy = false, message = "Microsoft session could not be refreshed. Check your connection and retry.")
                } else save(candidate)
            }
        }
    }

    private fun save(candidate: AuthState) {
        try {
            credentials.write(candidate.jsonSerializeString())
            check(flags.edit().putBoolean("disabled", false).commit())
            authState = candidate
            mutable.value = connectedStatus(candidate)
            startMinecraftServices(candidate)
        } catch (_: Exception) {
            logout()
            mutable.value = AccountStatus(message = "Could not protect credentials. Session removed; sign in again.")
        }
    }

    private fun startMinecraftServices(candidate: AuthState) {
        if (!approval.enabled) return
        if (!approval.acceptsGrantedScopes(grantedScopes(candidate))) {
            mutable.value = status.value.copy(needsMinecraftSignIn = true,
                minecraft = "Your current Microsoft session has no Xbox permission. Tap Connect Minecraft account to authorize it.")
            return
        }
        val access = candidate.accessToken ?: return
        val ticket = epoch.current()
        mutable.value = status.value.copy(busy = true, minecraft = "Connecting Minecraft account…")
        background.launch {
            val result = MinecraftServicesClient().authenticate(access, approval, { epoch.isCurrent(ticket) }) { stage ->
                synchronized(this@MicrosoftAccount) {
                    if (epoch.isCurrent(ticket)) mutable.value = status.value.copy(minecraft = when (stage) {
                        ServicesStage.XBOX -> "Authenticating with Xbox Live…"
                        ServicesStage.XSTS -> "Checking Xbox account authorization…"
                        ServicesStage.MINECRAFT -> "Connecting to Minecraft Services…"
                        ServicesStage.ENTITLEMENTS -> "Retrieving Minecraft entitlement evidence…"
                        ServicesStage.PROFILE -> "Retrieving Minecraft Java profile…"
                    })
                }
            }
            synchronized(this@MicrosoftAccount) {
                if (!epoch.isCurrent(ticket)) return@synchronized
                when (result) {
                    is ServicesResult.Connected -> {
                        try {
                            minecraftCredentials.write(result.session.encryptedPayload(BuildConfig.CLIENT_ID))
                            mutable.value = status.value.copy(busy = false,
                                minecraft = "Minecraft profile connected: ${result.session.profileName}. Checking Java ownership...", ownershipVerified = false)
                            verifyOwnership()
                        } catch (_: Exception) {
                            logout()
                            mutable.value = AccountStatus(message = "Could not protect Minecraft credentials. Session removed.")
                        }
                    }
                    is ServicesResult.Failed -> mutable.value = status.value.copy(busy = false, minecraft = failureMessage(result.reason))
                }
            }
        }
    }

    @Synchronized fun verifyOwnership() {
        if (status.value.busy || !status.value.signedIn) return
        val ticket = epoch.current()
        installGrant = null
        mutable.value = status.value.copy(busy = true, ownershipVerified = false,
            minecraft = "Verifying signed Java purchase license and profile...")
        background.launch {
            try {
                val session = JSONObject(requireNotNull(minecraftCredentials.read()))
                val grant = MinecraftOwnershipClient().verify(session, activeSession, approval)
                synchronized(this@MicrosoftAccount) {
                    if (epoch.isCurrent(ticket)) {
                        installGrant = grant
                        mutable.value = status.value.copy(busy = false, ownershipVerified = true,
                            minecraft = "Java Edition purchase ownership verified. Signed license and account profile match.")
                    }
                }
            } catch (failure: Exception) {
                synchronized(this@MicrosoftAccount) {
                    if (epoch.isCurrent(ticket)) mutable.value = status.value.copy(busy = false, ownershipVerified = false,
                        minecraft = ownershipFailure(failure))
                }
            }
        }
    }

    /** Always rechecks online ownership before acquiring any proprietary client bytes. */
    @Synchronized fun downloadResearchClient() {
        if (status.value.busy || !status.value.signedIn) return
        val ticket = epoch.current()
        val sessionId = activeSession
        installGrant = null
        mutable.value = status.value.copy(busy = true, ownershipVerified = false,
            downloadProgress = "Rechecking ownership before official client download...")
        background.launch {
            try {
                val session = JSONObject(requireNotNull(minecraftCredentials.read()))
                val grant = MinecraftOwnershipClient().verify(session, sessionId, approval)
                synchronized(this@MicrosoftAccount) {
                    check(epoch.isCurrent(ticket))
                    installGrant = grant
                    mutable.value = status.value.copy(ownershipVerified = true,
                        minecraft = "Java Edition purchase ownership verified.")
                }
                var lastPercent = -1
                OfficialClientInstaller(clientDirectory).downloadResearchClient(
                    { synchronized(this@MicrosoftAccount) { installGrant } }, sessionId,
                    { epoch.isCurrent(ticket) }) { received, size ->
                    val percent = (received * 100 / size).toInt()
                    if (percent != lastPercent) synchronized(this@MicrosoftAccount) {
                        lastPercent = percent
                        if (epoch.isCurrent(ticket)) mutable.value = status.value.copy(downloadProgress = "Official 1.20.4 client: $percent% ... checking integrity before committing.")
                    }
                }
                synchronized(this@MicrosoftAccount) {
                    if (epoch.isCurrent(ticket)) mutable.value = status.value.copy(downloadProgress = "Verifying initialization dependencies from Mojang...")
                }
                OfficialClientInstaller(clientDirectory).downloadInitializationLibraries(
                    { synchronized(this@MicrosoftAccount) { installGrant } }, sessionId, { epoch.isCurrent(ticket) })
                synchronized(this@MicrosoftAccount) {
                    if (epoch.isCurrent(ticket)) mutable.value = status.value.copy(busy = false, clientDownloaded = true,
                        downloadProgress = "Original Minecraft 1.20.4 client downloaded and SHA-1 verified. Initialization dependencies verified. Other libraries, assets and execution support remain incomplete.")
                }
            } catch (failure: Exception) {
                synchronized(this@MicrosoftAccount) {
                    if (epoch.isCurrent(ticket)) mutable.value = status.value.copy(busy = false,
                        downloadProgress = if (failure is IOException) "Download or ownership service connection failed. Existing files were preserved; retry when connected."
                            else "Download stopped. Ownership, cancellation, metadata or integrity checks did not pass.",
                        minecraft = if (failure is OwnershipCheckFailure) failure.reason else status.value.minecraft)
                }
            }
        }
    }

    private fun ownershipFailure(failure: Exception): String = when (failure) {
        is OwnershipCheckFailure -> failure.reason
        is IOException -> "Ownership service could not be reached. This is not a missing-license result. Retry when connected."
        else -> "Java ownership could not be verified. The signed response was invalid, expired or unsupported. Subscription-only access is not supported yet."
    }

    private fun failureMessage(reason: ServicesFailure) = when (reason) {
        ServicesFailure.APPROVAL_REQUIRED -> "This project's own client ID and Minecraft API approval are required."
        ServicesFailure.CANCELLED -> "Minecraft sign-in was cancelled."
        ServicesFailure.NETWORK -> "Minecraft account services could not be reached. Check the connection and retry."
        ServicesFailure.EXPIRED_CREDENTIAL -> "Account credentials expired or were rejected. Sign in again."
        ServicesFailure.ACCESS_DENIED -> "Service access was denied. Check this app's approval, permissions and account restrictions."
        ServicesFailure.ACCOUNT_RESTRICTED -> "Xbox account setup, age, region or family settings require attention."
        ServicesFailure.PROFILE_REQUIRED -> "No usable Java profile was returned. Check the account on the official Minecraft website."
        ServicesFailure.RATE_LIMITED -> "Account services asked us to wait. Retry later."
        ServicesFailure.SERVICE_UNAVAILABLE -> "Minecraft/Xbox services are temporarily unavailable."
        ServicesFailure.CONTRACT_CHANGED -> "Account service response was unexpected. Ownership remains unverified."
    }

    @Synchronized fun logout() {
        epoch.invalidate()
        installGrant = null
        activeSession = UUID.randomUUID()
        authState = AuthState()
        val disabled = flags.edit().putBoolean("disabled", true).commit()
        val removedAccount = runCatching { credentials.clear() }.isSuccess
        val removedPending = runCatching { pending.clear() }.isSuccess
        val removedMinecraft = runCatching { minecraftCredentials.clear() }.isSuccess
        val cleared = disabled && removedAccount && removedPending && removedMinecraft
        flags.edit().putBoolean("cleanup-failed", !cleared).commit()
        mutable.value = signedOutStatus(message = if (cleared) "Local session removed. Your browser may still be signed in to Microsoft."
            else "Session disabled. Credential cleanup failed; clear this app's storage before signing in again.")
    }

    companion object { val REDIRECT: Uri = Uri.parse("dev.andes.androidjavaport://oauth2redirect") }
}
