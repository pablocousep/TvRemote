package com.reelog.tvremote

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Design tokens. The layout is drawn on a 692 × 1490 "design pixel" canvas that
// matches the reference mock-up and is scaled uniformly to the device screen.
// ---------------------------------------------------------------------------

private const val DESIGN_W = 692f
private const val DESIGN_H = 1490f

private val Bg = Color(0xFF000000)
private val SurfaceTop = Color(0xFF2A2B2F)
private val SurfaceBottom = Color(0xFF18191C)
private val NavBg = Color(0xFF141517)
private val Ink = Color(0xFFF4F4F6)
private val InkMuted = Color(0xFFA3A5AA)
private val DotOff = Color(0xFF4A4B4F)
private val PrimeBlue = Color(0xFF1FA6E0)
private val NetflixRed = Color(0xFFE50914)
private val OnlineGreen = Color(0xFF3DDC84)

/** Converts design pixels of the mock-up into real dp / sp for this screen. */
@Stable
private class Ds(val s: Float, private val fontScale: Float) {
    fun dp(px: Number): Dp = (px.toFloat() * s).dp
    fun sp(px: Number): TextUnit = (px.toFloat() * s / fontScale).sp
}

private val LocalDs = staticCompositionLocalOf { Ds(0.5f, 1f) }
private val LocalHaptics = staticCompositionLocalOf { true }

private fun Modifier.at(d: Ds, x: Number, y: Number, w: Number, h: Number) =
    this.offset(d.dp(x), d.dp(y)).size(d.dp(w), d.dp(h))

private fun View.tick(enabled: Boolean) {
    if (enabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}

data class TvApp(val id: String, val name: String)

val TvApps = listOf(
    TvApp("3201910019365", "Prime Video"),
    TvApp("3201901017640", "Disney+"),
    TvApp("3201907018807", "Netflix"),
    TvApp("111299001912", "YouTube"),
    TvApp("3201807016597", "Apple TV"),
    TvApp("3201606009684", "Spotify"),
    TvApp("3202301029760", "Max"),
    TvApp("3201512006963", "Navegador")
)

class RemoteActions(
    val key: (String) -> Unit,
    val text: (String) -> Unit,
    val launchApp: (String) -> Unit,
    val power: () -> Unit,
    val discover: () -> Unit,
    val connect: (SamsungTv) -> Unit,
    val forget: (SamsungTv) -> Unit,
    val disconnect: () -> Unit,
    val openCastSettings: () -> Unit,
    val setHaptics: (Boolean) -> Unit
)

// ---------------------------------------------------------------------------
// Root screen
// ---------------------------------------------------------------------------

@Composable
fun RemoteScreen(
    status: String,
    statusSerial: Int,
    connected: Boolean,
    scanning: Boolean,
    tvs: List<SamsungTv>,
    selectedTv: SamsungTv?,
    haptics: Boolean,
    actions: RemoteActions
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val scale = min(maxWidth.value / DESIGN_W, maxHeight.value / DESIGN_H)
        val d = Ds(scale, LocalDensity.current.fontScale)
        var tab by remember { mutableIntStateOf(0) }
        var showPicker by remember { mutableStateOf(false) }
        val pager = rememberPagerState { 3 }

        CompositionLocalProvider(LocalDs provides d, LocalHaptics provides haptics) {
            Box(Modifier.align(Alignment.Center).size(d.dp(DESIGN_W), d.dp(DESIGN_H))) {
                Header(selectedTv, connected, actions, onPickTv = { showPicker = true })

                when (tab) {
                    0 -> {
                        HorizontalPager(pager, Modifier.at(d, 0, 225, DESIGN_W, 1015)) { page ->
                            when (page) {
                                0 -> MainPage(actions)
                                1 -> NumbersPage(actions)
                                else -> TouchPage(actions)
                            }
                        }
                        PageDots(pager.currentPage)
                    }
                    1 -> Box(Modifier.at(d, 0, 225, DESIGN_W, 1085)) { AppsTab(actions) }
                    2 -> Box(Modifier.at(d, 0, 225, DESIGN_W, 1085)) { CastTab(actions) }
                    else -> Box(Modifier.at(d, 0, 225, DESIGN_W, 1085)) {
                        SettingsTab(status, connected, scanning, tvs, selectedTv, haptics, actions)
                    }
                }

                StatusToast(status, statusSerial)
                BottomNav(tab) { tab = it }
            }
        }

        if (showPicker) {
            CompositionLocalProvider(LocalDs provides d, LocalHaptics provides haptics) {
                TvPickerDialog(status, scanning, tvs, selectedTv, actions) { showPicker = false }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header: power · title + TV model · mute
// ---------------------------------------------------------------------------

@Composable
private fun Header(tv: SamsungTv?, connected: Boolean, actions: RemoteActions, onPickTv: () -> Unit) {
    val d = LocalDs.current
    RoundButton(Modifier.at(d, 43, 93, 90, 90), onClick = actions.power) {
        Icon("power", Modifier.size(d.dp(40)))
    }
    RoundButton(Modifier.at(d, 561, 94, 88, 88), onClick = { actions.key("KEY_MUTE") }) {
        Icon("speaker", Modifier.size(d.dp(46)))
    }
    Text(
        "TvRemote",
        modifier = Modifier.at(d, 140, 78, 412, 60),
        color = Ink, fontSize = d.sp(46), fontWeight = FontWeight.Bold,
        letterSpacing = d.sp(-0.6), textAlign = TextAlign.Center, maxLines = 1
    )
    Row(
        Modifier.offset(y = d.dp(144)).fillMaxWidth().height(d.dp(34)),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(d.dp(17))).pressable(onClick = onPickTv)
                .padding(horizontal = d.dp(10)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Icon("tv", Modifier.size(d.dp(30), d.dp(26)), Ink.copy(alpha = .85f))
                if (connected) Box(
                    Modifier.align(Alignment.TopEnd).offset(d.dp(5), d.dp(-3)).size(d.dp(9))
                        .background(OnlineGreen, CircleShape).border(d.dp(2), Bg, CircleShape)
                )
            }
            Spacer(Modifier.width(d.dp(14)))
            Text(
                tv?.model?.takeIf { it.isNotBlank() && !it.startsWith("Modelo") } ?: tv?.name ?: "Seleccionar TV",
                color = Ink, fontSize = d.sp(25), fontWeight = FontWeight.SemiBold,
                letterSpacing = d.sp(0.3), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = d.dp(420))
            )
            Spacer(Modifier.width(d.dp(16)))
            Icon("chevronDown", Modifier.size(d.dp(20), d.dp(12)), Ink.copy(alpha = .85f))
        }
    }
}

// ---------------------------------------------------------------------------
// Page 1 — the main remote from the mock-up
// ---------------------------------------------------------------------------

@Composable
private fun MainPage(actions: RemoteActions) {
    val d = LocalDs.current
    var playing by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        BrandTile("prime", Modifier.at(d, 46, 16, 184, 100)) { actions.launchApp(TvApps[0].id) }
        BrandTile("disney", Modifier.at(d, 254, 16, 184, 100)) { actions.launchApp(TvApps[1].id) }
        BrandTile("netflix", Modifier.at(d, 462, 16, 184, 100)) { actions.launchApp(TvApps[2].id) }

        DPad(Modifier.at(d, 86, 161, 520, 520), actions.key)

        RockerPill(Modifier.at(d, 46, 706, 118, 285), "volume",
            onUp = { actions.key("KEY_VOLUP") }, onDown = { actions.key("KEY_VOLDOWN") })
        RockerPill(Modifier.at(d, 528, 706, 118, 285), "channel",
            onUp = { actions.key("KEY_CHUP") }, onDown = { actions.key("KEY_CHDOWN") })

        GlassButton(Modifier.at(d, 186, 743, 92, 93), RoundedCornerShape(d.dp(24)), repeat = true,
            onClick = { actions.key("KEY_REWIND") }) { SkipIcon(forward = false) }
        GlassButton(Modifier.at(d, 300, 743, 92, 93), RoundedCornerShape(d.dp(24)),
            onClick = {
                actions.key(if (playing) "KEY_PAUSE" else "KEY_PLAY")
                playing = !playing
            }) { Icon("playPause", Modifier.size(d.dp(44), d.dp(30))) }
        GlassButton(Modifier.at(d, 414, 743, 92, 93), RoundedCornerShape(d.dp(24)), repeat = true,
            onClick = { actions.key("KEY_FF") }) { SkipIcon(forward = true) }

        GlassButton(Modifier.at(d, 192, 873, 143, 102), RoundedCornerShape(d.dp(26)),
            onClick = { actions.key("KEY_RETURN") }) { Icon("back", Modifier.size(d.dp(38), d.dp(32))) }
        GlassButton(Modifier.at(d, 358, 873, 143, 102), RoundedCornerShape(d.dp(26)),
            onClick = { actions.key("KEY_HOME") }) { Icon("home", Modifier.size(d.dp(38))) }
    }
}

