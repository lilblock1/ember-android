package dev.andes.lwjgl

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.lwjgl.glfw.GLFW

/** Device tests. A compiled APK does not establish that any of these passed. */
class EglBoundaryTest {
    @Test fun upstreamCompiledDexRendersAndReadsActualGlesPixel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = AndroidGlfw.runFixture(context, false)
        assertTrue(result.contains("Pixel verified:"))
        assertTrue(result.contains("OpenGL ES"))
    }
    @Test fun upstreamCompiledShaderVboVaoDrawsActualPixel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Shader/VBO/VAO pixel verified: 255,0,0", AndroidGlfw.runShaderFixture(context))
    }
    @Test fun foreignThreadAndStaleWindowHandlesAreRejected() {
        assertTrue(GLFW.glfwInit())
        try {
            GLFW.glfwDefaultWindowHints()
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_ES_API)
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
            val window = GLFW.glfwCreateWindow(8,8,"thread-test",0,0)
            assertTrue(GLFW.androidLastError(), window != 0L)
            var failure: Throwable? = null
            val foreign = Thread { try { GLFW.glfwMakeContextCurrent(window) } catch (error: Throwable) { failure = error } }
            foreign.start(); foreign.join()
            assertTrue(failure is IllegalStateException)
            GLFW.glfwDestroyWindow(window)
            assertThrows(IllegalStateException::class.java) { GLFW.glfwMakeContextCurrent(window) }
        } finally { GLFW.glfwTerminate() }
    }
}
