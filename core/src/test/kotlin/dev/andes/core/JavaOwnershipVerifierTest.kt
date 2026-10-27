package dev.andes.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.nio.file.Files

class JavaOwnershipVerifierTest {
    private val now = Instant.parse("2026-10-26T20:00:00Z")
    private val request = UUID.randomUUID()
    private val profile = UUID.randomUUID()
    private val session = UUID.randomUUID()
    private val verifier = JavaOwnershipVerifier(pair.public)
    private fun items(source: String = "PURCHASE") = JSONArray().put(JSONObject().put("name", "game_minecraft").put("source", source))
        .put(JSONObject().put("name", "product_minecraft").put("source", source))
    private fun claims() = JSONObject().put("requestId", request.toString()).put("signerId", "123456789")
        .put("iat", now.epochSecond).put("nbf", now.minusSeconds(180).epochSecond)
        .put("exp", now.plusSeconds(172800).epochSecond).put("entitlements", items())
    private fun envelope(claims: JSONObject = claims(), mutateHeader: (JSONObject) -> Unit = {}): JSONObject {
        val header = JSONObject().put("alg", "RS256").put("kid", "1").put("typ", "JWT").put("x5t", "IUtWwYtrS_IzIKJbi6s4kVh_E5s")
        mutateHeader(header)
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val unsigned = encoder.encodeToString(header.toString().toByteArray()) + "." + encoder.encodeToString(claims.toString().toByteArray())
        val signature = Signature.getInstance("SHA256withRSA").run { initSign(pair.private); update(unsigned.toByteArray()); sign() }
        return JSONObject().put("keyId", "1").put("requestId", request.toString()).put("errors", JSONArray())
            .put("items", claims.getJSONArray("entitlements")).put("signature", "$unsigned.${encoder.encodeToString(signature)}")
    }
    private fun verify(body: JSONObject = envelope(), expectedUser: String = "123456789", expectedProfile: UUID = profile,
        expiry: Instant = now.plusSeconds(3600)) = verifier.verify(body.toString(), request, expectedUser,
        JSONObject().put("id", profile.toString().replace("-", "")).put("name", "TestPlayer").toString(), expectedProfile, session, expiry, now)
    private fun rejected(body: JSONObject) { assertThrows(IllegalStateException::class.java) { verify(body) } }

    @Test fun grantsOnlyTenMinutesForMatchingPurchasedJavaAndProfile() {
        val grant = verify()
        assertEquals(now.plusSeconds(600), grant.validUntil)
        InstallAccessPolicy().requireAccess(grant, session, now)
        assertThrows(IllegalStateException::class.java) { InstallAccessPolicy().requireAccess(grant, UUID.randomUUID(), now) }
    }
    @Test fun tokenExpiryBoundsGrant() { assertEquals(now.plusSeconds(10), verify(expiry = now.plusSeconds(10)).validUntil) }
    @Test fun rejectsWrongSignature() {
        val body = envelope(); val parts = body.getString("signature").split('.').toMutableList()
        val bytes = Base64.getUrlDecoder().decode(parts[2]); bytes[0] = (bytes[0].toInt() xor 1).toByte()
        parts[2] = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        rejected(body.put("signature", parts.joinToString(".")))
    }
    @Test fun rejectsUnrecognizedKeysAndAlgorithmDowngrade() {
        rejected(envelope(mutateHeader = { it.put("alg", "none") }))
        rejected(envelope(mutateHeader = { it.put("kid", "2") }))
        rejected(envelope(mutateHeader = { it.put("crit", JSONArray().put("custom")) }))
    }
    @Test fun rejectsReplayedAndSubstitutedNonce() {
        rejected(envelope(claims().put("requestId", UUID.randomUUID().toString())))
        rejected(envelope().put("requestId", UUID.randomUUID().toString()))
    }
    @Test fun rejectsDifferentAccountOrProfile() {
        assertThrows(IllegalStateException::class.java) { verify(expectedUser = "999") }
        assertThrows(IllegalStateException::class.java) { verify(expectedProfile = UUID.randomUUID()) }
    }
    @Test fun rejectsExpiredFutureOrStaleEvidence() {
        rejected(envelope(claims().put("exp", now.epochSecond)))
        rejected(envelope(claims().put("nbf", now.plusSeconds(1).epochSecond)))
        rejected(envelope(claims().put("iat", now.minusSeconds(121).epochSecond)))
        assertThrows(IllegalStateException::class.java) { verify(expiry = now) }
    }
    @Test fun rejectsUnsignedOuterTamperingAndPartialServiceFailure() {
        rejected(envelope().put("items", JSONArray()))
        rejected(envelope().put("errors", JSONArray().put("unavailable")))
    }
    @Test fun rejectsBedrockAndIncompleteJavaLicenses() {
        rejected(envelope(claims().put("entitlements", JSONArray().put(JSONObject().put("name", "game_minecraft_bedrock").put("source", "PURCHASE")))))
        rejected(envelope(claims().put("entitlements", JSONArray().put(items().getJSONObject(0)))))
    }
    @Test fun rejectsUnvalidatedSubscriptionsAndUnknownSchema() {
        rejected(envelope(claims().put("entitlements", items("SUBSCRIPTION"))))
        val values = items(); values.getJSONObject(0).put("newContract", true)
        rejected(envelope(claims().put("entitlements", values)))
    }
    @Test fun mcPurchaseIsSupportedAndErrorsAreSanitized() {
        verify(envelope(claims().put("entitlements", items("MC_PURCHASE"))))
        val failure = assertThrows(IllegalStateException::class.java) { verifier.verify("secret-invalid-body", request, "123", "secret-profile", profile, session, now.plusSeconds(1), now) }
        assertFalse(failure.toString().contains("secret")); assertNull(failure.cause)
    }
    @Test fun missingGrantPreventsAnyDownloadOrFilesystemChange() {
        val directory = Files.createTempDirectory("installer-policy").resolve("not-created").toFile()
        assertThrows(IllegalStateException::class.java) { OfficialClientInstaller(directory).downloadResearchClient({ null }, session, { true }) { _, _ -> } }
        assertFalse(directory.exists())
    }
    companion object { private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
}
