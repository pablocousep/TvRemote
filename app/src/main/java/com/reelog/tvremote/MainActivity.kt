package com.reelog.tvremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val bg = Color(0xFF0B0D10)
private val card = Color(0xFF171B21)
private val card2 = Color(0xFF202630)
private val accent = Color(0xFFC5F36B)
private val muted = Color(0xFF9AA4B2)

class MainActivity : ComponentActivity() {
    private lateinit var remote: SamsungRemote

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val statusState = mutableStateOf("Listo para conectar")
        remote = SamsungRemote(applicationContext) { message ->
            runOnUiThread { statusState.value = message }
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = accent, onPrimary = Color(0xFF11150A),
                background = bg, onBackground = Color.White,
                surface = card, onSurface = Color.White,
                secondary = muted
            )) {
                RemoteHome(
                    status = statusState.value,
                    onConnect = { remote.connect(it) },
                    onKey = { remote.sendKey(it) },
                    onText = { remote.sendText(it) },
                    onForget = { remote.forgetPairing(it) },
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
    onConnect: (String) -> Unit,
    onKey: (String) -> Unit,
    onText: (String) -> Unit,
    onForget: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    var ip by remember { mutableStateOf("") }
    var typedText by remember { mutableStateOf("") }

    Surface(Modifier.fillMaxSize(), color = bg) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
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
                            Text("Tu televisor Samsung", fontWeight = FontWeight.SemiBold)
                            Text(status, color = muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(color = Color(0xFF26351C), shape = CircleShape) {
                            Text("LOCAL", color = accent, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = ip, onValueChange = { ip = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Dirección IP del televisor") },
                        placeholder = { Text("192.168.1.50") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onConnect(ip.trim()) }, enabled = ip.isNotBlank(), modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                            Text("Conectar")
                        }
                        OutlinedButton(onClick = onDisconnect, shape = RoundedCornerShape(14.dp)) { Text("Parar") }
                        TextButton(onClick = { onForget(ip) }, enabled = ip.isNotBlank()) { Text("Olvidar") }
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
            Spacer(Modifier.weight(1f))
            Text("Conexión local cifrada · Sin anuncios · Sin cuenta", color = muted, style = MaterialTheme.typography.labelSmall)
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
