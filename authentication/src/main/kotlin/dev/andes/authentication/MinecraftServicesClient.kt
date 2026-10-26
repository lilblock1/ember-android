package dev.andes.authentication

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

internal enum class ServiceEndpoint(val address: String, val method: String) {
    XBOX("https://user.auth.xboxlive.com/user/authenticate", "POST"),
    XSTS("https://xsts.auth.xboxlive.com/xsts/authorize", "POST"),
    MINECRAFT("https://api.minecraftservices.com/authentication/login_with_xbox", "POST"),
    ENTITLEMENTS("https://api.minecraftservices.com/entitlements/mcstore", "GET"),
    PROFILE("https://api.minecraftservices.com/minecraft/profile", "GET")
}
internal class ServiceResponse(val status: Int, val body: String) {
    override fun toString() = "ServiceResponse(status=$status, body=[REDACTED])"
}
internal fun interface ServicesTransport {
    fun request(endpoint: ServiceEndpoint, authorization: String?, body: String?): ServiceResponse
}

/** Closed endpoint set, system TLS trust, no redirects/logging, bounded reads/timeouts. */
internal class HttpsServicesTransport : ServicesTransport {
    override fun request(endpoint: ServiceEndpoint, authorization: String?, body: String?): ServiceResponse {
        val connection = URL(endpoint.address).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = endpoint.method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("x-xbl-contract-version", "1")
            authorization?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            body?.let {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { stream -> stream.write(it.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (code in 300..399) throw IOException("Authentication redirects are not permitted")
            if (connection.contentLengthLong > MAX_BODY) throw IOException("Authentication response exceeds limit")
            val input = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = ByteArrayOutputStream()
            input?.use { stream ->
                val chunk = ByteArray(8192)
                while (true) {
                    val count = stream.read(chunk)
                    if (count < 0) break
                    if (bytes.size() + count > MAX_BODY) throw IOException("Authentication response exceeds limit")
                    bytes.write(chunk, 0, count)
                }
            }
            return ServiceResponse(code, bytes.toString(Charsets.UTF_8.name()))
        } finally { connection.disconnect() }
    }
    companion object { private const val MAX_BODY = 1024 * 1024 }
}

internal class ServicesApproval(val clientId: String, private val registrationReference: String,
    private val approvalReference: String, val scopes: List<String>) {
    fun acceptsGrantedScopes(granted: String): Boolean = enabled &&
        granted.split(Regex("\\s+")).any { it.equals("XboxLive.signin", true) }
    val enabled: Boolean get() = clientId.matches(Regex("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")) &&
        registrationReference.isNotBlank() && approvalReference.isNotBlank() &&
        scopes.any { it.equals("XboxLive.signin", true) } && scopes.all { scope ->
            listOf("XboxLive.signin", "offline_access", "XboxLive.offline_access").any { it.equals(scope, true) }
        }
}
internal enum class ServicesStage { XBOX, XSTS, MINECRAFT, ENTITLEMENTS, PROFILE }
internal enum class ServicesFailure { APPROVAL_REQUIRED, CANCELLED, NETWORK, EXPIRED_CREDENTIAL,
    ACCESS_DENIED, ACCOUNT_RESTRICTED, PROFILE_REQUIRED, RATE_LIMITED, SERVICE_UNAVAILABLE, CONTRACT_CHANGED }

/** Token-bearing objects intentionally have no generated data-class/string representation. */
internal class MinecraftSession(val profileId: UUID, val profileName: String, private val accessToken: String,
    val expiresAt: Instant, val observedEntitlementCount: Int?) {
    fun encryptedPayload(clientId: String): String = JSONObject().put("clientId", clientId)
        .put("profileId", profileId.toString()).put("profileName", profileName)
        .put("accessToken", accessToken).put("expiresAt", expiresAt.toString())
        .put("ownershipVerified", false).toString()
    override fun toString() = "MinecraftSession([REDACTED], ownershipVerified=false)"
}
internal sealed interface ServicesResult {
    class Connected(val session: MinecraftSession) : ServicesResult
    class Failed(val reason: ServicesFailure) : ServicesResult
}
internal class StageFailure(val reason: ServicesFailure) : RuntimeException("Authentication stage failed: $reason")

/** Original implementation. Approval is an owner record, not a substitute for server authorization. */
internal class MinecraftServicesClient(private val transport: ServicesTransport = HttpsServicesTransport(),
    private val now: () -> Instant = { Instant.now() }) {
    fun authenticate(microsoftToken: String, approval: ServicesApproval, active: () -> Boolean = { true },
        progress: (ServicesStage) -> Unit = {}): ServicesResult {
        if (!approval.enabled) return ServicesResult.Failed(ServicesFailure.APPROVAL_REQUIRED)
        if (microsoftToken.isBlank()) return ServicesResult.Failed(ServicesFailure.EXPIRED_CREDENTIAL)
        fun call(stage: ServicesStage, endpoint: ServiceEndpoint, token: String? = null, body: JSONObject? = null): JSONObject {
            if (!active()) throw StageFailure(ServicesFailure.CANCELLED)
            progress(stage)
            val response = transport.request(endpoint, token, body?.toString())
            if (!active()) throw StageFailure(ServicesFailure.CANCELLED)
            if (response.status !in 200..299) {
                val xerr = runCatching { JSONObject(response.body).optLong("XErr", -1) }.getOrDefault(-1)
                val reason = when {
                    endpoint == ServiceEndpoint.XSTS && xerr in 2148916233L..2148916238L -> ServicesFailure.ACCOUNT_RESTRICTED
                    endpoint == ServiceEndpoint.PROFILE && response.status == 404 -> ServicesFailure.PROFILE_REQUIRED
                    response.status == 401 -> ServicesFailure.EXPIRED_CREDENTIAL
                    response.status == 403 -> ServicesFailure.ACCESS_DENIED
                    response.status == 429 -> ServicesFailure.RATE_LIMITED
                    response.status in 500..599 -> ServicesFailure.SERVICE_UNAVAILABLE
                    else -> ServicesFailure.CONTRACT_CHANGED
                }
                throw StageFailure(reason)
            }
            return JSONObject(response.body)
        }
        fun requiredToken(data: JSONObject, field: String): String = data.getString(field).also {
            if (it.isBlank() || it.length > 64 * 1024) throw JSONException("Invalid token field")
        }
        try {
            val xbox = call(ServicesStage.XBOX, ServiceEndpoint.XBOX, body = JSONObject()
                .put("Properties", JSONObject().put("AuthMethod", "RPS").put("SiteName", "user.auth.xboxlive.com")
                    .put("RpsTicket", "d=$microsoftToken"))
                .put("RelyingParty", "http://auth.xboxlive.com").put("TokenType", "JWT"))
            val xsts = call(ServicesStage.XSTS, ServiceEndpoint.XSTS, body = JSONObject()
                .put("Properties", JSONObject().put("SandboxId", "RETAIL")
                    .put("UserTokens", JSONArray().put(requiredToken(xbox, "Token"))))
                .put("RelyingParty", "rp://api.minecraftservices.com/").put("TokenType", "JWT"))
            val userHash = xsts.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs")
            if (!userHash.matches(Regex("[0-9]{1,32}"))) throw JSONException("Invalid user hash")
            val minecraft = call(ServicesStage.MINECRAFT, ServiceEndpoint.MINECRAFT, body = JSONObject()
                .put("identityToken", "XBL3.0 x=$userHash;${requiredToken(xsts, "Token")}"))
            val minecraftToken = requiredToken(minecraft, "access_token")
            val lifetime = minecraft.getLong("expires_in")
            if (lifetime !in 1..604800) throw JSONException("Invalid token lifetime")
            val expiry = now().plusSeconds(lifetime)
            val evidence = call(ServicesStage.ENTITLEMENTS, ServiceEndpoint.ENTITLEMENTS, minecraftToken)
            // Observe transport-authenticated data; do NOT decode an unverified JWT as license proof.
            val items = evidence.optJSONArray("items")
            val itemCount = items?.length()
            if (items == null && evidence.optString("signature", "").isBlank()) throw JSONException("Unknown entitlement envelope")
            if (items != null) for (i in 0 until items.length()) {
                if (items.getJSONObject(i).getString("name").isBlank()) throw JSONException("Invalid entitlement item")
            }
            val profile = call(ServicesStage.PROFILE, ServiceEndpoint.PROFILE, minecraftToken)
            val rawId = profile.getString("id")
            if (!rawId.matches(Regex("[a-fA-F0-9]{32}"))) throw JSONException("Invalid Minecraft profile id")
            val profileId = UUID.fromString(rawId.replaceFirst(Regex("(.{8})(.{4})(.{4})(.{4})(.{12})"), "$1-$2-$3-$4-$5"))
            val name = profile.getString("name")
            if (!name.matches(Regex("[A-Za-z0-9_]{3,16}"))) throw JSONException("Invalid Minecraft profile name")
            if (!active()) return ServicesResult.Failed(ServicesFailure.CANCELLED)
            return ServicesResult.Connected(MinecraftSession(profileId, name, minecraftToken, expiry, itemCount))
        } catch (failure: StageFailure) { return ServicesResult.Failed(failure.reason) }
        catch (_: IOException) { return ServicesResult.Failed(ServicesFailure.NETWORK) }
        catch (_: JSONException) { return ServicesResult.Failed(ServicesFailure.CONTRACT_CHANGED) }
        catch (_: IllegalArgumentException) { return ServicesResult.Failed(ServicesFailure.CONTRACT_CHANGED) }
    }
}
