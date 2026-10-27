package dev.andes.core

import org.json.JSONArray
import org.json.JSONObject
import java.security.PublicKey
import java.security.Signature
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** Validates the observed signed /entitlements/license contract. Unknown keys/sources fail closed.
 * Inputs must come from fresh authenticated HTTPS requests using the same Minecraft token.
 * Only purchased licenses are supported; no inference from a Microsoft login or profile alone.
 */
class JavaOwnershipVerifier internal constructor(private val key: PublicKey) {
    constructor() : this(MinecraftEntitlementSigningKey.load())

    fun verify(licenseBody: String, requestId: UUID, expectedXboxUser: String,
        profileBody: String, expectedProfile: UUID, sessionId: UUID,
        sessionExpiry: Instant, now: Instant): InstallGrant {
        try {
            require(licenseBody.length <= 1024 * 1024 && profileBody.length <= 1024 * 1024)
            require(expectedXboxUser.matches(Regex("[0-9]{1,32}")))
            val envelope = JSONObject(licenseBody)
            require(envelope.getJSONArray("errors").length() == 0)
            require(envelope.getString("requestId") == requestId.toString())
            require(envelope.getString("keyId") == "1")
            val parts = envelope.getString("signature").split('.')
            require(parts.size == 3 && parts.all { it.length in 1..262144 && it.matches(Regex("[A-Za-z0-9_-]+")) })
            val header = JSONObject(String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8))
            require(header.getString("alg") == "RS256" && header.getString("kid") == "1")
            require(header.getString("typ") == "JWT" && header.getString("x5t") == "IUtWwYtrS_IzIKJbi6s4kVh_E5s")
            require(!header.has("crit") && !header.has("jku") && !header.has("jwk") && !header.has("b64"))
            require(Signature.getInstance("SHA256withRSA").run {
                initVerify(key)
                update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
                verify(Base64.getUrlDecoder().decode(parts[2]))
            })
            // Interpret claims only after verifying the exact compact JWS bytes.
            val claims = JSONObject(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8))
            require(claims.getString("requestId") == requestId.toString())
            require(claims.getString("signerId") == expectedXboxUser)
            fun time(field: String): Long {
                val value = claims.get(field)
                require(value is Number && value.toString().matches(Regex("[0-9]{1,12}")))
                return value.toLong()
            }
            val issued = Instant.ofEpochSecond(time("iat"))
            val notBefore = Instant.ofEpochSecond(time("nbf"))
            val expires = Instant.ofEpochSecond(time("exp"))
            require(issued <= now.plusSeconds(30) && issued >= now.minusSeconds(120))
            require(notBefore <= now && expires > now && expires > issued && notBefore <= issued)
            require(expires <= issued.plusSeconds(604800) && sessionExpiry > now)
            val signed = entries(claims.getJSONArray("entitlements"))
            require(signed == entries(envelope.getJSONArray("items")))
            val purchased = setOf("PURCHASE", "MC_PURCHASE")
            require(signed.any { it.first == "game_minecraft" && it.second in purchased })
            require(signed.any { it.first == "product_minecraft" && it.second in purchased })
            val profile = JSONObject(profileBody)
            require(profile.getString("id").matches(Regex("[0-9a-fA-F]{32}")))
            require(profile.getString("id").equals(expectedProfile.toString().replace("-", ""), true))
            require(profile.getString("name").matches(Regex("[A-Za-z0-9_]{3,16}")))
            return InstallGrant(sessionId, expectedProfile, minOf(expires, sessionExpiry, now.plusSeconds(600)))
        } catch (_: Exception) {
            // Parser messages and signed account claims must never escape into logs/UI.
            throw IllegalStateException("Java ownership evidence was missing, invalid, expired, or unsupported.")
        }
    }

    private fun entries(values: JSONArray): List<Pair<String, String>> {
        require(values.length() <= 128)
        return (0 until values.length()).map {
            val item = values.getJSONObject(it)
            val fields = item.keys().asSequence().toSet()
            require(fields == setOf("name", "source"))
            item.getString("name") to item.getString("source")
        }.sortedWith(compareBy({ it.first }, { it.second }))
    }
}
