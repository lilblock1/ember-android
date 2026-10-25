package dev.andes.authentication

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant

class MinecraftServicesClientTest {
    private val approval = ServicesApproval("12345678-1234-1234-1234-123456789abc", "fixture registration", "fixture approval",
        listOf("XboxLive.signin", "offline_access"))
    private class Fake : ServicesTransport {
        val requests = mutableListOf<Triple<ServiceEndpoint,String?,String?>>()
        var intercept: ((ServiceEndpoint) -> ServiceResponse?)? = null
        override fun request(endpoint: ServiceEndpoint, authorization: String?, body: String?): ServiceResponse {
            requests += Triple(endpoint,authorization,body)
            intercept?.invoke(endpoint)?.let { return it }
            return ServiceResponse(200, when(endpoint) {
                ServiceEndpoint.XBOX -> """{"Token":"fake-xbox"}"""
                ServiceEndpoint.XSTS -> """{"Token":"fake-xsts","DisplayClaims":{"xui":[{"uhs":"12345"}]}}"""
                ServiceEndpoint.MINECRAFT -> """{"access_token":"fake-minecraft","expires_in":7200}"""
                ServiceEndpoint.ENTITLEMENTS -> """{"items":[{"name":"fixture_java_entitlement"}]}"""
                ServiceEndpoint.PROFILE -> """{"id":"12345678123412341234123456789abc","name":"FixturePlayer"}"""
            })
        }
    }
    private fun reason(result: ServicesResult) = (result as ServicesResult.Failed).reason

    @Test fun noApprovalPerformsZeroNetworkRequests() {
        val fake = Fake()
        val missing = ServicesApproval(approval.clientId, "registered", "", approval.scopes)
        assertEquals(ServicesFailure.APPROVAL_REQUIRED, reason(MinecraftServicesClient(fake).authenticate("fake-ms", missing)))
        assertTrue(fake.requests.isEmpty())
    }
    @Test fun exactServiceChainUsesV2RpsPrefixAndNeverEquatesProfileWithOwnership() {
        val fake = Fake()
        val now = Instant.parse("2026-10-24T00:00:00Z")
        val stages = mutableListOf<ServicesStage>()
        val result = MinecraftServicesClient(fake) { now }.authenticate("fake-ms", approval, progress = stages::add) as ServicesResult.Connected
        assertEquals(ServiceEndpoint.entries, fake.requests.map { it.first })
        assertEquals(ServicesStage.entries, stages)
        val xbox = JSONObject(fake.requests[0].third!!)
        assertEquals("d=fake-ms", xbox.getJSONObject("Properties").getString("RpsTicket"))
        val xsts = JSONObject(fake.requests[1].third!!)
        assertEquals("rp://api.minecraftservices.com/", xsts.getString("RelyingParty"))
        assertEquals("RETAIL", xsts.getJSONObject("Properties").getString("SandboxId"))
        assertEquals("XBL3.0 x=12345;fake-xsts", JSONObject(fake.requests[2].third!!).getString("identityToken"))
        assertEquals("fake-minecraft", fake.requests[3].second)
        assertEquals("fake-minecraft", fake.requests[4].second)
        assertEquals(now.plusSeconds(7200), result.session.expiresAt)
        assertFalse(JSONObject(result.session.encryptedPayload(approval.clientId)).getBoolean("ownershipVerified"))
        assertFalse(result.session.toString().contains("fake-minecraft"))
    }
    @Test fun profileDoesNotSkipEntitlementRetrieval() {
        val fake = Fake()
        fake.intercept = { if (it == ServiceEndpoint.ENTITLEMENTS) ServiceResponse(200, """{"items":[]}""") else null }
        val result = MinecraftServicesClient(fake).authenticate("fake-ms", approval) as ServicesResult.Connected
        assertEquals(0, result.session.observedEntitlementCount)
        assertFalse(JSONObject(result.session.encryptedPayload(approval.clientId)).getBoolean("ownershipVerified"))
    }
    @Test fun unknownEntitlementSchemaDoesNotMeanMissingOwnership() {
        val fake = Fake()
        fake.intercept = { if(it == ServiceEndpoint.ENTITLEMENTS) ServiceResponse(200,"""{"newSchema":true}""") else null }
        assertEquals(ServicesFailure.CONTRACT_CHANGED, reason(MinecraftServicesClient(fake).authenticate("fake-ms",approval)))
        assertFalse(fake.requests.any { it.first == ServiceEndpoint.PROFILE })
    }
    @Test fun noProfileNeverCreatesDemoIdentity() {
        val fake = Fake()
        fake.intercept = { if(it == ServiceEndpoint.PROFILE) ServiceResponse(404,"{}") else null }
        assertEquals(ServicesFailure.PROFILE_REQUIRED, reason(MinecraftServicesClient(fake).authenticate("fake-ms",approval)))
    }
    @Test fun restrictionsAndApiDenialRemainDistinct() {
        val fake = Fake()
        fake.intercept = { if(it == ServiceEndpoint.XSTS) ServiceResponse(401,"""{"XErr":2148916238}""") else null }
        assertEquals(ServicesFailure.ACCOUNT_RESTRICTED, reason(MinecraftServicesClient(fake).authenticate("fake-ms",approval)))
        fake.intercept = { if(it == ServiceEndpoint.MINECRAFT) ServiceResponse(403,"{}") else null }
        assertEquals(ServicesFailure.ACCESS_DENIED, reason(MinecraftServicesClient(fake).authenticate("fake-ms",approval)))
    }
    @Test fun cancellationAfterExchangePreventsLaterServiceCalls() {
        val fake = Fake()
        var active = true
        fake.intercept = { active = false; null }
        assertEquals(ServicesFailure.CANCELLED, reason(MinecraftServicesClient(fake).authenticate("fake-ms",approval,{active})))
        assertEquals(1, fake.requests.size)
    }
    @Test fun networkAndThrottleErrorsNeverBecomeMissingOwnership() {
        val disconnected = ServicesTransport { _,_,_ -> throw IOException("fixture disconnect") }
        assertEquals(ServicesFailure.NETWORK, reason(MinecraftServicesClient(disconnected).authenticate("fake-ms",approval)))
        val throttled = ServicesTransport { _,_,_ -> ServiceResponse(429,"{}") }
        assertEquals(ServicesFailure.RATE_LIMITED, reason(MinecraftServicesClient(throttled).authenticate("fake-ms",approval)))
    }
    @Test fun malformedProfileAndLifetimeFailClosed() {
        val fake = Fake()
        fake.intercept = { if(it == ServiceEndpoint.PROFILE) ServiceResponse(200,"""{"id":"bad","name":"FixturePlayer"}""") else null }
        assertEquals(ServicesFailure.CONTRACT_CHANGED, reason(MinecraftServicesClient(fake).authenticate("fake-ms",approval)))
        fake.intercept = { if(it == ServiceEndpoint.MINECRAFT) ServiceResponse(200,"""{"access_token":"fake-mc","expires_in":-1}""") else null }
        assertEquals(ServicesFailure.CONTRACT_CHANGED, reason(MinecraftServicesClient(fake).authenticate("fake-ms",approval)))
    }
}
