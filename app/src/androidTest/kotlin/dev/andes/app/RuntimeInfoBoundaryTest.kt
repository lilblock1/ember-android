package dev.andes.app

import dev.ember.platform.management.ManagementFactory
import org.junit.Assert.*
import org.junit.Test

class RuntimeInfoBoundaryTest {
    @Test fun runtimeInformationUsesAndroidProcessAndGcCounters() {
        val runtime = ManagementFactory.getRuntimeMXBean()
        assertTrue(runtime.inputArguments.isEmpty())
        val start = android.os.Process.getStartElapsedRealtime()
        val before = android.os.SystemClock.elapsedRealtime() - start
        val measured = runtime.uptime
        val after = android.os.SystemClock.elapsedRealtime() - start
        assertTrue(measured in before..after)
        assertTrue(runtime.startTime > 0)
        val gc = ManagementFactory.getGarbageCollectorMXBeans().single()
        val nativeCount = android.os.Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: -1
        val actual = gc.collectionCount
        assertTrue(actual == -1L || nativeCount == -1L || actual >= nativeCount)
        assertTrue(gc.collectionTime >= -1)
    }
}
