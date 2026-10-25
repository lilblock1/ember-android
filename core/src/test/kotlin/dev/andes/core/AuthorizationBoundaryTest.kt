package dev.andes.core

import org.junit.Assert.*
import org.junit.Test

class AuthorizationBoundaryTest {
    private fun accepts(state: String? = "random-state", client: String = "own-client",
        redirect: String = "dev.andes.androidjavaport://oauth2redirect", time: Long = 1000,
        code: String? = "one-use-code") = AuthorizationBoundary.accepts("random-state", state,
        "own-client", client, "dev.andes.androidjavaport://oauth2redirect", redirect, 1000, time, code)
    @Test fun acceptsBoundedMatchingRequest() { assertTrue(accepts(time = 601000)) }
    @Test fun rejectsMissingStateAndInjectedResponses() {
        assertFalse(accepts(state = null))
        assertFalse(accepts(state = "attacker"))
        assertFalse(accepts(client = "borrowed-client"))
        assertFalse(accepts(redirect = "dev.andes.androidjavaport://oauth2redirect/evil"))
        assertFalse(accepts(code = ""))
    }
    @Test fun rejectsExpiredOrClockRollbackResponses() {
        assertFalse(accepts(time = 601001))
        assertFalse(accepts(time = 999))
    }
}
