package com.reelog.tvremote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val bg = Color(0xFF030405)
private val panel = Color(0xFF111316)
private val panelLight = Color(0xFF25282D)
private val panelInner = Color(0xFF191B20)
private val accent = Color(0xFFF2F3F5)
private val muted = Color(0xFFB0B3BA)
private val white = Color(0xFFF5F7F6)

class MainActivity : ComponentActivity() {
    private lateinit var remote: SamsungRemote
    private lateinit var discovery: SamsungTvDiscovery

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.navigationBarColor = android.graphics.Color.rgb(5, 6, 7)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        val statusState = mutableStateOf("Busca televisores Samsung en tu red")
        val scanningState = mutableStateOf(false)
        val foundTvs = mutableStateListOf<SamsungTv>()
        remote = SamsungRemote(applicationContext) { message ->
            runOnUiThread { statusState.value = message }
        }
        discovery = SamsungTvDiscovery(applicationContext)
        AppUpdateScheduler.schedule(applicationContext)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4102)
        }
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
                    onConnect = { tv ->
                        remote.connect(tv.ip)
                        lifecycleScope.launch {
                            kotlinx.coroutines.delay(600)
                            val mac = tv.macAddress ?: discovery.resolveMacAddress(tv.ip)
                            if (mac != null) {
                                getSharedPreferences("tvremote_selected_tv", MODE_PRIVATE)
                                    .edit().putString("mac", mac).apply()
                            }
                        }
                    },
                    onKey = { remote.sendKey(it) },
                    onPower = { tv ->
                        if (remote.isConnected()) {
                            remote.sendKey("KEY_POWER")
                        } else if (tv != null) {
                            statusState.value = "Buscando la MAC guardada y enviando Wake-on-LAN…"
                            lifecycleScope.launch {
                                val prefs = getSharedPreferences("tvremote_selected_tv", MODE_PRIVATE)
                                val mac = tv.macAddress
                                    ?: prefs.getString("mac", null)
                                    ?: discovery.resolveMacAddress(tv.ip)
                                if (mac == null) {
                                    statusState.value = "No se pudo aprender la MAC. Enciende la TV y conéctala una vez a la app."
                                } else {
                                    prefs.edit().putString("mac", mac).apply()
                                    val result = WakeOnLan.wake(mac, tv.ip)
                                    statusState.value = if (result.isSuccess)
                                        "Wake-on-LAN enviado. Si no enciende, activa Encendido por red en la TV."
                                    else "No se pudo enviar Wake-on-LAN: " + (result.exceptionOrNull()?.localizedMessage ?: "error de red")
                                }
                            }
                        } else {
                            statusState.value = "Selecciona tu televisor una vez para activar el encendido automático."
                        }
                    },
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
    onKey: (String) -> Unit, onPower: (SamsungTv?) -> Unit,
    onText: (String) -> Unit, onForget: (SamsungTv) -> Unit,
    onDisconnect: () -> Unit
) {
    var typedText by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val tvPrefs = remember { context.getSharedPreferences("tvremote_selected_tv", android.content.Context.MODE_PRIVATE) }
    var selectedTv by remember {
        mutableStateOf(
            tvPrefs.getString("ip", null)?.let { ip ->
                SamsungTv(
                    ip,
                    tvPrefs.getString("name", "Samsung Smart TV") ?: "Samsung Smart TV",
                    tvPrefs.getString("model", "Modelo no disponible") ?: "Modelo no disponible",
                    tvPrefs.getString("mac", null)
                )
            }
        )
    }
    var showDevices by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        selectedTv?.let { onConnect(it) }
    }

    Surface(Modifier.fillMaxSize(), color = bg) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 42.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clickable { onPower(selectedTv) }, contentAlignment = Alignment.Center) {
                    RemoteIcon("power", Modifier.size(27.dp), white)
                }
                Spacer(Modifier.weight(1f))
                Text("TvRemote", color = white, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = (-.7).sp)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(42.dp).clickable { onKey("KEY_MUTE") }, contentAlignment = Alignment.Center) {
                    RemoteIcon("volume", Modifier.size(30.dp), white)
                }
            }
            Row(Modifier.fillMaxWidth().height(30.dp).clickable { showDevices = true },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                RemoteIcon("tv", Modifier.size(19.dp), muted)
                Spacer(Modifier.width(9.dp))
                Text(selectedTv?.model ?: "Seleccionar televisor", color = white,
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                RemoteIcon("down", Modifier.size(14.dp), muted)
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTile("prime", Modifier.weight(1f))
                AppTile("disney", Modifier.weight(1f))
                AppTile("netflix", Modifier.weight(1f))
            }

            Spacer(Modifier.height(18.dp))
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val dialSize = maxWidth * .80f
                Box(Modifier.size(dialSize), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.radialGradient(listOf(Color(0xFF34373C), Color(0xFF25282D), Color(0xFF1B1D22))),
                            CircleShape
                        ).border(1.dp, Color.White.copy(alpha = .10f), CircleShape)
                    )
                    Box(
                        Modifier.fillMaxSize(.46f).background(
                            Brush.radialGradient(listOf(Color(0xFF303338), Color(0xFF1B1D22))), CircleShape
                        ).border(1.dp, Color.White.copy(alpha = .10f), CircleShape)
                    )
                    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(dialSize * .32f)
                        .clickable { onKey("KEY_UP") }, contentAlignment = Alignment.Center) {
                        RemoteIcon("up", Modifier.size(25.dp), white)
                    }
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(dialSize * .32f)
                        .clickable { onKey("KEY_DOWN") }, contentAlignment = Alignment.Center) {
                        RemoteIcon("down", Modifier.size(25.dp), white)
                    }
                    Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(dialSize * .32f)
                        .clickable { onKey("KEY_LEFT") }, contentAlignment = Alignment.Center) {
                        RemoteIcon("left", Modifier.size(25.dp), white)
                    }
                    Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(dialSize * .32f)
                        .clickable { onKey("KEY_RIGHT") }, contentAlignment = Alignment.Center) {
                        RemoteIcon("right", Modifier.size(25.dp), white)
                    }
                    Box(Modifier.fillMaxSize(.46f).clickable { onKey("KEY_ENTER") },
                        contentAlignment = Alignment.Center) {
                        Text("OK", color = white, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SideControl("volume", Modifier.weight(.90f),
                    onUp = { onKey("KEY_VOLUP") }, onDown = { onKey("KEY_VOLDOWN") })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteTile("rewind", "10", Modifier.weight(1f)) { onKey("KEY_REWIND") }
                        RemoteTile("playpause", "", Modifier.weight(1f)) { onKey("KEY_PLAY") }
                        RemoteTile("forward", "10", Modifier.weight(1f)) { onKey("KEY_FF") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteTile("back", "", Modifier.weight(1f)) { onKey("KEY_RETURN") }
                        RemoteTile("home", "", Modifier.weight(1f)) { onKey("KEY_HOME") }
                    }
                }
                SideControl("channel", Modifier.weight(.90f),
                    onUp = { onKey("KEY_CHUP") }, onDown = { onKey("KEY_CHDOWN") })
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { i ->
                    Box(Modifier.size(9.dp).background(if (selectedTab == i) white else Color(0xFF526568), CircleShape))
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(70.dp).background(panel, RoundedCornerShape(36.dp)).border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(36.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                BottomItem("remote", "Mando", 0, selectedTab) { selectedTab = 0 }
                BottomItem("apps", "Apps", 1, selectedTab) { selectedTab = 1 }
                BottomItem("cast", "Transmitir", 2, selectedTab) { selectedTab = 2 }
                BottomItem("settings", "Ajustes", 3, selectedTab) { selectedTab = 3 }
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
                            onConnect = {
                                selectedTv = tv
                                val editor = tvPrefs.edit()
                                    .putString("ip", tv.ip)
                                    .putString("name", tv.name)
                                    .putString("model", tv.model)
                                if (tv.macAddress != null) {
                                    editor.putString("mac", tv.macAddress)
                                } else if (selectedTv?.ip != tv.ip) {
                                    editor.remove("mac")
                                }
                                editor.apply()
                                onConnect(tv)
                                showDevices = false
                            },
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
            "playpause" -> {
                val p=Path().apply { moveTo(w*.12f,h*.16f); lineTo(w*.52f,h*.5f); lineTo(w*.12f,h*.84f); close() }
                drawPath(p,tint)
                drawLine(tint,Offset(w*.70f,h*.18f),Offset(w*.70f,h*.82f),sw*1.5f)
                drawLine(tint,Offset(w*.88f,h*.18f),Offset(w*.88f,h*.82f),sw*1.5f)
            }
            "rewind", "forward" -> {
                val start=if(kind=="rewind") 35f else 215f
                drawArc(tint,start,290f,false,Offset(w*.12f,h*.12f),Size(w*.76f,h*.76f),style=stroke)
                if(kind=="rewind"){ line(.18f,.12f,.18f,.36f); line(.18f,.12f,.42f,.12f) }
                else { line(.82f,.88f,.82f,.64f); line(.82f,.88f,.58f,.88f) }
            }
            "remote" -> { drawRoundRect(tint,Offset(w*.25f,h*.08f),Size(w*.5f,h*.84f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(sw*2),style=stroke); drawCircle(tint,w*.045f,Offset(w*.5f,h*.30f)) }
            "apps" -> { for (x in 0..1) for(y in 0..1) drawRoundRect(tint,Offset(w*(.12f+x*.42f),h*(.12f+y*.42f)),Size(w*.32f,h*.32f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(sw),style=stroke) }
            "cast" -> {
                drawRoundRect(tint, Offset(w*.12f,h*.12f), Size(w*.76f,h*.62f),
                    cornerRadius=androidx.compose.ui.geometry.CornerRadius(sw), style=stroke)
                drawArc(tint,180f,90f,false,Offset(w*.12f,h*.60f),Size(w*.30f,h*.30f),style=stroke)
                drawArc(tint,180f,90f,false,Offset(w*.12f,h*.76f),Size(w*.14f,h*.14f),style=stroke)
                drawCircle(tint,w*.035f,Offset(w*.15f,h*.87f))
            }
            "settings" -> {
                drawCircle(tint,w*.29f,Offset(w*.5f,h*.5f),style=stroke)
                drawCircle(tint,w*.11f,Offset(w*.5f,h*.5f))
                for (i in 0..7) {
                    val a = i * (Math.PI / 4.0)
                    val x1 = .5f + kotlin.math.cos(a).toFloat()*.31f
                    val y1 = .5f + kotlin.math.sin(a).toFloat()*.31f
                    val x2 = .5f + kotlin.math.cos(a).toFloat()*.46f
                    val y2 = .5f + kotlin.math.sin(a).toFloat()*.46f
                    line(x1,y1,x2,y2)
                }
            }
        }
    }
}

@Composable
private fun AppTile(brand: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFF202328), shape = RoundedCornerShape(22.dp),
        modifier = modifier.height(58.dp).border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(22.dp))
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (brand) {
                "prime" -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("prime", color = Color(0xFF19A8E0), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Canvas(Modifier.width(52.dp).height(10.dp)) {
                        val smile = Path().apply {
                            moveTo(size.width * .08f, size.height * .18f)
                            cubicTo(size.width * .35f, size.height * 1.15f, size.width * .72f, size.height * .95f, size.width * .94f, size.height * .18f)
                        }
                        drawPath(smile, Color(0xFF19A8E0), style = Stroke(width = 2.dp.toPx()))
                    }
                }
                "disney" -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Canvas(Modifier.width(70.dp).height(8.dp)) {
                        drawArc(white, 190f, 160f, false, Offset(0f, -size.height * 1.25f),
                            Size(size.width, size.height * 2.5f), style = Stroke(width = 1.6.dp.toPx()))
                    }
                    Text("Disney+", color = white, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = (-.7).sp)
                }
                "netflix" -> Text("NETFLIX", color = Color(0xFFE50914), fontSize = 19.sp,
                    fontWeight = FontWeight.Black, letterSpacing = (-1.1).sp)
            }
        }
    }
}