@Composable
private fun SkipIcon(forward: Boolean) {
    val d = LocalDs.current
    Box(Modifier.size(d.dp(42)).scale(if (forward) -1f else 1f, 1f), contentAlignment = Alignment.Center) {
        Icon("skip", Modifier.fillMaxSize())
    }
    Text("10", color = Ink, fontSize = d.sp(14), fontWeight = FontWeight.Bold, letterSpacing = d.sp(-0.5))
}

@Composable
private fun DPad(modifier: Modifier, onKey: (String) -> Unit) {
    val d = LocalDs.current
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val key by rememberUpdatedState(onKey)
    var pressed by remember { mutableStateOf<String?>(null) }

    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val r = size.width / 2f
                val dx = down.position.x - r
                val dy = down.position.y - size.height / 2f
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist > r) return@awaitEachGesture
                val zone = when {
                    dist < r * 0.44f -> "KEY_ENTER"
                    abs(dx) > abs(dy) -> if (dx < 0) "KEY_LEFT" else "KEY_RIGHT"
                    else -> if (dy < 0) "KEY_UP" else "KEY_DOWN"
                }
                pressed = zone
                var fired = false
                val repeater = if (zone != "KEY_ENTER") scope.launch {
                    delay(420)
                    while (true) { fired = true; view.tick(haptics); key(zone); delay(120) }
                } else null
                val up = waitForUpOrCancellation()
                repeater?.cancel()
                pressed = null
                if (up != null && !fired) { up.consume(); view.tick(haptics); key(zone) }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        // Outer ring with soft top-left light.
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFF3D3E42), Color(0xFF2C2D31), Color(0xFF1F2023)),
                    center = Offset(size.width * .40f, size.height * .30f), radius = r * 1.35f
                ), r
            )
            val sweep = when (pressed) {
                "KEY_RIGHT" -> -45f; "KEY_DOWN" -> 45f; "KEY_LEFT" -> 135f; "KEY_UP" -> 225f; else -> null
            }
            if (sweep != null) drawArc(Color.White.copy(alpha = .07f), sweep, 90f, true)
            drawCircle(
                Brush.verticalGradient(listOf(Color.White.copy(.20f), Color.White.copy(.03f), Color.White.copy(.07f))),
                r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx())
            )
            // Shadow cast by the raised OK button.
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.Black.copy(.55f), Color.Black.copy(.25f), Color.Transparent),
                    center = Offset(center.x, center.y + r * .03f), radius = r * .58f
                ), r * .58f, center = Offset(center.x, center.y + r * .03f)
            )
        }
        Icon("chevronUp", Modifier.align(Alignment.TopCenter).padding(top = d.dp(70)).size(d.dp(38), d.dp(20)))
        Icon("chevronDown", Modifier.align(Alignment.BottomCenter).padding(bottom = d.dp(70)).size(d.dp(38), d.dp(20)))
        Icon("chevronLeft", Modifier.align(Alignment.CenterStart).padding(start = d.dp(66)).size(d.dp(20), d.dp(38)))
        Icon("chevronRight", Modifier.align(Alignment.CenterEnd).padding(end = d.dp(66)).size(d.dp(20), d.dp(38)))

        // Raised OK button.
        Box(Modifier.size(d.dp(228)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFF34353A), Color(0xFF2A2B2F), Color(0xFF222326)),
                        center = Offset(size.width * .42f, size.height * .32f), radius = r * 1.3f
                    ), r
                )
                if (pressed == "KEY_ENTER") drawCircle(Color.White.copy(.07f), r)
                drawCircle(
                    Brush.verticalGradient(listOf(Color.White.copy(.18f), Color.White.copy(.02f), Color.White.copy(.05f))),
                    r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx())
                )
            }
            Text("OK", color = Ink, fontSize = d.sp(40), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RockerPill(modifier: Modifier, kind: String, onUp: () -> Unit, onDown: () -> Unit) {
    val d = LocalDs.current
    val shape = RoundedCornerShape(d.dp(44))
    var upPressed by remember { mutableStateOf(false) }
    var downPressed by remember { mutableStateOf(false) }
    Column(modifier.glass(shape), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .background(if (upPressed) Color.White.copy(.07f) else Color.Transparent)
                .pressable(repeat = true, onPressedChange = { upPressed = it }, onClick = onUp),
            contentAlignment = Alignment.Center
        ) {
            if (kind == "volume") Icon("plus", Modifier.size(d.dp(36)))
            else Icon("chevronUp", Modifier.size(d.dp(36), d.dp(19)))
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (kind == "volume") Icon("speakerSmall", Modifier.size(d.dp(32)))
            else Icon("tv", Modifier.size(d.dp(34), d.dp(30)))
        }
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .background(if (downPressed) Color.White.copy(.07f) else Color.Transparent)
                .pressable(repeat = true, onPressedChange = { downPressed = it }, onClick = onDown),
            contentAlignment = Alignment.Center
        ) {
            if (kind == "volume") Icon("minus", Modifier.size(d.dp(36)))
            else Icon("chevronDown", Modifier.size(d.dp(36), d.dp(19)))
        }
    }
}

