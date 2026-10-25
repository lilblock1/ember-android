package dev.andes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.andes.engine.ProbeSurface

class ProbeActivity : ComponentActivity() {
    private lateinit var surface: ProbeSurface
    private var report by mutableStateOf("Creating native GLES context…")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        surface = ProbeSurface(this) { report = it }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Native surface / input probe", style = MaterialTheme.typography.titleLarge)
                        Text(report, style = MaterialTheme.typography.bodySmall)
                        Text("Touch the surface to place a marker. Rendering runs only when requested.")
                        AndroidView(factory = { surface }, modifier = Modifier.fillMaxWidth().weight(1f))
                        OutlinedButton(onClick = { finish() }) { Text("Return to foundation") }
                    }
                }
            }
        }
    }
    override fun onResume() { super.onResume(); surface.onResume() }
    override fun onPause() { surface.onPause(); super.onPause() }
}