@Composable
private fun SideControl(kind: String, modifier: Modifier, onUp: () -> Unit, onDown: () -> Unit) {
    Surface(
        color = Color(0xFF202328), shape = RoundedCornerShape(25.dp),
        modifier = modifier.height(158.dp)
            .border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(25.dp))
    ) {
        Column(Modifier.fillMaxSize().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            Box(Modifier.fillMaxWidth().weight(1f).clickable { onUp() }, contentAlignment = Alignment.Center) {
                if (kind == "volume") Text("+", color = white, fontSize = 32.sp, fontWeight = FontWeight.Light)
                else RemoteIcon("up", Modifier.size(25.dp), white)
            }
            RemoteIcon(if (kind == "volume") "volume" else "tv", Modifier.size(24.dp), white)
            Box(Modifier.fillMaxWidth().weight(1f).clickable { onDown() }, contentAlignment = Alignment.Center) {
                if (kind == "volume") Text("−", color = white, fontSize = 32.sp, fontWeight = FontWeight.Light)
                else RemoteIcon("down", Modifier.size(25.dp), white)
            }
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
    Surface(
        color = Color(0xFF202328), shape = RoundedCornerShape(21.dp),
        modifier = modifier.height(52.dp).border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(21.dp))
            .clickable { onClick() }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            RemoteIcon(symbol, Modifier.size(25.dp), white)
            if (caption.isNotEmpty()) Text(caption, color = muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BottomItem(icon: String, label: String, index: Int, selected: Int, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth(.25f).fillMaxHeight()
            .background(if (selected == index) Color(0xFF34383E) else Color.Transparent, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        RemoteIcon(icon, Modifier.size(25.dp), if (selected == index) white else Color(0xFFB9BEC5))
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
