package com.reelog.tvremote

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val bg = Color(0xFF050607)
private val panel = Color(0xFF111316)
private val panelLight = Color(0xFF24272C)
private val panelInner = Color(0xFF191C20)
private val accent = Color(0xFFF2F3F5)
private val muted = Color(0xFF92979F)
private val white = Color(0xFFF5F7F6)

class MainActivity : ComponentActivity() {
    private lateinit var remote: SamsungRemote
    private lateinit var discovery: SamsungTvDiscovery

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.navigationBarColor = android.graphics.Color.rgb(5, 6, 7)
        val statusState = mutableStateOf("Busca televisores Samsung en tu red")
        val scanningState = mutableStateOf(false)
        val foundTvs = mutableStateListOf<SamsungTv>()
        remote = SamsungRemote(applicationContext) { message ->
            runOnUiThread { statusState.value = message }
        }
        discovery = SamsungTvDiscovery(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFE8EAED), onPrimary = Color(0xFF101114),
                background = bg, onBackground = white,
                surface = panel, onSurface = white, secondary = muted
            )) {
                RemoteHome(
                    status = statusState.value, tvs = foundTvs, scanning = scanningState.value,
                    onDiscover = {
                        if (!scanningState.value) {
                            scanningState.value = true
                            statusState.value = "Buscando televisores en tu red local…"
                            lifecycleScope.launch {
                                try {
                                    foundTvs.clear()
                                    foundTvs.addAll(discovery.discover())
                                    statusState.value = if (foundTvs.isEmpty())
                                        "No se encontraron televisores. Comprueba que estén encendidos y en la misma Wi-Fi."
                                    else "${foundTvs.size} televisor(es) encontrado(s)"
                                } catch (e: Exception) {
                                    statusState.value = "Error al buscar: ${e.localizedMessage ?: "red no disponible"}"
                                } finally { scanningState.value = false }
                            }
                        }
                    },
                    onConnect = { remote.connect(it.ip) },
                    onKey = { remote.sendKey(it) },
                    onText = { remote.sendText(it) },
                    onForget = { remote.forgetPairing(it.ip) },
                    onDisconnect = { remote.close() }
                )
            }
        }
    }

    override fun onDestroy() {
        if (::remote.isInitialized) remote.close()
        super.onDestroy()
    }
}

@Composable
private fun RemoteHome(
    status: String, tvs: SnapshotStateList<SamsungTv>, scanning: Boolean,
    onDiscover: () -> Unit, onConnect: (SamsungTv) -> Unit,
    onKey: (String) -> Unit, onText: (String) -> Unit,
    onForget: (SamsungTv) -> Unit, onDisconnect: () -> Unit
) {
    var typedText by remember { mutableStateOf("") }
    var selectedTv by remember { mutableStateOf<SamsungTv?>(null) }
    var showDevices by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    Surface(Modifier.fillMaxSize(), color = bg) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("⏻", color = accent, style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.clickable { onKey("KEY_POWER") }.padding(5.dp))
                Spacer(Modifier.weight(1f))
                Text("TvRemote", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = white)
                Spacer(Modifier.weight(1f))
                Text("◖))", color = white, style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.clickable { onKey("KEY_MUTE") }.padding(5.dp))
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = panel, shape = CircleShape,
                modifier = Modifier.fillMaxWidth().clickable { showDevices = true }) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("▣", color = muted, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.size(8.dp).background(if (selectedTv != null) Color(0xFF55D69A) else muted, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text(selectedTv?.model ?: "Seleccionar televisor", color = white,
                        modifier = Modifier.weight(1f), maxLines = 1)
                    Text("⌄", color = muted, style = MaterialTheme.typography.titleLarge)
                }
            }
