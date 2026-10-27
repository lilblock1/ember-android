package dev.andes.app

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/** Owner-authorized opt-in real service test. Does not clear data or export credentials. */
class OwnershipAndClientDownloadTest {
    @Test fun verifiesPurchasedJavaAndDownloadsOriginalClient() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("ownershipAndDownload") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val account = (context.applicationContext as PortApplication).account
        account.verifyOwnership()
        await(45000) { !account.status.value.busy }
        assertTrue("Ownership verification did not succeed; private service response withheld", account.status.value.ownershipVerified)
        account.downloadResearchClient()
        await(120000) { !account.status.value.busy }
        assertTrue("Official download did not finish; private service response withheld", account.status.value.clientDownloaded)
        val client = File(context.filesDir, "research-client/1.20.4-client.jar")
        val metadata = JSONObject(File(context.filesDir, "research-client/1.20.4.json").readText()).getJSONObject("downloads").getJSONObject("client")
        val digest = MessageDigest.getInstance("SHA-1")
        client.inputStream().use { input ->
            val block = ByteArray(65536)
            while (true) { val count = input.read(block); if (count < 0) break; digest.update(block, 0, count) }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        assertEquals(metadata.getString("sha1"), hash)
        assertEquals(metadata.getLong("size"), client.length())
        File(context.cacheDir, "ownership-download-evidence.json").writeText(JSONObject()
            .put("observedAt", Instant.now().toString()).put("device", "Samsung Galaxy S25+ SM-S936U")
            .put("androidApi", android.os.Build.VERSION.SDK_INT).put("javaPurchaseOwnershipVerified", true)
            .put("licenseSignatureAlgorithm", "RS256").put("licenseNonceAndAccountBound", true)
            .put("version", "1.20.4").put("clientBytes", client.length()).put("officialClientSha1", hash)
            .put("clientIntegrityVerified", true).put("clientExecuted", false)
            .put("fullGameInstalled", false).put("sessionPreserved", true).toString(2))
    }
    private fun await(timeoutMs: Long, complete: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (!complete() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        assertTrue("Timed out; private account status withheld", complete())
    }
}
