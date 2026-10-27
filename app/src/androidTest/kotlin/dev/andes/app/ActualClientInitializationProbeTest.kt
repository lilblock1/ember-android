package dev.andes.app

import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.InMemoryDexClassLoader
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.lang.reflect.InvocationTargetException
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Private, owned-client experiment. No game bytecode is packaged in any distributed APK.
 * This is a blocker probe, not a passing game execution test or a selected runtime strategy.
 */
class ActualClientInitializationProbeTest {
    @Test fun recordsActualClientInitializationBoundary() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("actualClientProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val account = (context.applicationContext as PortApplication).account
        account.verifyOwnership()
        val deadline = android.os.SystemClock.elapsedRealtime() + 45000
        while (account.status.value.busy && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        assertTrue("Fresh ownership is required for this client experiment", account.status.value.ownershipVerified)
        val directory = File(context.filesDir, "owned-client-probe")
        val files = requireNotNull(directory.listFiles()).filter { it.name.matches(Regex("classes[0-9]*\\.dex")) }.sortedBy { it.name }
        require(files.isNotEmpty() && files.sumOf { it.length() } < 128 * 1024 * 1024)
        val hashes = JSONArray(files.map { file -> JSONObject().put("file", file.name).put("bytes", file.length())
            .put("sha256", MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }) })
        val loader = InMemoryDexClassLoader(files.map { ByteBuffer.wrap(it.readBytes()) }.toTypedArray(), context.classLoader)
        var invoked = false
        var entryObserved = false
        var loaded = false
        var failureType = "none"
        var missingClass: String? = null
        try {
            val main = Class.forName("net.minecraft.client.main.Main", true, loader)
            loaded = true
            invoked = true
            // No credentials or demo/offline identity are passed. This only exercises argument
            // parsing/dependency linkage; it cannot authenticate or start a playable game.
            main.getMethod("main", Array<String>::class.java).invoke(null, emptyArray<String>() as Any)
            entryObserved = true
        } catch (reflection: InvocationTargetException) {
            val failure = reflection.targetException
            failureType = failure.javaClass.simpleName
            entryObserved = failure.stackTrace.any { it.className == "net.minecraft.client.main.Main" && it.methodName == "main" }
            val candidates = generateSequence<Throwable>(failure) { it.cause }.take(8).mapNotNull { it.message }.joinToString(" ")
            missingClass = Regex("(?:L|class \")((?:com[/.]mojang|org[/.]lwjgl|org[/.]apache|com[/.]google|joptsimple)[A-Za-z0-9_$/.-]{1,180})")
                .find(candidates)?.groupValues?.get(1)
        } catch (failure: LinkageError) {
            failureType = failure.javaClass.simpleName
            // Only an allowlisted class-path token is exported; never arbitrary exception messages.
            val candidates = generateSequence<Throwable>(failure) { it.cause }.take(8).mapNotNull { it.message }.joinToString(" ")
            missingClass = Regex("(?:L|class \")((?:com[/.]mojang|org[/.]lwjgl|org[/.]apache|com[/.]google|joptsimple)[A-Za-z0-9_$/.-]{1,180})")
                .find(candidates)?.groupValues?.get(1)
        }
        File(context.cacheDir, "actual-client-initialization.json").writeText(JSONObject()
            .put("version", "1.20.4").put("originalClientSha1", "fd19469fed4a4b4c15b2d5133985f0e3e7816a8a")
            .put("freshOwnershipVerified", true).put("translation", "D8 to DEX; ART runtime, not native AOT")
            .put("dexFiles", hashes).put("mainClassInitialized", loaded).put("failureType", failureType)
            .put("missingClass", missingClass ?: JSONObject.NULL).put("mainMethodInvoked", invoked).put("mainEntryObserved", entryObserved)
            .put("mainMenuReached", false).put("playable", false).toString(2))
    }
}
