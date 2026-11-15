package com.reelog.tvremote

import android.os.Bundle
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

private val bg = Color(0xFF14292B)
private val panel = Color(0xFF263B3D)
private val panelLight = Color(0xFF34494B)
private val panelInner = Color(0xFF304547)
private val accent = Color(0xFF43C879)
private val muted = Color(0xFF9EADAC)
private val white = Color(0xFFF5F7F6)

class MainActivity : ComponentActivity() {
    private lateinit var remote: SamsungRemote
    private lateinit var discovery: SamsungTvDiscovery

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val statusState = mutableStateOf("Busca televisores Samsung en tu red")
        val scanningState = mutableStateOf(false)
        val foundTvs = mutableStateListOf<SamsungTv>()
        remote = SamsungRemote(applicationContext) { message ->
            runOnUiThread { statusState.value = message }
        }
        discovery = SamsungTvDiscovery(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFB9D8D5), onPrimary = Color(0xFF102426),
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
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("⏻", color = accent, style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.clickable { onKey("KEY_POWER") }.padding(5.dp))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showDevices = true }) {
                        Text(selectedTv?.name ?: "Sala de estar", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("⌄", color = muted, style = MaterialTheme.typography.titleLarge)
                    }
                    Text(if (selectedTv != null) status else "Samsung TV · Red local",
                        color = muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                Text("◖))", color = white, style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.clickable { onKey("KEY_MUTE") }.padding(5.dp))
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTile("prime", Color(0xFF19A8E0), Modifier.weight(1f))
                AppTile("Disney+", white, Modifier.weight(1f))
                AppTile("NETFLIX", Color(0xFFE53945), Modifier.weight(1f))
            }

            Spacer(Modifier.height(28.dp))
            Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                Surface(color = panelLight, shape = CircleShape, modifier = Modifier.fillMaxSize()) {}
                Surface(color = panelInner, shape = CircleShape, modifier = Modifier.size(140.dp)) {}
                Text("⌃", color = muted, style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp)
                        .clickable { onKey("KEY_UP") })
                Text("‹", color = muted, style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 25.dp)
                        .clickable { onKey("KEY_LEFT") })
                Text("›", color = muted, style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 25.dp)
                        .clickable { onKey("KEY_RIGHT") })
                Text("⌄", color = muted, style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp)
                        .clickable { onKey("KEY_DOWN") })
                Text("OK", color = white, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center).clickable { onKey("KEY_ENTER") }
                        .padding(35.dp))
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TallControl("＋", "VOL", "－", onPlus = { onKey("KEY_VOLUP") },
                    onMinus = { onKey("KEY_VOLDOWN") }, modifier = Modifier.weight(0.82f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteTile("⟲", "10", Modifier.weight(1f)) { onKey("KEY_REWIND") }
                        RemoteTile("▶Ⅱ", "", Modifier.weight(1f)) { onKey("KEY_PLAY") }
                        RemoteTile("⟳", "10", Modifier.weight(1f)) { onKey("KEY_FF") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteTile("←", "", Modifier.weight(1f)) { onKey("KEY_RETURN") }
                        RemoteTile("⌂", "", Modifier.weight(1f)) { onKey("KEY_HOME") }
                    }
                }
                TallControl("⌃", "CH", "⌄", onPlus = { onKey("KEY_CHUP") },
                    onMinus = { onKey("KEY_CHDOWN") }, modifier = Modifier.weight(0.82f))
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { i ->
                    Box(Modifier.size(9.dp).background(if (selectedTab == i) white else Color(0xFF526568), CircleShape))
                }
            }

            Spacer(Modifier.height(20.dp))
            Surface(color = panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("TELEVISIÓN", color = muted, style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold)
                            Text(selectedTv?.model ?: "Conecta con tu Samsung TV", fontWeight = FontWeight.SemiBold)
                            Text(selectedTv?.ip ?: status, color = muted, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { showDevices = true }) { Text("Dispositivos") }
                    }
                    if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (selectedTv == null) {
                        Button(onClick = { showDevices = true; onDiscover() }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (scanning) "Buscando…" else "Buscar televisores")
                        }
                    } else {
                        OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Desconectar") }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Surface(color = panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("ENVIAR TEXTO A LA TV", color = muted, style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(value = typedText, onValueChange = { typedText = it },
                        modifier = Modifier.fillMaxWidth(), placeholder = { Text("Escribe aquí…") },
                        singleLine = true, shape = RoundedCornerShape(14.dp))
                    Button(onClick = { onText(typedText) }, enabled = typedText.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()) { Text("Enviar") }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(24.dp)).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                BottomItem("▣", "Mando", 0, selectedTab) { selectedTab = 0 }
                BottomItem("▦", "Apps", 1, selectedTab) { selectedTab = 1 }
                BottomItem("◔", "Transmitir", 2, selectedTab) { selectedTab = 2 }
                BottomItem("⚙", "Ajustes", 3, selectedTab) { selectedTab = 3 }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDevices) {
        AlertDialog(
            onDismissRequest = { showDevices = false },
            containerColor = panel,
            title = { Text("Televisores Samsung") },
            text = {
                Column {
                    Text(status, color = muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    if (tvs.isEmpty()) Text("Busca televisores conectados a tu misma Wi-Fi.")
                    tvs.forEach { tv ->
                        TvChoice(tv, selectedTv?.ip == tv.ip,
                            onConnect = { selectedTv = tv; onConnect(tv); showDevices = false },
                            onForget = { onForget(tv) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDiscover) { Text(if (scanning) "Buscando…" else "Buscar") } },
            dismissButton = { TextButton(onClick = { showDevices = false }) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun AppTile(name: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(color = panelLight, shape = RoundedCornerShape(20.dp), modifier = modifier.height(72.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(name, color = tint, fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TallControl(top: String, label: String, bottom: String, onPlus: () -> Unit,
                        onMinus: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = panelLight, shape = RoundedCornerShape(22.dp), modifier = modifier.height(206.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            Text(top, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clickable { onPlus() })
            Text(label, color = white, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Text(bottom, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clickable { onMinus() })
        }
    }
}

@Composable
private fun RemoteTile(symbol: String, caption: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(color = panelLight, shape = RoundedCornerShape(20.dp),
        modifier = modifier.height(98.dp).clickable { onClick() }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(symbol, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (caption.isNotEmpty()) Text(caption, color = muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun BottomItem(icon: String, label: String, index: Int, selected: Int, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 5.dp)) {
        Text(icon, color = if (selected == index) white else muted, style = MaterialTheme.typography.titleLarge)
        Text(label, color = if (selected == index) white else muted, style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun TvChoice(tv: SamsungTv, selected: Boolean, onConnect: () -> Unit, onForget: () -> Unit) {
    Surface(color = if (selected) panelLight else panelInner,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tv.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(tv.model, color = muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(tv.ip, color = muted, style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = onConnect, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) {
                    Text("Conectar")
                }
                TextButton(onClick = onForget) { Text("Olvidar") }
            }
        }
    }
}
