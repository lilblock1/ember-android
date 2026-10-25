package dev.andes.engine

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

internal object NativeProbe {
    init { System.loadLibrary("andes_probe") }
    external fun create(): Long
    external fun destroy(handle: Long)
    external fun surfaceCreated(handle: Long): String
    external fun resize(handle: Long, width: Int, height: Int)
    external fun touch(handle: Long, x: Float, y: Float)
    external fun pause(handle: Long)
    external fun draw(handle: Long)
}

/** Event-driven rendering; no polling or render loop while idle. */
class ProbeSurface(context: Context, private val report: (String) -> Unit) : GLSurfaceView(context) {
    private var handle = 0L // Access only on the GL thread.
    init {
        contentDescription = "Native graphics and touch probe. Touch to place a green marker."
        setEGLContextClientVersion(3)
        setRenderer(object : Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                if (handle == 0L) handle = NativeProbe.create()
                val result = NativeProbe.surfaceCreated(handle)
                post { report(result) }
            }
            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) = NativeProbe.resize(handle, width, height)
            override fun onDrawFrame(gl: GL10?) = NativeProbe.draw(handle)
        })
        renderMode = RENDERMODE_WHEN_DIRTY
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
            val x = event.x / width.coerceAtLeast(1)
            val y = event.y / height.coerceAtLeast(1)
            queueEvent { NativeProbe.touch(handle, x, y) }
            requestRender()
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) { performClick(); return true }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onPause() {
        queueEvent { NativeProbe.pause(handle) }
        super.onPause()
    }
    override fun onDetachedFromWindow() {
        queueEvent { NativeProbe.destroy(handle); handle = 0 }
        super.onDetachedFromWindow()
    }
}
