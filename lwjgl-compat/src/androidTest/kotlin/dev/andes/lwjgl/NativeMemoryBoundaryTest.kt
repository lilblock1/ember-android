package dev.andes.lwjgl

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

class NativeMemoryBoundaryTest {
    @Test fun concurrentAllocationsPreserveOwnershipAndReturnToBaseline() {
        val baseline = NativeMemory.allocatedBytes()
        val executor = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val jobs = (0 until 4).map { worker -> java.util.concurrent.Callable {
                repeat(200) { index ->
                    val address = MemoryUtil.nmemCalloc(8, 4)
                    try {
                        MemoryUtil.memPutInt(address, worker * 1000 + index)
                        assertEquals(worker * 1000 + index, MemoryUtil.memGetInt(address))
                        MemoryUtil.memCopy(address, address + 1, 4)
                        assertEquals(worker * 1000 + index, MemoryUtil.memGetInt(address + 1))
                    } finally { MemoryUtil.nmemFree(address) }
                }
            } }
            executor.invokeAll(jobs).forEach { it.get() }
        } finally { executor.shutdownNow() }
        assertEquals(baseline, NativeMemory.allocatedBytes())
    }
    @Test fun upstreamCompiledFixtureUsesActualArm64Memory() {
        val baseline = NativeMemory.allocatedBytes()
        assertEquals(73, AndroidGlfw.runMemoryFixture(InstrumentationRegistry.getInstrumentation().targetContext))
        assertEquals(baseline, NativeMemory.allocatedBytes())
    }
    @Test fun invalidNativeRangesAndForeignBuffersAreRejected() {
        val baseline = NativeMemory.allocatedBytes()
        val address = MemoryUtil.nmemAlloc(8)
        try {
            assertThrows(IllegalArgumentException::class.java) { MemoryUtil.memGetInt(address + 6) }
            assertThrows(IllegalArgumentException::class.java) { MemoryUtil.memByteBuffer(address, 9) }
            assertThrows(IllegalArgumentException::class.java) { MemoryUtil.nmemFree(address + 1) }
            assertThrows(IllegalArgumentException::class.java) { MemoryUtil.memUTF8(1) }
            assertThrows(IllegalArgumentException::class.java) { MemoryUtil.memAddress0(ByteBuffer.allocateDirect(8)) }
            assertThrows(ArithmeticException::class.java) { MemoryUtil.nmemCalloc(Long.MAX_VALUE, 2) }
            assertThrows(OutOfMemoryError::class.java) { MemoryUtil.nmemAlloc(256L * 1024 * 1024 + 1) }
        } finally { MemoryUtil.nmemFree(address) }
        assertThrows(IllegalArgumentException::class.java) { MemoryUtil.nmemFree(address) }
        assertThrows(IllegalArgumentException::class.java) { MemoryUtil.memGetByte(address) }
        val empty = MemoryUtil.memAlloc(0)
        MemoryUtil.memFree(empty)
        MemoryUtil.memFree(MemoryUtil.memByteBuffer(0, 0))
        assertEquals(baseline, NativeMemory.allocatedBytes())
    }
}
