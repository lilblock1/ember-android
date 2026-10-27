package dev.andes.app

import androidx.test.platform.app.InstrumentationRegistry
import dev.andes.authentication.TokenVault
import dev.andes.core.MinecraftEntitlementSigningKey
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID

/** Opt-in live diagnostic; credentials and raw service bodies never leave the target process. */
class OwnershipSchemaProbeTest {
    private var authenticationKeys: List<PublicKey> = emptyList()
    private var allOfficialKeys: List<Pair<String, PublicKey>> = emptyList()
    private var minecraftTokenClaims = JSONObject()
    @Test fun inspectAuthenticatedOwnershipResponseStructure() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("ownershipProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            val session = JSONObject(requireNotNull(TokenVault(context, "minecraft-session").read()))
            check(session.getString("clientId") == "6a1a3aa5-6dc6-48de-836d-af462ed06b2f")
            check(Instant.parse(session.getString("expiresAt")).isAfter(Instant.now()))
            val token = session.getString("accessToken")
            minecraftTokenClaims = runCatching { JSONObject(String(Base64.getUrlDecoder().decode(token.split('.')[1]), Charsets.UTF_8)) }
                .getOrDefault(JSONObject())
            val publicKeys = requireNotNull(get("/publickeys", null).second)
            allOfficialKeys = listOf("authenticationKeys", "profilePropertyKeys", "playerCertificateKeys").flatMap { kind ->
                val values = publicKeys.getJSONArray(kind)
                check(values.length() in 1..8)
                (0 until values.length()).map { index -> kind to KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(
                    Base64.getDecoder().decode(values.getJSONObject(index).getString("publicKey")))) }
            }
            authenticationKeys = allOfficialKeys.filter { it.first == "authenticationKeys" }.map { it.second }
            allOfficialKeys = allOfficialKeys + ("publisherLauncherEntitlementKey" to MinecraftEntitlementSigningKey.load())
            val report = JSONObject().put("gameFilesDownloaded", false)
                .put("minecraftTokenClaimFields", keys(minecraftTokenClaims))
            report.put("mcstore", summarize(get("/entitlements/mcstore", token), null))
            val requestId = UUID.randomUUID().toString()
            report.put("license", summarize(get("/entitlements/license?requestId=$requestId", token), requestId))
            File(context.cacheDir, "ownership-schema.json").writeText(report.toString(2))
        } catch (failure: Exception) {
            // Do not propagate JSON/parser/network messages which could contain response data.
            throw AssertionError("Ownership schema probe failed: ${failure.javaClass.simpleName}; private details withheld")
        }
    }

    private fun get(path: String, token: String?): Pair<Int, JSONObject?> {
        val connection = URL("https://api.minecraftservices.com$path").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.useCaches = false
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) return status to null
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val block = ByteArray(8192)
                while (true) {
                    val count = input.read(block)
                    if (count < 0) break
                    check(output.size() + count <= 1024 * 1024)
                    output.write(block, 0, count)
                }
            }
            val bytes = output.toByteArray()
            return status to if (bytes.isEmpty()) JSONObject() else JSONObject(String(bytes, Charsets.UTF_8))
        } finally { connection.disconnect() }
    }

    private fun keys(value: JSONObject): JSONArray = JSONArray(value.keys().asSequence()
        .filter { it.matches(Regex("[A-Za-z0-9_.:-]{1,64}")) }.sorted().toList())

    private fun summarize(response: Pair<Int, JSONObject?>, requestId: String?): JSONObject {
        val result = JSONObject().put("httpStatus", response.first)
        val body = response.second ?: return result
        result.put("fields", keys(body))
        val items = body.optJSONArray("items")
        result.put("itemCount", items?.length() ?: JSONObject.NULL)
        val knownNames = setOf("product_minecraft", "game_minecraft", "product_minecraft_bedrock", "game_minecraft_bedrock")
        val labels = JSONArray()
        if (items != null) for (index in 0 until minOf(items.length(), 64)) {
            val item = items.optJSONObject(index) ?: continue
            val name = item.optString("name")
            labels.put(JSONObject().put("name", if (name in knownNames) name else "[other entitlement]")
                .put("fields", keys(item)).put("sourceType", when(item.opt("source")) {
                    is String -> "string"; is JSONObject -> "object"; else -> "other"
                }).put("sourceLabel", item.optString("source").takeIf { it.matches(Regex("[A-Za-z_]{1,32}")) } ?: "[not a simple label]")
                .put("signature", jwsShape(item.optString("signature"), requestId)))
        }
        result.put("items", labels)
        result.put("signature", jwsShape(body.optString("signature"), requestId))
        if (requestId != null) result.put("outerRequestNonceMatches", body.optString("requestId") == requestId)
        result.put("errorCount", body.optJSONArray("errors")?.length() ?: JSONObject.NULL)
        val keyId = body.optString("keyId", body.optString("keyID", ""))
        if (keyId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) result.put("publicKeyId", keyId)
        return result
    }

    private fun jwsShape(value: String, requestId: String?): JSONObject {
        val result = JSONObject().put("present", value.isNotEmpty())
        val parts = value.split('.')
        if (parts.size != 3 || parts.any { it.length > 128 * 1024 }) return result.put("compactJws", false)
        return try {
            val header = JSONObject(String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8))
            val payload = JSONObject(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8))
            result.put("compactJws", true).put("headerFields", keys(header)).put("claimFields", keys(payload))
            val alg = header.optString("alg")
            if (alg in setOf("RS256", "RS512", "ES256", "none")) result.put("algorithm", alg)
            for (field in listOf("kid", "x5t")) header.optString(field).takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) }
                ?.let { result.put("public_$field", it) }
            if (alg == "RS256") {
                val matching = authenticationKeys.firstOrNull { key -> runCatching {
                    Signature.getInstance("SHA256withRSA").run {
                        initVerify(key); update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
                        verify(Base64.getUrlDecoder().decode(parts[2]))
                    }
                }.getOrDefault(false) }
                result.put("verifiedByOfficialAuthenticationKey", matching != null)
                if (matching != null) result.put("verifiedKeySha256", MessageDigest.getInstance("SHA-256")
                    .digest(matching.encoded).joinToString("") { "%02x".format(it) })
                result.put("otherOfficialKeyMatches", JSONArray(allOfficialKeys.filter { (_, key) -> runCatching {
                    Signature.getInstance("SHA256withRSA").run {
                        initVerify(key); update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
                        verify(Base64.getUrlDecoder().decode(parts[2]))
                    }
                }.getOrDefault(false) }.map { (kind, key) -> JSONObject().put("kind", kind)
                    .put("sha256", MessageDigest.getInstance("SHA-256").digest(key.encoded).joinToString("") { "%02x".format(it) }) }))
            }
            if (requestId != null) result.put("requestNonceMatches", payload.optString("requestId", payload.optString("entitlementsRequestId")) == requestId)
            val signer = payload.optString("signerId")
            result.put("tokenSubjectMatchFields", JSONArray(listOf("xuid", "sub", "username", "uuid").filter {
                signer.isNotEmpty() && minecraftTokenClaims.optString(it) == signer
            }))
            for (field in listOf("iat", "nbf", "exp")) if (payload.opt(field) is Number)
                result.put("has_$field", true)
            val products = payload.optJSONArray("entitlements") ?: payload.optJSONArray("items")
            if (products != null) result.put("signedItemsType", if (products.length() == 0) "empty" else
                when (products.opt(0)) { is JSONObject -> "objects"; is String -> "strings"; else -> "other" })
            if (products != null) result.put("signedItemShapes", JSONArray((0 until products.length()).map {
                val item = products.optJSONObject(it) ?: JSONObject()
                JSONObject().put("fields", keys(item)).put("source", item.optString("source").takeIf { value -> value.matches(Regex("[A-Z_]{1,32}")) } ?: "absent")
            }))
            for (field in listOf("iat", "nbf", "exp")) if (payload.opt(field) is Number)
                result.put("${field}SecondsFromNow", payload.getLong(field) - Instant.now().epochSecond)
            if (products != null) result.put("signedJavaGamePresent", (0 until products.length()).any {
                products.optJSONObject(it)?.optString("name") == "game_minecraft" || products.optString(it) == "game_minecraft"
            }).put("signedJavaProductPresent", (0 until products.length()).any {
                products.optJSONObject(it)?.optString("name") == "product_minecraft" || products.optString(it) == "product_minecraft"
            })
            result
        } catch (_: Exception) { result.put("compactJws", false) }
    }
}
