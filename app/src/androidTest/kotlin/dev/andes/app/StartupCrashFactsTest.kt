package dev.andes.app

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Reads only this isolated experiment's new crash file, inside the phone. Raw text stays private. */
class StartupCrashFactsTest {
    @Test fun exportsOnlyStructuralFactsAfterClientTerminatesProcess() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("startupCrashFacts") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pending = JSONObject(File(context.cacheDir, "startup-probe-pending.json").readText())
        assertEquals(InstrumentationRegistry.getArguments().getString("probeRunId"), pending.getString("probeRunId"))
        val directory = File(context.filesDir, "research-client/game-1.20.4/crash-reports")
        val file = requireNotNull(directory.listFiles()).filter { it.isFile && it.name.matches(Regex("crash-[0-9_.-]+-client\\.txt")) }
            .filter { it.lastModified() >= pending.getLong("startedAt") - 1000 }.maxByOrNull { it.lastModified() }
        requireNotNull(file) { "No new isolated client crash report" }
        require(file.length() <= 2 * 1024 * 1024)
        val content = file.readText()
        val frames = Regex("(?m)^\\s*at ([A-Za-z0-9_.$<>]+)\\(").findAll(content).map { it.groupValues[1] }.distinct().take(80).toList()
        val types = Regex("(?m)^(?:Caused by: )?((?:java|javax|org|com|dev)\\.[A-Za-z0-9_.$]+(?:Exception|Error))(?::|$)").findAll(content).map { it.groupValues[1] }.distinct().take(16).toList()
        val missing = Regex("(?:L|class \")((?:org/lwjgl|java/lang|com/mojang|javax/management)[A-Za-z0-9_$/]{1,180})[;\"]").findAll(content).map { it.groupValues[1] }.distinct().take(16).toList()
        File(context.cacheDir, "startup-crash-facts.json").writeText(JSONObject()
            .put("probeRunId", pending.getString("probeRunId")).put("authenticatedStartup", pending.getBoolean("authenticatedStartup"))
            .put("terminatedBeforeProbeReport", true).put("exceptionTypes", JSONArray(types)).put("failureFrames", JSONArray(frames))
            .put("missingClasses", JSONArray(missing)).put("mainMenuReached", false).put("playable", false).toString(2))
    }
}
