package dev.andes.lwjgl

import android.content.Context
import dalvik.system.InMemoryDexClassLoader
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer

object AndroidGlfw {
    fun runShaderFixture(context: Context): String {
        val data = context.assets.open("lwjgl-fixture/classes.dex").use { it.readBytes() }
        val loader = InMemoryDexClassLoader(ByteBuffer.wrap(data), javaClass.classLoader)
        return try { loader.loadClass("fixture.LwjglBoundaryFixture").getMethod("renderShader").invoke(null) as String }
        catch (failure: InvocationTargetException) { throw (failure.cause ?: failure) }
    }
    /** Only an original, packaged fixture is loaded. No arbitrary client loading path exists. */
    fun runFixture(context: Context, visible: Boolean): String {
        val data = context.assets.open("lwjgl-fixture/classes.dex").use { it.readBytes() }
        val loader = InMemoryDexClassLoader(ByteBuffer.wrap(data), javaClass.classLoader)
        val type = loader.loadClass("fixture.LwjglBoundaryFixture")
        return try { type.getMethod("render", Boolean::class.javaPrimitiveType).invoke(null, visible) as String }
        catch (failure: InvocationTargetException) { throw (failure.cause ?: failure) }
    }
}
