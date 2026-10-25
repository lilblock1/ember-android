package dev.andes.app

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.andes.lwjgl.AndroidGlfw
import dev.andes.lwjgl.NativeBridge
import java.util.concurrent.atomic.AtomicInteger

class LwjglProbeActivity : ComponentActivity() {
    private var report by mutableStateOf("Waiting for Android Surface…")
    private var worker: Thread? = null
    private val generation = AtomicInteger()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val surface = SurfaceView(this).apply {
            contentDescription = "Original Java fixture rendered through the LWJGL API and Android EGL bridge"
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    val ticket = generation.incrementAndGet()
                    if (worker?.isAlive == true) { report = "Previous surface cleanup is pending. Return and reopen the probe."; return }
                    report = "Running original Java 17 DEX against the Android LWJGL subset…"
                    worker = Thread({
                        val result = try {
                            NativeBridge.attachSurface(holder.surface)
                            AndroidGlfw.runFixture(this@LwjglProbeActivity, true)
                        } catch (failure: Throwable) {
                            "Execution failed: ${failure.javaClass.simpleName}: ${failure.message ?: "no detail"}"
                        } finally {
                            runCatching { NativeBridge.releaseSurface() }
                        }
                        runOnUiThread { if (generation.get() == ticket && !isDestroyed) report = result }
                    }, "LWJGL-EGL-Probe").also { it.start() }
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                override fun surfaceDestroyed(holder: SurfaceHolder) { generation.incrementAndGet() }
            })
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("LWJGL / ARM64 execution experiment", style = MaterialTheme.typography.titleLarge)
                        Text("An original fixture compiled against LWJGL 3.3.2. This does not execute Minecraft.")
                        Text(report, style = MaterialTheme.typography.bodySmall)
                        AndroidView(factory = { surface }, modifier = Modifier.fillMaxWidth().weight(1f))
                        OutlinedButton(onClick = { finish() }) { Text("Return") }
                    }
                }
            }
        }
    }
}
