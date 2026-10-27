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

class MainActivity : ComponentActivity() {
    private val account get() = (application as PortApplication).account
    private val authorization = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        account.complete(it.data)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("ember.keepScreenOnForTesting", false))
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                                    Text(when {
                                        state.needsMinecraftSignIn -> "Connect Minecraft account"
                                        state.signedIn -> "Switch Microsoft account"
                                        else -> "Sign in with Microsoft"
                                    })
                                }
                                if (state.signedIn) OutlinedButton(onClick = account::refresh, enabled = !state.busy) {
                                    Text(if (account.servicesAccessEnabled) "Check Minecraft connection" else "Check Microsoft session")
                                }
                                OutlinedButton(onClick = account::logout) { Text("Remove local session / cancel sign-in") }
                                Text("Microsoft sign-in does not verify game ownership.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Minecraft installation", style = MaterialTheme.typography.titleLarge)
                                Text(if (state.ownershipVerified) "Java purchase license verified. The research client download preserves the original JAR. Game execution is not available yet."
                                    else "Verify Java ownership before downloading the official research client.")
                                if (state.signedIn) OutlinedButton(onClick = account::verifyOwnership, enabled = !state.busy) { Text("Verify Java ownership") }
                                Text(state.downloadProgress)
                                if (!state.ownershipVerified) TextButton(onClick = {
                                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.minecraft.net/en-us/store/minecraft-java-bedrock-edition-pc")))
                                }) { Text("Official Minecraft purchase / account website") }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(onClick = account::downloadResearchClient, enabled = state.ownershipVerified && !state.busy) { Text(if (state.clientDownloaded) "Check client" else "Download client") }
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
