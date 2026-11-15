package com.reelog.tvremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val bg = Color(0xFF080B12)
private val panel = Color(0xFF151B27)
private val panelRaised = Color(0xFF202A3A)
private val blue = Color(0xFF75A7FF)
private val muted = Color(0xFF9AA8BC)

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
                primary = blue, onPrimary = Color(0xFF06101F),
                background = bg, onBackground = Color.White,
                surface = panel, onSurface = Color.White, secondary = muted
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
    Surface(Modifier.fillMaxSize(), color = bg) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Smart Remote", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("SAMSUNG TV", color = blue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Surface(color = panel, shape = CircleShape) {
                    Text("LOCAL", modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp), color = blue, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(22.dp))
            Surface(color = panel, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = panelRaised, shape = RoundedCornerShape(15.dp), modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("TV", color = blue, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selectedTv?.name ?: "Mis dispositivos", fontWeight = FontWeight.SemiBold)
                            Text(if (selectedTv != null) status else "Selecciona un televisor para empezar", color = muted, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = onDiscover, enabled = !scanning) {
                            Text(if (scanning) "Buscando…" else "Buscar")
                        }
                    }
                    if (scanning) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = blue)
                    }
                    if (tvs.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        tvs.forEach { tv ->
                            TvChoice(tv = tv, selected = selectedTv?.ip == tv.ip,
                                onConnect = { selectedTv = tv; onConnect(tv) },
                                onForget = { onForget(tv) })
                            Spacer(Modifier.height(8.dp))
                        }
                    } else if (!scanning) {
                        Spacer(Modifier.height(8.dp))
                        Text("El móvil y la TV deben estar conectados a la misma red Wi-Fi.", color = muted, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onDiscover, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Text("Buscar televisores cercanos")
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CONTROL REMOTO", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text("TOCA PARA CONTROLAR", color = muted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = panel, shape = RoundedCornerShape(30.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        RoundKey("⏻", "KEY_POWER", onKey, 48)
                        RoundKey("↶", "KEY_RETURN", onKey, 48)
                        RoundKey("⌂", "KEY_HOME", onKey, 48)
                        RoundKey("⋮", "KEY_MENU", onKey, 48)
                    }
                    Spacer(Modifier.height(18.dp))
                    Surface(color = panelRaised, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("NAVEGACIÓN", color = muted, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(8.dp))
                            RoundKey("⌃", "KEY_UP", onKey, 54)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RoundKey("‹", "KEY_LEFT", onKey, 54)
                                Spacer(Modifier.width(10.dp))
                                Button(onClick = { onKey("KEY_ENTER") }, modifier = Modifier.size(78.dp),
                                    shape = CircleShape, contentPadding = PaddingValues(0.dp)) { Text("OK", fontWeight = FontWeight.Bold) }
                                Spacer(Modifier.width(10.dp))
                                RoundKey("›", "KEY_RIGHT", onKey, 54)
                            }
                            RoundKey("⌄", "KEY_DOWN", onKey, 54)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VerticalControls("VOLUMEN", "KEY_VOLUP", "KEY_VOLDOWN", "KEY_MUTE", onKey, Modifier.weight(1f))
                        VerticalControls("CANALES", "KEY_CHUP", "KEY_CHDOWN", "KEY_SOURCE", onKey, Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(color = panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("INTRODUCIR TEXTO", color = blue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = typedText, onValueChange = { typedText = it },
                        modifier = Modifier.fillMaxWidth(), placeholder = { Text("Buscar una película o escribir…") },
                        singleLine = true, shape = RoundedCornerShape(14.dp))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onText(typedText) }, enabled = typedText.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Enviar a la televisión") }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Desconectar") }
            Spacer(Modifier.height(18.dp))
            Text("Control local · Sin anuncios · Sin cuenta", modifier = Modifier.align(Alignment.CenterHorizontally),
                color = muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TvChoice(tv: SamsungTv, selected: Boolean, onConnect: () -> Unit, onForget: () -> Unit) {
    Surface(color = if (selected) Color(0xFF1D2C43) else panelRaised,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFF263954), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("TV", color = blue, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tv.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(tv.model, color = muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(tv.ip, color = muted, style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = onConnect, contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp)) { Text("Conectar") }
                TextButton(onClick = onForget, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Text("Olvidar", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun RoundKey(label: String, key: String, onKey: (String) -> Unit, size: Int) {
    FilledTonalButton(onClick = { onKey(key) }, modifier = Modifier.size(size.dp),
        shape = CircleShape, contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = panelRaised, contentColor = Color.White)) {
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun VerticalControls(title: String, up: String, down: String, extra: String, onKey: (String) -> Unit, modifier: Modifier) {
    Surface(color = panelRaised, shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = blue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            FilledTonalButton(onClick = { onKey(up) }, modifier = Modifier.fillMaxWidth()) { Text("+") }
            FilledTonalButton(onClick = { onKey(down) }, modifier = Modifier.fillMaxWidth()) { Text("−") }
            FilledTonalButton(onClick = { onKey(extra) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (extra == "KEY_MUTE") "MUTE" else "SOURCE", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
