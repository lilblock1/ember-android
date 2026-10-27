package dev.andes.authentication

import dev.andes.core.InstallGrant
import dev.andes.core.JavaOwnershipVerifier
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Base64
import java.util.UUID

internal class OwnershipCheckFailure(val reason: String) : RuntimeException(reason)

/** Owns credentials only within the app process; never persists an install grant. */
internal class MinecraftOwnershipClient {
    fun verify(session: JSONObject, activeSession: UUID, approval: ServicesApproval): InstallGrant {
        if (!approval.enabled || session.getString("clientId") != approval.clientId)
            throw OwnershipCheckFailure("Minecraft API approval is required for this app registration.")
        val expiry = Instant.parse(session.getString("expiresAt"))
        if (expiry <= Instant.now()) throw OwnershipCheckFailure("Minecraft session expired. Check Minecraft connection to renew it.")
        val token = session.getString("accessToken")
        val parts = token.split('.')
        require(parts.size == 3)
        // The service authenticates this token on both HTTPS requests below. This decoded
        // claim only binds the signed license to that token; it is never ownership proof.
        val user = JSONObject(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)).getString("xuid")
        val request = UUID.randomUUID()
        val license = get("/entitlements/license?requestId=$request", token)
        val profile = get("/minecraft/profile", token)
        return JavaOwnershipVerifier().verify(license, request, user, profile,
            UUID.fromString(session.getString("profileId")), activeSession, expiry, Instant.now())
    }

    private fun get(path: String, token: String): String {
        val connection = URL("https://api.minecraftservices.com$path").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code != 200) throw OwnershipCheckFailure(when (code) {
                401 -> "Minecraft session expired or was rejected. Sign in again."
                403 -> "Minecraft access was denied. Check account restrictions and API approval."
                404 -> "Java ownership/profile is unavailable for this account. Check the official Minecraft website."
                429 -> "Minecraft Services asked us to wait. Retry later."
                in 500..599 -> "Minecraft Services is temporarily unavailable. Ownership was not checked."
                else -> "Minecraft Services returned an unsupported response. Ownership remains unverified."
            })
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val block = ByteArray(8192)
                while (true) {
                    val count = input.read(block)
                    if (count < 0) break
                    if (output.size() + count > 1024 * 1024) throw IOException("Response exceeds limit")
                    output.write(block, 0, count)
                }
            }
            return output.toString(Charsets.UTF_8.name())
        } finally { connection.disconnect() }
    }
}
