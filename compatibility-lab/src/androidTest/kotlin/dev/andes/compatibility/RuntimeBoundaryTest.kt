package dev.andes.compatibility

import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.InMemoryDexClassLoader
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer

/** Requires a physical ARM64 Android device. Compilation alone is not a passing result. */
class RuntimeBoundaryTest {
    private fun fixture(): Class<*> {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bytes = context.assets.open("classes.dex").use { it.readBytes() }
        val loader = InMemoryDexClassLoader(ByteBuffer.wrap(bytes), javaClass.classLoader)
        return loader.loadClass("fixture.CompatibilityFixture")
    }

    @Test fun originalJava17DexReflectionAndRegisteredJni() {
        val type = fixture()
        assertEquals("java.lang.StringBuilder", type.getMethod("reflect").invoke(null)!!.javaClass.name)
        assertTrue(NativeFixtureBridge.attach(type))
        assertEquals(0x4a415641L, type.getMethod("desktopNativeHandle").invoke(null) as Long)
    }

    @Test fun conversionDoesNotSupplyDesktopJavaApis() {
        val type = fixture()
        val failure = assertThrows(InvocationTargetException::class.java) {
            type.getMethod("desktopDependency").invoke(null)
        }
        assertTrue("Desktop AWT should remain an explicit missing dependency", failure.cause is NoClassDefFoundError)
    }
}