@Composable
private fun BrandTile(brand: String, modifier: Modifier, onClick: () -> Unit) {
    val d = LocalDs.current
    GlassButton(modifier, RoundedCornerShape(d.dp(24)), onClick = onClick) { BrandLogo(brand, 1f) }
}

@Composable
private fun BrandLogo(brand: String, k: Float) {
    val d = LocalDs.current
    when (brand) {
        "prime" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "prime", color = PrimeBlue, fontSize = d.sp(40 * k), fontWeight = FontWeight.Bold,
                letterSpacing = d.sp(-1 * k),
                modifier = Modifier.offset(y = d.dp(4 * k))
            )
            Canvas(Modifier.size(d.dp(84 * k), d.dp(16 * k)).offset(y = d.dp(-4 * k))) {
                val w = size.width; val h = size.height
                val sw = 2.6f * d.s * k * density
                val smile = Path().apply {
                    moveTo(w * .04f, h * .12f)
                    quadraticTo(w * .48f, h * 1.05f, w * .90f, h * .30f)
                }
                drawPath(smile, PrimeBlue, style = Stroke(sw, cap = StrokeCap.Round))
                val head = Path().apply {
                    moveTo(w * .80f, h * .18f); lineTo(w * .97f, h * .10f); lineTo(w * .93f, h * .62f)
                }
                drawPath(head, PrimeBlue, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        "disney" -> Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(d.dp(118 * k), d.dp(60 * k))) {
                val sw = 2.2f * d.s * k * density
                drawArc(
                    Brush.horizontalGradient(listOf(Color(0xFF0E3B8C), Color(0xFF3FA8F4), Color(0xFFE6F6FF))),
                    200f, 150f, false, Offset(size.width * .02f, size.height * .02f),
                    Size(size.width * .96f, size.height * 1.3f), style = Stroke(sw, cap = StrokeCap.Round)
                )
            }
            Text(
                "Disney+", color = Ink, fontSize = d.sp(32 * k), fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Cursive, letterSpacing = d.sp(-0.5 * k),
                modifier = Modifier.offset(y = d.dp(6 * k))
            )
        }
        "netflix" -> Text(
            "NETFLIX", color = NetflixRed, fontSize = d.sp(40 * k), fontWeight = FontWeight.Black,
            fontFamily = CondensedFont, letterSpacing = d.sp(-0.5 * k),
            style = TextStyle(textGeometricTransform = TextGeometricTransform(scaleX = .86f))
        )
        else -> Text(brand, color = Ink, fontSize = d.sp(28 * k), fontWeight = FontWeight.SemiBold)
    }
}

