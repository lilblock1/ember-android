package dev.andes.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.edit
import dev.andes.core.MinecraftInstallationService

class MainActivity : ComponentActivity() {
    private val account get() = (application as PortApplication).account
    private val authorization = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        account.complete(it.data)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = getSharedPreferences("appearance", MODE_PRIVATE)
        setContent {
            var dark by remember { mutableStateOf(settings.getBoolean("dark", true)) }
            val state by account.status.collectAsStateWithLifecycle()
            var diagnostics by remember { mutableStateOf(DeviceDiagnostics.describe(this)) }
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState())
                        .widthIn(max = 840.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("EMBER / RESEARCH BUILD", style = MaterialTheme.typography.labelLarge)
                        Text("Java Edition on Android", style = MaterialTheme.typography.headlineLarge)
                        Text("Android foundation and native platform probe. Minecraft is not yet executable.")
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Microsoft account", style = MaterialTheme.typography.titleLarge)
                                Text(state.message)
                                Text(state.minecraft)
                                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                Button(onClick = {
                                    account.begin()?.let { intent ->
                                        try { authorization.launch(intent) } catch (_: Exception) { account.complete(null) }
                                    }
                                }, enabled = !state.busy) {
                                    Text(if (state.signedIn) "Switch Microsoft account" else "Sign in with Microsoft")
                                }
                                if (state.signedIn) OutlinedButton(onClick = account::refresh, enabled = !state.busy) { Text("Check Microsoft session") }
                                OutlinedButton(onClick = account::logout) { Text("Remove local session / cancel sign-in") }
                                Text("Microsoft sign-in does not verify game ownership.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Minecraft installation", style = MaterialTheme.typography.titleLarge)
                                Text(MinecraftInstallationService().installationBlocker())
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(onClick = {}, enabled = false) { Text("Install") }
                                    Button(onClick = {}, enabled = false) { Text("Play") }
                                }
                            }
                        }
                        Text("ARM64 platform laboratory", style = MaterialTheme.typography.titleLarge)
                        Text("Inspect the native graphics surface and touch bridge. This probe contains no Minecraft code or assets.")
                        Button(onClick = { startActivity(Intent(this@MainActivity, ProbeActivity::class.java)) }) { Text("Open native surface probe") }
                        OutlinedButton(onClick = { startActivity(Intent(this@MainActivity, LwjglProbeActivity::class.java)) }) { Text("Open LWJGL execution probe") }
                        Text(diagnostics)
                        OutlinedButton(onClick = { diagnostics = DeviceDiagnostics.describe(this@MainActivity) }) { Text("Refresh device information") }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Dark appearance", Modifier.padding(top = 12.dp))
                            Switch(checked = dark, onCheckedChange = { dark = it; settings.edit { putBoolean("dark", it) } })
                        }
                        Text("Unofficial independent research project. Not approved by or associated with Mojang or Microsoft.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
