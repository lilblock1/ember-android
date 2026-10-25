package dev.andes.core

object AuthorizationBoundary {
    /** Exact-match binding, bounded lifetime, fail closed on clock rollback. */
    fun accepts(expectedState: String, responseState: String?, expectedClient: String,
        responseClient: String, expectedRedirect: String, responseRedirect: String,
        createdAt: Long, now: Long, code: String?): Boolean =
        expectedState.isNotBlank() && expectedState == responseState &&
        expectedClient.isNotBlank() && expectedClient == responseClient &&
        expectedRedirect == responseRedirect && createdAt <= now &&
        now - createdAt in 0..600_000 && !code.isNullOrBlank()
}