private val CondensedFont = FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))

// ---------------------------------------------------------------------------
// Page 2 — numbers, input source and colour keys
// ---------------------------------------------------------------------------

@Composable
private fun NumbersPage(actions: RemoteActions) {
    val d = LocalDs.current
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "LIST", "0", "PREV")
    Box(Modifier.fillMaxSize()) {
        keys.forEachIndexed { i, k ->
            val col = i % 3; val row = i / 3
            GlassButton(
                Modifier.at(d, 46 + col * 208, 16 + row * 124, 184, 104), RoundedCornerShape(d.dp(24)),
                onClick = {
                    actions.key(when (k) { "LIST" -> "KEY_CH_LIST"; "PREV" -> "KEY_PRECH"; else -> "KEY_$k" })
                }
            ) {
                when (k) {
                    "LIST" -> Label("Canales", 24)
                    "PREV" -> Label("Anterior", 24)
                    else -> Text(k, color = Ink, fontSize = d.sp(44), fontWeight = FontWeight.Medium)
                }
            }
        }
        val extras = listOf("Fuente" to "KEY_SOURCE", "Menú" to "KEY_MENU", "Info" to "KEY_INFO", "Guía" to "KEY_GUIDE")
        extras.forEachIndexed { i, (label, key) ->
            GlassButton(Modifier.at(d, 46 + i * 153, 524, 141, 88), RoundedCornerShape(d.dp(24)),
                onClick = { actions.key(key) }) { Label(label, 24) }
        }
        val colours = listOf(
            Color(0xFFE5484D) to "KEY_RED", Color(0xFF30A46C) to "KEY_GREEN",
            Color(0xFFF5D90A) to "KEY_YELLOW", Color(0xFF3E8EF7) to "KEY_CYAN"
        )
        colours.forEachIndexed { i, (c, key) ->
            GlassButton(Modifier.at(d, 46 + i * 153, 636, 141, 70), RoundedCornerShape(d.dp(24)),
                onClick = { actions.key(key) }) {
                Box(Modifier.size(d.dp(54), d.dp(12)).background(c, RoundedCornerShape(d.dp(6))))
            }
        }
        val bottom = listOf("Silencio" to "KEY_MUTE", "Salir" to "KEY_EXIT", "Ajustes TV" to "KEY_TOOLS")
        bottom.forEachIndexed { i, (label, key) ->
            GlassButton(Modifier.at(d, 46 + i * 208, 730, 184, 88), RoundedCornerShape(d.dp(24)),
                onClick = { actions.key(key) }) { Label(label, 24) }
        }
    }
}

// ---------------------------------------------------------------------------
// Page 3 — touchpad and keyboard
// ---------------------------------------------------------------------------

