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
        val phase = InstrumentationRegistry.getArguments().getString("probeDirectory") ?: "original"
        require(phase in setOf("original", "java36"))
        val directory = File(context.filesDir, if (phase == "original") "owned-client-probe" else "owned-client-probe/$phase")
        val files = requireNotNull(directory.listFiles()).filter { it.name.matches(Regex("(?:classes[0-9]*|dependency-[a-f0-9-]+)\\.dex")) }.sortedBy { it.name }
        require(files.isNotEmpty() && files.sumOf { it.length() } < 128 * 1024 * 1024)
        val hashes = JSONArray(files.map { file -> JSONObject().put("file", file.name).put("bytes", file.length())
            .put("sha256", MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }) })
        if (phase == "java36") {
            val manifest = JSONObject(File(directory, "probe-manifest.json").readText())
            require(manifest.getString("version") == "1.20.4" && manifest.getBoolean("inputsIntegrityVerified"))
            val expected = manifest.getJSONArray("dex")
            require(expected.length() == hashes.length())
            for (index in 0 until hashes.length()) {
                val actual = hashes.getJSONObject(index)
                val match = (0 until expected.length()).map { expected.getJSONObject(it) }.single { it.getString("file") == actual.getString("file") }
                require(match.getLong("bytes") == actual.getLong("bytes") && match.getString("sha256") == actual.getString("sha256"))
            }
        }
        val original = File(context.filesDir, "research-client/1.20.4-client.jar")
        fun sha1(file: File): String {
            val digest = MessageDigest.getInstance("SHA-1")
            file.inputStream().use { input ->
                val block = ByteArray(65536)
                while (true) { val count = input.read(block); if (count < 0) break; digest.update(block, 0, count) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        require(sha1(original) == "fd19469fed4a4b4c15b2d5133985f0e3e7816a8a")
        val libraryDirectory = File(context.filesDir, "research-client/initialization-libraries")
        val records = JSONArray(File(libraryDirectory, "verified.json").readText())
        val jars = (0 until records.length()).map {
            val record = records.getJSONObject(it)
            val hash = record.getString("sha1")
            require(hash.matches(Regex("[a-f0-9]{40}")))
            File(libraryDirectory, "$hash.jar").also { file -> require(file.length() == record.getLong("size") && sha1(file) == hash) }
        }
        val parent = OwnedResourceClassLoader(context.classLoader, listOf(original) + jars)
        val loader = InMemoryDexClassLoader(files.map { ByteBuffer.wrap(it.readBytes()) }.toTypedArray(), parent)
        val originalContextLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = loader
        val authenticated = InstrumentationRegistry.getArguments().getString("authenticatedStartup") == "true"
        val protected = if (authenticated) ProtectedStartup.prepare(context) else null
        val console = protected?.protectConsole()
        // Force a console-only logger configuration for this private probe; no credential-bearing file logs.
        val previousLogging = System.getProperty("log4j.configurationFile")
        val previousJmx = System.getProperty("log4j2.disable.jmx")
        System.setProperty("log4j2.disable.jmx", "true") // Android has no platform JMX server.
        val logging = File(context.cacheDir, "startup-log4j2.xml")
        logging.writeText("<Configuration status=\"OFF\"><Appenders><Console name=\"safe\" target=\"SYSTEM_ERR\"><PatternLayout pattern=\"%level %msg%n\"/></Console></Appenders><Loggers><Root level=\"warn\"><AppenderRef ref=\"safe\"/></Root></Loggers></Configuration>")
        System.setProperty("log4j.configurationFile", logging.toURI().toString())
        var invoked = false
        var entryObserved = false
        var loaded = false
        var failureType = "none"
        var missingClass: String? = null
        var failureFrames = JSONArray()
        var failureCauses = JSONArray()
        File(context.cacheDir, "startup-probe-pending.json").writeText(JSONObject()
            .put("probeRunId", InstrumentationRegistry.getArguments().getString("probeRunId") ?: "manual")
            .put("startedAt", System.currentTimeMillis()).put("authenticatedStartup", authenticated).toString())
        try {
            val main = Class.forName("net.minecraft.client.main.Main", true, loader)
            loaded = true
            invoked = true
            // Default is a no-argument linkage test. The explicit authenticated variant gets
            // real credentials only from the protected phone vault, with console redaction.
            main.getMethod("main", Array<String>::class.java).invoke(null, (protected?.arguments ?: emptyArray<String>()) as Any)
            entryObserved = true
        } catch (reflection: InvocationTargetException) {
            val failure = reflection.targetException
            if (!authenticated && failure is VerifyError) File(context.cacheDir, "client-verifier-private.txt").writeText(failure.message.orEmpty())
            if (!authenticated) File(context.cacheDir, "startup-private.txt").writeText(generateSequence<Throwable>(failure) { it.cause }.take(8).joinToString("\n") { it.javaClass.name + ": " + it.message.orEmpty() })
            failureType = failure.javaClass.simpleName
            failureCauses = JSONArray(generateSequence<Throwable>(failure) { it.cause }.take(8).map { cause ->
                JSONObject().put("type", cause.javaClass.simpleName).put("frames", JSONArray(cause.stackTrace.take(8).map { it.className + "." + it.methodName }))
            }.toList())
            failureFrames = JSONArray(generateSequence<Throwable>(failure) { it.cause }.take(8).flatMap { it.stackTrace.asSequence() }.take(24).map {
                it.className + "." + it.methodName
            }.filter { it.matches(Regex("[A-Za-z0-9_.$<>]+")) }.toList())
            entryObserved = failure.stackTrace.any { it.className == "net.minecraft.client.main.Main" && it.methodName == "main" }
            val candidates = generateSequence<Throwable>(failure) { it.cause }.take(8).mapNotNull { it.message }.joinToString(" ")
            missingClass = Regex("(?:L|class \")((?:com[/.]mojang|org[/.]lwjgl|org[/.]apache|com[/.]google|joptsimple|java[/.]lang|javax[/.]management|sun[/.]misc|com[/.]sun)[A-Za-z0-9_$/.-]{1,180})")
                .find(candidates)?.groupValues?.get(1)
        } catch (failure: LinkageError) {
            if (!authenticated) File(context.cacheDir, "startup-private.txt").writeText(generateSequence<Throwable>(failure) { it.cause }.take(8).joinToString("\n") { it.javaClass.name + ": " + it.message.orEmpty() })
            failureType = failure.javaClass.simpleName
            failureCauses = JSONArray(generateSequence<Throwable>(failure) { it.cause }.take(8).map { cause ->
                JSONObject().put("type", cause.javaClass.simpleName).put("frames", JSONArray(cause.stackTrace.take(8).map { it.className + "." + it.methodName }))
            }.toList())
            failureFrames = JSONArray(generateSequence<Throwable>(failure) { it.cause }.take(8).flatMap { it.stackTrace.asSequence() }.take(24).map {
                it.className + "." + it.methodName
            }.filter { it.matches(Regex("[A-Za-z0-9_.$<>]+")) }.toList())
            // Only an allowlisted class-path token is exported; never arbitrary exception messages.
            val candidates = generateSequence<Throwable>(failure) { it.cause }.take(8).mapNotNull { it.message }.joinToString(" ")
            missingClass = Regex("(?:L|class \")((?:com[/.]mojang|org[/.]lwjgl|org[/.]apache|com[/.]google|joptsimple|java[/.]lang|javax[/.]management|sun[/.]misc|com[/.]sun)[A-Za-z0-9_$/.-]{1,180})")
                .find(candidates)?.groupValues?.get(1)
        }
        finally {
            console?.close()
            if (previousLogging == null) System.clearProperty("log4j.configurationFile") else System.setProperty("log4j.configurationFile", previousLogging)
            if (previousJmx == null) System.clearProperty("log4j2.disable.jmx") else System.setProperty("log4j2.disable.jmx", previousJmx)
            Thread.currentThread().contextClassLoader = originalContextLoader
        }
        File(context.cacheDir, "actual-client-initialization.json").writeText(JSONObject()
            .put("probeRunId", InstrumentationRegistry.getArguments().getString("probeRunId") ?: "manual")
            .put("authenticatedStartup", authenticated).put("phase", phase).put("verifiedJavaLibraries", records.length())
            .put("guavaTickerClassPresent", runCatching { loader.loadClass("com.google.common.base.Ticker") }.isSuccess)
            .put("guavaFailureAccessClassPresent", runCatching { loader.loadClass("com.google.common.util.concurrent.internal.InternalFutureFailureAccess") }.isSuccess)
            .put("version", "1.20.4").put("originalClientSha1", "fd19469fed4a4b4c15b2d5133985f0e3e7816a8a")
            .put("freshOwnershipVerified", true).put("translation", "D8 to DEX; ART runtime, not native AOT")
            .put("dexFiles", hashes).put("mainClassInitialized", loaded).put("failureType", failureType)
            .put("missingClass", missingClass ?: JSONObject.NULL).put("mainMethodInvoked", invoked).put("mainEntryObserved", entryObserved)
            .put("failureCauses", failureCauses).put("failureFrames", failureFrames).put("mainMenuReached", false).put("playable", false).toString(2))
    }
}
