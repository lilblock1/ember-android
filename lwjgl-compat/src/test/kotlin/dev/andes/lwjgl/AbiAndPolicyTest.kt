package dev.andes.lwjgl

import org.junit.Assert.*
import org.junit.Test
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11C
import java.io.File
import java.net.URLClassLoader
import java.lang.reflect.Modifier
import java.nio.ByteBuffer

class AbiAndPolicyTest {
    @Test fun publicLwjglMethodsMatchReal332Descriptors() {
        val paths = requireNotNull(System.getProperty("lwjgl.reference.classpath")).split(File.pathSeparator).map { File(it).toURI().toURL() }
        URLClassLoader(paths.toTypedArray(), ClassLoader.getSystemClassLoader().parent).use { upstream ->
            for (name in listOf("org.lwjgl.glfw.GLFW", "org.lwjgl.opengl.GL11", "org.lwjgl.opengl.GL11C")) {
                val shim = Class.forName(name, false, javaClass.classLoader)
                val reference = Class.forName(name, false, upstream)
                for (method in shim.declaredMethods.filter { Modifier.isPublic(it.modifiers) && !it.name.startsWith("android") }) {
                    val original = reference.getMethod(method.name, *method.parameterTypes)
                    assertEquals("$name.${method.name}", original.returnType, method.returnType)
                    assertTrue(Modifier.isStatic(original.modifiers))
                }
            }
        }
    }
    @Test fun desktopGlIsRejectedBeforeAnyNativeLoading() {
        GLFW.glfwDefaultWindowHints()
        assertEquals(0L, GLFW.glfwCreateWindow(64,64,"client",0,0))
        assertTrue(GLFW.androidLastError().contains("Desktop OpenGL"))
    }
    @Test fun unsupportedContextAndMonitorRequestsFailClosed() {
        GLFW.glfwDefaultWindowHints()
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_ES_API)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4)
        assertEquals(0L, GLFW.glfwCreateWindow(64,64,"probe",0,0))
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
        assertEquals(0L, GLFW.glfwCreateWindow(64,64,"probe",1,0))
        assertTrue(GLFW.androidLastError().contains("Monitor"))
    }
    @Test fun nativeReadbackCannotWriteBeyondJavaBuffers() {
        assertThrows(IllegalArgumentException::class.java) { GL11C.glReadPixels(0,0,1,1,GL11C.GL_RGBA,GL11C.GL_UNSIGNED_BYTE,ByteBuffer.allocate(4)) }
        assertThrows(IllegalArgumentException::class.java) { GL11C.glReadPixels(0,0,2,2,GL11C.GL_RGBA,GL11C.GL_UNSIGNED_BYTE,ByteBuffer.allocateDirect(4)) }
        assertThrows(IllegalArgumentException::class.java) { GL11C.glReadPixels(0,0,1,1,GL11C.GL_RGBA,GL11C.GL_UNSIGNED_BYTE,ByteBuffer.allocateDirect(4).asReadOnlyBuffer()) }
    }
}