@Composable
private fun TouchPage(actions: RemoteActions) {
    val d = LocalDs.current
    val view = LocalView.current
    val haptics = LocalHaptics.current
    var text by remember { mutableStateOf("") }
    val key by rememberUpdatedState(actions.key)
    Box(Modifier.fillMaxSize()) {
        // Text field + send.
        Row(
            Modifier.at(d, 46, 16, 600, 100).glass(RoundedCornerShape(d.dp(24))).padding(start = d.dp(28), end = d.dp(10)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Label("Escribe y envía al televisor", 24, InkMuted)
                BasicTextField(
                    text, { text = it }, singleLine = true,
                    textStyle = TextStyle(color = Ink, fontSize = d.sp(26)),
                    cursorBrush = SolidColor(Ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (text.isNotEmpty()) { actions.text(text); text = "" } }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            GlassButton(Modifier.size(d.dp(140), d.dp(76)), RoundedCornerShape(d.dp(20)),
                onClick = { if (text.isNotEmpty()) { actions.text(text); text = "" } }) { Label("Enviar", 24) }
        }

        // Touchpad: swipe to move, tap for OK.
        Box(
            Modifier.at(d, 46, 146, 600, 600).glass(RoundedCornerShape(d.dp(44)))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { view.tick(haptics); key("KEY_ENTER") })
                }
                .pointerInput(Unit) {
                    val step = 46.dp.toPx()
                    var acc = Offset.Zero
                    detectDragGestures(onDragStart = { acc = Offset.Zero }) { change, drag ->
                        change.consume()
                        acc += drag
                        while (abs(acc.x) > step || abs(acc.y) > step) {
                            val k = if (abs(acc.x) > abs(acc.y)) {
                                val dir = if (acc.x > 0) "KEY_RIGHT" else "KEY_LEFT"
                                acc = acc.copy(x = acc.x - step * kotlin.math.sign(acc.x)); dir
                            } else {
                                val dir = if (acc.y > 0) "KEY_DOWN" else "KEY_UP"
                                acc = acc.copy(y = acc.y - step * kotlin.math.sign(acc.y)); dir
                            }
                            view.tick(haptics); key(k)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon("touch", Modifier.size(d.dp(64)), InkMuted)
                Spacer(Modifier.height(d.dp(18)))
                Label("Desliza para moverte · toca para OK", 22, InkMuted)
            }
        }

        val row = listOf("stop" to "KEY_STOP", "back" to "KEY_RETURN", "home" to "KEY_HOME", "exit" to "KEY_EXIT")
        row.forEachIndexed { i, (icon, k) ->
            GlassButton(Modifier.at(d, 46 + i * 153, 776, 141, 96), RoundedCornerShape(d.dp(24)),
                onClick = { actions.key(k) }) {
                if (icon == "exit") Label("Salir", 24) else Icon(icon, Modifier.size(d.dp(36)))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Other tabs
// ---------------------------------------------------------------------------

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    val d = LocalDs.current
    Column(Modifier.padding(start = d.dp(52), top = d.dp(20), bottom = d.dp(26))) {
        Text(title, color = Ink, fontSize = d.sp(40), fontWeight = FontWeight.Bold, letterSpacing = d.sp(-0.6))
        if (subtitle != null) {
            Spacer(Modifier.height(d.dp(6)))
            Label(subtitle, 22, InkMuted)
        }
    }
}

@Composable
private fun AppsTab(actions: RemoteActions) {
    val d = LocalDs.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("Aplicaciones", "Abre una app instalada en tu televisor")
        TvApps.chunked(2).forEach { pair ->
            Row(Modifier.padding(horizontal = d.dp(46)).padding(bottom = d.dp(24)),
                horizontalArrangement = Arrangement.spacedBy(d.dp(24))) {
                pair.forEach { app ->
                    GlassButton(Modifier.weight(1f).height(d.dp(150)), RoundedCornerShape(d.dp(28)),
                        onClick = { actions.launchApp(app.id) }) {
                        when (app.name) {
                            "Prime Video" -> BrandLogo("prime", 1.15f)
                            "Disney+" -> BrandLogo("disney", 1.15f)
                            "Netflix" -> BrandLogo("netflix", 1.15f)
                            "YouTube" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(d.dp(52), d.dp(36)).background(Color(0xFFFF0000), RoundedCornerShape(d.dp(10))),
                                    contentAlignment = Alignment.Center) { Icon("play", Modifier.size(d.dp(16))) }
                                Spacer(Modifier.width(d.dp(8)))
                                Text("YouTube", color = Ink, fontSize = d.sp(32), fontWeight = FontWeight.Bold, letterSpacing = d.sp(-1))
                            }
                            "Spotify" -> Text("Spotify", color = Color(0xFF1ED760), fontSize = d.sp(34), fontWeight = FontWeight.Bold)
                            "Max" -> Text("max", color = Color(0xFF6E8BFF), fontSize = d.sp(40), fontWeight = FontWeight.Black, letterSpacing = d.sp(-1))
                            else -> Text(app.name, color = Ink, fontSize = d.sp(30), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CastTab(actions: RemoteActions) {
    val d = LocalDs.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("Transmitir", "Duplica la pantalla o cambia la entrada")
        GlassButton(
            Modifier.padding(horizontal = d.dp(46)).fillMaxWidth().height(d.dp(170)),
            RoundedCornerShape(d.dp(32)), onClick = actions.openCastSettings
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = d.dp(36)), verticalAlignment = Alignment.CenterVertically) {
                Icon("cast", Modifier.size(d.dp(56)))
                Spacer(Modifier.width(d.dp(28)))
                Column {
                    Text("Duplicar pantalla", color = Ink, fontSize = d.sp(30), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(d.dp(6)))
                    Label("Abre Smart View / Enviar pantalla del móvil", 22, InkMuted)
                }
            }
        }
        Spacer(Modifier.height(d.dp(40)))
        Label("Entradas", 26, InkMuted, Modifier.padding(start = d.dp(52), bottom = d.dp(18)))
        val sources = listOf("Fuente" to "KEY_SOURCE", "TV" to "KEY_TV", "HDMI 1" to "KEY_HDMI1",
            "HDMI 2" to "KEY_HDMI2", "HDMI 3" to "KEY_HDMI3", "HDMI 4" to "KEY_HDMI4")
        sources.chunked(2).forEach { pair ->
            Row(Modifier.padding(horizontal = d.dp(46)).padding(bottom = d.dp(24)),
                horizontalArrangement = Arrangement.spacedBy(d.dp(24))) {
                pair.forEach { (label, key) ->
                    GlassButton(Modifier.weight(1f).height(d.dp(110)), RoundedCornerShape(d.dp(26)),
                        onClick = { actions.key(key) }) { Label(label, 28) }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    status: String, connected: Boolean, scanning: Boolean, tvs: List<SamsungTv>,
    selectedTv: SamsungTv?, haptics: Boolean, actions: RemoteActions
) {
    val d = LocalDs.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("Ajustes")
        Card {
            Label("Televisor", 22, InkMuted)
            Spacer(Modifier.height(d.dp(10)))
            Text(selectedTv?.name ?: "Ninguno seleccionado", color = Ink, fontSize = d.sp(30), fontWeight = FontWeight.SemiBold)
            if (selectedTv != null) Label("${selectedTv.model} · ${selectedTv.ip}", 22, InkMuted)
            Spacer(Modifier.height(d.dp(12)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(d.dp(14)).background(if (connected) OnlineGreen else DotOff, CircleShape))
                Spacer(Modifier.width(d.dp(10)))
                Label(if (connected) "Conectado" else "Sin conexión", 22, Ink)
            }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(d.dp(10)))
                Label(status, 21, InkMuted)
            }
            if (selectedTv != null) {
                Spacer(Modifier.height(d.dp(20)))
                Row(horizontalArrangement = Arrangement.spacedBy(d.dp(18))) {
                    PillButton(if (connected) "Reconectar" else "Conectar", Modifier.weight(1f)) { actions.connect(selectedTv) }
                    PillButton("Desconectar", Modifier.weight(1f), onClick = actions.disconnect)
                }
            }
        }
        Card { TvList(scanning, tvs, selectedTv, actions, onChosen = {}) }
        Card {
            Row(Modifier.fillMaxWidth().pressable { actions.setHaptics(!haptics) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Vibración al pulsar", color = Ink, fontSize = d.sp(28), fontWeight = FontWeight.Medium)
                    Label("Respuesta háptica en cada botón", 21, InkMuted)
                }
                Switch(haptics)
            }
        }
        Label(
            "TvRemote · Samsung Smart TV (Tizen 2016+)\nEl móvil y el televisor deben estar en la misma Wi-Fi.",
            20, InkMuted.copy(alpha = .7f), Modifier.padding(start = d.dp(52), top = d.dp(4), bottom = d.dp(40))
        )
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val d = LocalDs.current
    Column(
        Modifier.padding(horizontal = d.dp(46)).padding(bottom = d.dp(24)).fillMaxWidth()
            .glass(RoundedCornerShape(d.dp(32))).padding(d.dp(32)),
        content = content
    )
}

@Composable
private fun Switch(on: Boolean) {
    val d = LocalDs.current
    val x by animateFloatAsState(if (on) 1f else 0f, label = "switch")
    Box(
        Modifier.size(d.dp(84), d.dp(48))
            .background(if (on) Color(0xFF4A4C52) else Color(0xFF232427), CircleShape)
            .border(1.dp, Color.White.copy(.10f), CircleShape).padding(d.dp(5))
    ) {
        Box(Modifier.offset(x = d.dp(36 * x)).size(d.dp(38)).background(if (on) Ink else InkMuted, CircleShape))
    }
}

@Composable
private fun TvList(
    scanning: Boolean, tvs: List<SamsungTv>, selectedTv: SamsungTv?,
    actions: RemoteActions, onChosen: () -> Unit
) {
    val d = LocalDs.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Televisores en tu red", color = Ink, fontSize = d.sp(28), fontWeight = FontWeight.Medium)
            Label(if (scanning) "Buscando…" else "${tvs.size} encontrado(s)", 21, InkMuted)
        }
        PillButton(if (scanning) "Buscando…" else "Buscar", Modifier.width(d.dp(180)), onClick = actions.discover)
    }
    tvs.forEach { tv ->
        Spacer(Modifier.height(d.dp(16)))
        val selected = selectedTv?.ip == tv.ip
        Row(
            Modifier.fillMaxWidth()
                .background(if (selected) Color.White.copy(.07f) else Color.White.copy(.03f), RoundedCornerShape(d.dp(22)))
                .border(1.dp, Color.White.copy(if (selected) .14f else .05f), RoundedCornerShape(d.dp(22)))
                .padding(d.dp(20)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon("tv", Modifier.size(d.dp(34), d.dp(30)), if (selected) Ink else InkMuted)
            Spacer(Modifier.width(d.dp(18)))
            Column(Modifier.weight(1f)) {
                Text(tv.name, color = Ink, fontSize = d.sp(25), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Label("${tv.model} · ${tv.ip}", 20, InkMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                PillButton("Conectar", Modifier.width(d.dp(150))) { actions.connect(tv); onChosen() }
                Text(
                    "Olvidar", color = InkMuted, fontSize = d.sp(20),
                    modifier = Modifier.padding(top = d.dp(8)).pressable { actions.forget(tv) }.padding(d.dp(6))
                )
            }
        }
    }
}

@Composable
private fun TvPickerDialog(
    status: String, scanning: Boolean, tvs: List<SamsungTv>, selectedTv: SamsungTv?,
    actions: RemoteActions, onDismiss: () -> Unit
) {
    val d = LocalDs.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(Color(0xFF111214), RoundedCornerShape(d.dp(40)))
                .border(1.dp, Color.White.copy(.10f), RoundedCornerShape(d.dp(40)))
                .padding(d.dp(36)).verticalScroll(rememberScrollState())
        ) {
            Text("Elegir televisor", color = Ink, fontSize = d.sp(36), fontWeight = FontWeight.Bold)
            if (status.isNotBlank()) {
                Spacer(Modifier.height(d.dp(8)))
                Label(status, 21, InkMuted)
            }
            Spacer(Modifier.height(d.dp(28)))
            TvList(scanning, tvs, selectedTv, actions, onChosen = onDismiss)
            Spacer(Modifier.height(d.dp(28)))
            PillButton("Cerrar", Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom bar, page dots, status toast
// ---------------------------------------------------------------------------

@Composable
private fun PageDots(current: Int) {
    val d = LocalDs.current
    repeat(3) { i ->
        Box(
            Modifier.at(d, 302 + i * 38.5f, 1257, 13, 13)
                .background(if (i == current) Ink else DotOff, CircleShape)
        )
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val d = LocalDs.current
    val shape = RoundedCornerShape(d.dp(53))
    Box(
        Modifier.at(d, 35, 1326, 623, 105)
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1C1F), NavBg)), shape)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(.14f), Color.White.copy(.04f))), shape)
    ) {
        val icons = listOf("remote", "apps", "cast", "settings")
        icons.forEachIndexed { i, icon ->
            val slot = 623f / 4f
            Box(
                Modifier.at(d, i * slot, 0, slot, 105).pressable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                if (i == selected) Box(
                    Modifier.size(d.dp(133), d.dp(72))
                        .background(Brush.verticalGradient(listOf(Color(0xFF34353A), Color(0xFF26272B))), CircleShape)
                        .border(1.dp, Color.White.copy(.08f), CircleShape)
                )
                val iconSize = when (icon) { "remote" -> d.dp(34); "settings" -> d.dp(38); else -> d.dp(36) }
                Icon(icon, Modifier.size(iconSize), if (i == selected) Ink else Ink.copy(alpha = .88f))
            }
        }
    }
}

@Composable
private fun StatusToast(status: String, serial: Int) {
    val d = LocalDs.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(serial) {
        if (serial > 0 && status.isNotBlank()) {
            visible = true
            delay(3600)
            visible = false
        }
    }
    Box(Modifier.at(d, 46, 1212, 600, 104), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Text(
                status, color = Ink, fontSize = d.sp(21), textAlign = TextAlign.Center, maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.background(Color(0xFF1E1F22), RoundedCornerShape(d.dp(26)))
                    .border(1.dp, Color.White.copy(.10f), RoundedCornerShape(d.dp(26)))
                    .padding(horizontal = d.dp(28), vertical = d.dp(14))
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------

@Composable
private fun Label(text: String, px: Number, color: Color = Ink, modifier: Modifier = Modifier) {
    val d = LocalDs.current
    Text(text, color = color, fontSize = d.sp(px), fontWeight = FontWeight.Medium, modifier = modifier)
}

/** Dark "glass" surface: lighter top, darker bottom, crisp highlight on the upper edge. */
private fun Modifier.glass(shape: Shape, pressed: Boolean = false) =
    this.clip(shape)
        .background(Brush.verticalGradient(listOf(SurfaceTop, SurfaceBottom)))
        .background(if (pressed) Color.White.copy(.07f) else Color.Transparent)
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(.20f), Color.White.copy(.04f), Color.White.copy(.07f))), shape)

/**
 * Fires on release (so swiping the pager never presses a key). With [repeat], holding the
 * button keeps firing, like a physical remote.
 */
@Composable
private fun Modifier.pressable(
    repeat: Boolean = false,
    onPressedChange: (Boolean) -> Unit = {},
    onClick: () -> Unit
): Modifier {
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val click by rememberUpdatedState(onClick)
    val pressedCb by rememberUpdatedState(onPressedChange)
    return this.pointerInput(repeat, haptics) {
        awaitEachGesture {
            awaitFirstDown()
            pressedCb(true)
            var fired = false
            val job = if (repeat) scope.launch {
                delay(420)
                while (true) { fired = true; view.tick(haptics); click(); delay(120) }
            } else null
            val up = waitForUpOrCancellation()
            job?.cancel()
            pressedCb(false)
            if (up != null && !fired) { up.consume(); view.tick(haptics); click() }
        }
    }
}

@Composable
private fun GlassButton(
    modifier: Modifier, shape: Shape, repeat: Boolean = false,
    onClick: () -> Unit, content: @Composable BoxScope.() -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val k by animateFloatAsState(if (pressed) .96f else 1f, label = "press")
    Box(
        modifier.scale(k).glass(shape, pressed)
            .pressable(repeat, onPressedChange = { pressed = it }, onClick = onClick),
        contentAlignment = Alignment.Center, content = content
    )
}

@Composable
private fun RoundButton(modifier: Modifier, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val k by animateFloatAsState(if (pressed) .94f else 1f, label = "press")
    Box(
        modifier.scale(k).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF26272A), Color(0xFF1A1B1D))))
            .background(if (pressed) Color.White.copy(.08f) else Color.Transparent)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(.12f), Color.White.copy(.03f))), CircleShape)
            .pressable(onPressedChange = { pressed = it }, onClick = onClick),
        contentAlignment = Alignment.Center, content = content
    )
}

