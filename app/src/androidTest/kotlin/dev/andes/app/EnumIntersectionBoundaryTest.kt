package dev.andes.app

import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.InMemoryDexClassLoader
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer

/** Original generic-array reproducer; no game content or credentials. */
class EnumIntersectionBoundaryTest {
    @Test fun comparePinnedCompilerArrayVerification() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("arrayFixtureProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = JSONArray()
        for (variant in listOf("old-debug", "old-release", "current-debug", "current-release", "adapted")) {
            val path = File(context.filesDir, "array-fixture/$variant.dex")
            val loader = InMemoryDexClassLoader(ByteBuffer.wrap(path.readBytes()), context.classLoader)
            val item = JSONObject().put("variant", variant)
            try {
                val result = loader.loadClass("fixture.EnumIntersectionFixture").getMethod("run").invoke(null) as Int
                item.put("result", result).put("expectedResult", 2).put("verifiedAndExecuted", result == 2)
            } catch (failure: Throwable) {
                val cause = if (failure is InvocationTargetException) failure.targetException else failure
                item.put("verifiedAndExecuted", false).put("failureType", cause.javaClass.simpleName)
            }
            report.put(item)
        }
        File(context.cacheDir, "enum-intersection-evidence.json").writeText(report.toString(2))
        org.junit.Assert.assertTrue("Adapted original array fixture must execute and return 2", report.getJSONObject(4).getBoolean("verifiedAndExecuted"))
    }
}
