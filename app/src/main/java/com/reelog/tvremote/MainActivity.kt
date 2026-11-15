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

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTile("prime", Color(0xFF19A8E0), Modifier.weight(1f))
                AppTile("Disney+", white, Modifier.weight(1f))
                AppTile("NETFLIX", Color(0xFFE53945), Modifier.weight(1f))
            }

            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                Surface(color = panelLight, shape = CircleShape, modifier = Modifier.fillMaxSize()) {}
                Surface(color = panelInner, shape = CircleShape, modifier = Modifier.fillMaxSize(0.46f)) {}
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

            Spacer(Modifier.height(12.dp))
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
private fun RemoteIcon(kind: String, modifier: Modifier = Modifier, tint: Color = white) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = (minOf(w, h) * .085f).coerceAtLeast(1.8.dp.toPx())
        val stroke = Stroke(width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(tint, Offset(x1*w, y1*h), Offset(x2*w, y2*h), sw,
                cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        when (kind) {
            "power" -> {
                drawArc(tint, 135f, 270f, false, Offset(w*.16f,h*.16f), Size(w*.68f,h*.68f), style=stroke)
                line(.5f,.04f,.5f,.48f)
            }
            "volume" -> {
                val p = Path().apply { moveTo(w*.10f,h*.40f); lineTo(w*.30f,h*.40f); lineTo(w*.56f,h*.20f); lineTo(w*.56f,h*.80f); lineTo(w*.30f,h*.60f); lineTo(w*.10f,h*.60f); close() }
                drawPath(p,tint)
                drawArc(tint,-55f,110f,false,Offset(w*.38f,h*.20f),Size(w*.48f,h*.60f),style=stroke)
                drawArc(tint,-48f,96f,false,Offset(w*.45f,h*.08f),Size(w*.48f,h*.84f),style=stroke)
            }
            "tv" -> { drawRoundRect(tint,Offset(w*.12f,h*.18f),Size(w*.76f,h*.58f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(sw),style=stroke); line(.34f,.88f,.66f,.88f) }
            "up" -> { line(.18f,.68f,.5f,.30f); line(.5f,.30f,.82f,.68f) }
            "down" -> { line(.18f,.32f,.5f,.70f); line(.5f,.70f,.82f,.32f) }
            "left" -> { line(.68f,.16f,.32f,.5f); line(.32f,.5f,.68f,.84f) }
            "right" -> { line(.32f,.16f,.68f,.5f); line(.68f,.5f,.32f,.84f) }
            "back" -> { line(.78f,.5f,.22f,.5f); line(.22f,.5f,.48f,.24f); line(.22f,.5f,.48f,.76f) }
            "home" -> { val p=Path().apply { moveTo(w*.16f,h*.46f); lineTo(w*.5f,h*.16f); lineTo(w*.84f,h*.46f); lineTo(w*.84f,h*.86f); lineTo(w*.16f,h*.86f); close() }; drawPath(p,tint,style=stroke) }
            "play" -> { val p=Path().apply { moveTo(w*.25f,h*.12f); lineTo(w*.78f,h*.5f); lineTo(w*.25f,h*.88f); close() }; drawPath(p,tint) }
            "rewind", "forward" -> {
                val start=if(kind=="rewind") 35f else 215f
                drawArc(tint,start,290f,false,Offset(w*.12f,h*.12f),Size(w*.76f,h*.76f),style=stroke)
                if(kind=="rewind"){ line(.18f,.12f,.18f,.36f); line(.18f,.12f,.42f,.12f) }
                else { line(.82f,.88f,.82f,.64f); line(.82f,.88f,.58f,.88f) }
            }
            "remote" -> { drawRoundRect(tint,Offset(w*.25f,h*.08f),Size(w*.5f,h*.84f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(sw*2),style=stroke); drawCircle(tint,w*.5f,h*.30f,w*.045f) }
            "apps" -> { for (x in 0..1) for(y in 0..1) drawRoundRect(tint,Offset(w*(.12f+x*.42f),h*(.12f+y*.42f)),Size(w*.32f,h*.32f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(sw),style=stroke) }
            "cast" -> { drawArc(tint,180f,90f,false,Offset(w*.10f,h*.10f),Size(w*.80f,h*.80f),style=stroke); drawArc(tint,180f,90f,false,Offset(w*.10f,h*.42f),Size(w*.48f,h*.48f),style=stroke); drawCircle(tint,w*.18f,h*.82f,w*.07f) }
            "settings" -> { drawCircle(tint,w*.5f,h*.5f,w*.30f,style=stroke); drawCircle(tint,w*.5f,h*.5f,w*.10f) }
        }
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
    Surface(color = panelLight, shape = RoundedCornerShape(22.dp), modifier = modifier.height(170.dp)) {
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
        modifier = modifier.height(88.dp).clickable { onClick() }) {
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