@Composable
private fun PillButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val d = LocalDs.current
    GlassButton(modifier.height(d.dp(76)), RoundedCornerShape(d.dp(38)), onClick = onClick) { Label(label, 23) }
}

// ---------------------------------------------------------------------------
// Line icons drawn to match the mock-up (thin, rounded strokes).
// ---------------------------------------------------------------------------

@Composable
private fun Icon(kind: String, modifier: Modifier = Modifier, tint: Color = Ink) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val m = minOf(w, h)
        val sw = when (kind) {
            "chevronUp", "chevronDown" -> h * .17f
            "chevronLeft", "chevronRight" -> w * .17f
            "plus", "minus" -> m * .085f
            else -> m * .085f
        }.coerceAtLeast(1.4.dp.toPx())
        val stroke = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * w, y * h)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, p(x1, y1), p(x2, y2), sw, StrokeCap.Round)
        fun poly(vararg pts: Pair<Float, Float>, close: Boolean = false, fill: Boolean = false) {
            val path = Path().apply {
                moveTo(pts[0].first * w, pts[0].second * h)
                for (i in 1 until pts.size) lineTo(pts[i].first * w, pts[i].second * h)
                if (close) close()
            }
            if (fill) drawPath(path, tint) else drawPath(path, tint, style = stroke)
        }
        when (kind) {
            "power" -> {
                drawArc(tint, -60f, 300f, false, p(.13f, .17f), Size(w * .74f, h * .74f), style = stroke)
                line(.5f, .06f, .5f, .46f)
            }
            "speaker", "speakerSmall" -> {
                poly(.08f to .38f, .26f to .38f, .50f to .16f, .50f to .84f, .26f to .62f, .08f to .62f, close = true, fill = true)
                poly(.08f to .38f, .26f to .38f, .50f to .16f, .50f to .84f, .26f to .62f, .08f to .62f, close = true)
                if (kind == "speaker") {
                    drawArc(tint, -50f, 100f, false, p(.44f, .30f), Size(w * .26f, h * .40f), style = stroke)
                    drawArc(tint, -55f, 110f, false, p(.42f, .12f), Size(w * .50f, h * .76f), style = stroke)
                } else {
                    drawArc(tint, -55f, 110f, false, p(.42f, .26f), Size(w * .36f, h * .48f), style = stroke)
                }
            }
            "chevronUp" -> poly(.06f to .88f, .5f to .14f, .94f to .88f)
            "chevronDown" -> poly(.06f to .12f, .5f to .86f, .94f to .12f)
            "chevronLeft" -> poly(.88f to .06f, .14f to .5f, .88f to .94f)
            "chevronRight" -> poly(.12f to .06f, .86f to .5f, .12f to .94f)
            "plus" -> { line(.08f, .5f, .92f, .5f); line(.5f, .08f, .5f, .92f) }
            "minus" -> line(.08f, .5f, .92f, .5f)
            "tv" -> {
                drawRoundRect(tint, p(.05f, .06f), Size(w * .90f, h * .66f), CornerRadius(m * .12f), style = stroke)
                line(.30f, .92f, .70f, .92f)
            }
            "skip" -> {
                val r = m * .40f
                drawArc(tint, -78f, 300f, false, Offset(w / 2 - r, h / 2 - r), Size(r * 2, r * 2), style = stroke)
                val a = Math.toRadians(-78.0)
                val tip = Offset(w / 2 + r * cos(a).toFloat(), h / 2 + r * sin(a).toFloat())
                val head = Path().apply {
                    moveTo(tip.x - m * .16f, tip.y)
                    lineTo(tip.x + m * .02f, tip.y - m * .11f)
                    lineTo(tip.x + m * .02f, tip.y + m * .11f)
                    close()
                }
                drawPath(head, tint)
            }
            "playPause" -> {
                poly(.04f to .04f, .46f to .5f, .04f to .96f, close = true, fill = true)
                drawRoundRect(tint, p(.60f, .04f), Size(w * .12f, h * .92f), CornerRadius(w * .03f))
                drawRoundRect(tint, p(.84f, .04f), Size(w * .12f, h * .92f), CornerRadius(w * .03f))
            }
            "play" -> poly(.15f to .05f, .95f to .5f, .15f to .95f, close = true, fill = true)
            "stop" -> drawRoundRect(tint, p(.2f, .2f), Size(w * .6f, h * .6f), CornerRadius(m * .08f))
            "back" -> { line(.06f, .5f, .94f, .5f); poly(.42f to .12f, .06f to .5f, .42f to .88f) }
            "home" -> poly(.10f to .44f, .5f to .08f, .90f to .44f, .90f to .92f, .10f to .92f, close = true)
            "remote" -> {
                val body = Size(w * .46f, h * .94f)
                drawRoundRect(tint, p(.27f, .03f), body, CornerRadius(w * .12f))
                val hole = Color(0xFF1C1D20)
                drawCircle(hole, m * .07f, p(.5f, .20f))
                for (r in 0..2) for (c in 0..1) drawCircle(hole, m * .05f, p(.41f + c * .18f, .46f + r * .15f))
            }
            "apps" -> {
                val s = .36f
                for (x in 0..1) for (y in 0..1) drawRoundRect(
                    tint, p(.07f + x * .50f, .07f + y * .50f), Size(w * s, h * s), CornerRadius(m * .10f), style = stroke
                )
            }
            "cast" -> {
                poly(.08f to .36f, .08f to .14f, .92f to .14f, .92f to .84f, .54f to .84f)
                drawArc(tint, 270f, 90f, false, Offset(w * (.08f - .40f), h * (.88f - .40f)), Size(w * .80f, h * .80f), style = stroke)
                drawArc(tint, 270f, 90f, false, Offset(w * (.08f - .22f), h * (.88f - .22f)), Size(w * .44f, h * .44f), style = stroke)
                drawCircle(tint, m * .06f, p(.11f, .85f))
            }
            "settings" -> {
                val c = Offset(w / 2, h / 2)
                val outer = m * .46f; val inner = m * .35f
                val path = Path()
                for (i in 0 until 8) {
                    val a = i * 45.0
                    fun pt(rad: Float, deg: Double) = Offset(
                        c.x + rad * cos(Math.toRadians(deg)).toFloat(), c.y + rad * sin(Math.toRadians(deg)).toFloat()
                    )
                    val p1 = pt(inner, a - 15); val p2 = pt(outer, a - 9); val p3 = pt(outer, a + 9); val p4 = pt(inner, a + 15)
                    if (i == 0) path.moveTo(p1.x, p1.y) else path.lineTo(p1.x, p1.y)
                    path.lineTo(p2.x, p2.y); path.lineTo(p3.x, p3.y); path.lineTo(p4.x, p4.y)
                }
                path.close()
                drawPath(path, tint, style = stroke)
                drawCircle(tint, m * .14f, c, style = stroke)
            }
            "touch" -> {
                drawCircle(tint, m * .12f, p(.5f, .32f), style = stroke)
                drawCircle(tint, m * .26f, p(.5f, .32f), style = Stroke(sw * .7f))
                line(.5f, .58f, .5f, .92f)
            }
        }
    }
}
