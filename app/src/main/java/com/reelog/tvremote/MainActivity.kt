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

private val bg = Color(0xFF0B0D10)
private val card = Color(0xFF171B21)
private val card2 = Color(0xFF202630)
private val accent = Color(0xFFC5F36B)
private val muted = Color(0xFF9AA4B2)

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
                primary = accent, onPrimary = Color(0xFF11150A),
                background = bg, onBackground = Color.White,
                surface = card, onSurface = Color.White,
                secondary = muted
            )) {
                RemoteHome(
                    status = statusState.value,
                    tvs = foundTvs,
                    scanning = scanningState.value,
                    onDiscover = {
                        if (!scanningState.value) {
                            scanningState.value = true
                            statusState.value = "Buscando televisores en tu red local…"
                            lifecycleScope.launch {
                                try {
                                    foundTvs.clear()
                                    foundTvs.addAll(discovery.discover())
                                    statusState.value = if (foundTvs.isEmpty()) {
                                        "No se encontraron televisores. Comprueba que estén encendidos y en la misma Wi-Fi."
                                    } else {
                                        "${foundTvs.size} televisor(es) encontrado(s)"
                                    }
                                } catch (e: Exception) {
                                    statusState.value = "Error al buscar: ${e.localizedMessage ?: "red no disponible"}"
                                } finally {
                                    scanningState.value = false
                                }
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
    status: String,
    tvs: SnapshotStateList<SamsungTv>,
    scanning: Boolean,
    onDiscover: () -> Unit,
    onConnect: (SamsungTv) -> Unit,
    onKey: (String) -> Unit,
    onText: (String) -> Unit,
    onForget: (SamsungTv) -> Unit,
    onDisconnect: () -> Unit
) {
    var typedText by remember { mutableStateOf("") }

    Surface(Modifier.fillMaxSize(), color = bg) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("REEL TV", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("SMART REMOTE", color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Surface(color = card, shape = CircleShape) {
                    Text("TIZEN", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = accent, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(22.dp))

            Surface(color = card, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = card2, shape = CircleShape, modifier = Modifier.size(42.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("TV", color = accent, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Televisores disponibles", fontWeight = FontWeight.SemiBold)
                            Text(status, color = muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(color = Color(0xFF26351C), shape = CircleShape) {
                            Text("LAN", color = accent, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onDiscover,
                        enabled = !scanning,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (scanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Buscando televisores…")
                        } else {
                            Text(if (tvs.isEmpty()) "Buscar televisores" else "Volver a buscar")
                        }
                    }
                    if (tvs.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        tvs.forEach { tv ->
                            TvChoice(
                                tv = tv,
                                onConnect = { onConnect(tv) },
                                onForget = { onForget(tv) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    } else if (!scanning) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Asegúrate de que el móvil y la TV estén conectados a la misma red. La primera vez tendrás que aceptar el permiso en la pantalla del televisor.",
                            color = muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("MANDO", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("NAVEGACIÓN", color = muted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(10.dp))

            Surface(color = card, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MiniKey("⏻", "KEY_POWER", onKey)
                        MiniKey("↶", "KEY_RETURN", onKey)
                        MiniKey("⌂", "KEY_HOME", onKey)
                        MiniKey("⋯", "KEY_MENU", onKey)
                    }
                    Spacer(Modifier.height(14.dp))
                    PadKey("▲", "KEY_UP", onKey)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PadKey("◀", "KEY_LEFT", onKey)
                        Spacer(Modifier.width(10.dp))
                        PadKey("OK", "KEY_ENTER", onKey, true)
                        Spacer(Modifier.width(10.dp))
                        PadKey("▶", "KEY_RIGHT", onKey)
                    }
                    PadKey("▼", "KEY_DOWN", onKey)
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlStack("VOLUMEN", listOf("+" to "KEY_VOLUP", "−" to "KEY_VOLDOWN", "MUTE" to "KEY_MUTE"), onKey, Modifier.weight(1f))
                ControlStack("CANALES", listOf("+" to "KEY_CHUP", "−" to "KEY_CHDOWN", "SOURCE" to "KEY_SOURCE"), onKey, Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))
            Surface(color = card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("TECLADO DEL MÓVIL", color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = typedText, onValueChange = { typedText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Escribe texto…") }, singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onText(typedText) }, enabled = typedText.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text("Enviar a la TV")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Desconectar") }
            Spacer(Modifier.height(12.dp))
            Text("Conexión local cifrada · Sin anuncios · Sin cuenta", color = muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TvChoice(tv: SamsungTv, onConnect: () -> Unit, onForget: () -> Unit) {
    Surface(color = card2, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFF303B2A), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("TV", color = accent, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tv.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(tv.model, color = muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(tv.ip, color = muted, style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = onConnect, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("Usar")
                }
                TextButton(onClick = onForget, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Text("Olvidar", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun MiniKey(label: String, key: String, onKey: (String) -> Unit) {
    Button(onClick = { onKey(key) }, modifier = Modifier.size(58.dp), shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = card2)) {
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PadKey(label: String, key: String, onKey: (String) -> Unit, selected: Boolean = false) {
    Button(onClick = { onKey(key) }, modifier = Modifier.padding(3.dp).size(if (selected) 76.dp else 62.dp),
        shape = if (selected) RoundedCornerShape(24.dp) else CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) accent else card2, contentColor = if (selected) Color.Black else Color.White)) {
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ControlStack(
    title: String,
    keys: List<Pair<String, String>>,
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = card, shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            keys.forEach { (label, key) ->
                Button(onClick = { onKey(key) }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).height(42.dp),
                    shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = card2)) {
                    Text(label, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
