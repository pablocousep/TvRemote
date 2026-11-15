package com.reelog.tvremote

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick as semanticsClick
import androidx.compose.ui.semantics.onLongClick as semanticsLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.painterResource
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

/** Colours for one theme. The dark one is the reference mock-up. */
@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color, val surfaceTop: Color, val surfaceBottom: Color,
    val navTop: Color, val navBg: Color, val pillTop: Color, val pillBottom: Color,
    val ink: Color, val inkMuted: Color, val dotOff: Color,
    /** Tint for highlights, hairlines and pressed overlays (white on dark, black on light). */
    val fg: Color,
    val dial: List<Color>, val ok: List<Color>, val round: List<Color>,
    val shadow: Float, val dialogBg: Color, val toastBg: Color,
    val switchOn: Color, val switchOff: Color
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF000000), surfaceTop = Color(0xFF2A2B2F), surfaceBottom = Color(0xFF18191C),
    navTop = Color(0xFF1B1C1F), navBg = Color(0xFF141517), pillTop = Color(0xFF36373C), pillBottom = Color(0xFF26272B),
    ink = Color(0xFFF4F4F6), inkMuted = Color(0xFFA3A5AA), dotOff = Color(0xFF4A4B4F), fg = Color.White,
    dial = listOf(Color(0xFF3D3E42), Color(0xFF2C2D31), Color(0xFF1F2023)),
    ok = listOf(Color(0xFF34353A), Color(0xFF2A2B2F), Color(0xFF222326)),
    round = listOf(Color(0xFF26272A), Color(0xFF1A1B1D)),
    shadow = .55f, dialogBg = Color(0xFF111214), toastBg = Color(0xFF1E1F22),
    switchOn = Color(0xFF4A4C52), switchOff = Color(0xFF232427)
)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFF1F2F5), surfaceTop = Color(0xFFFFFFFF), surfaceBottom = Color(0xFFE7E9ED),
    navTop = Color(0xFFFFFFFF), navBg = Color(0xFFECEEF1), pillTop = Color(0xFFE3E5E9), pillBottom = Color(0xFFD5D8DD),
    ink = Color(0xFF15171A), inkMuted = Color(0xFF686C73), dotOff = Color(0xFFC6C9CF), fg = Color.Black,
    dial = listOf(Color(0xFFFFFFFF), Color(0xFFF0F1F4), Color(0xFFDCDFE4)),
    ok = listOf(Color(0xFFFFFFFF), Color(0xFFF4F5F7), Color(0xFFE6E8EC)),
    round = listOf(Color(0xFFFFFFFF), Color(0xFFE8EAEE)),
    shadow = .16f, dialogBg = Color(0xFFFFFFFF), toastBg = Color(0xFFFFFFFF),
    switchOn = Color(0xFFBFC3CA), switchOff = Color(0xFFE2E4E8)
)

private val LocalPalette = staticCompositionLocalOf { DarkPalette }
private val pal: Palette @Composable @ReadOnlyComposable get() = LocalPalette.current
private val Bg: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.bg
private val Ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
private val InkMuted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.inkMuted
private val DotOff: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.dotOff
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

private fun Modifier.offset(shift: Offset) = this.offset(shift.x.dp, shift.y.dp)

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
    val setHaptics: (Boolean) -> Unit,
    val probeTv: () -> Unit = {},
    val wakeTest: () -> Unit = {},
    val saveMac: (String) -> Unit = {},
    val clearLog: () -> Unit = {},
    val reconnect: () -> Unit = {},
    val togglePin: (TvApp) -> Unit = {},
    val setHardwareVolume: (Boolean) -> Unit = {},
    val setTheme: (String) -> Unit = {},
    val saveSmartThingsToken: (String) -> Unit = {},
    val chooseSmartThingsDevice: (StDevice) -> Unit = {}
)

/** Snapshot of everything the screen shows. */
data class RemoteUiState(
    val status: String = "",
    val statusSerial: Int = 0,
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val connectionLost: Boolean = false,
    val scanning: Boolean = false,
    val tvs: List<SamsungTv> = emptyList(),
    val selectedTv: SamsungTv? = null,
    val haptics: Boolean = true,
    val hardwareVolume: Boolean = true,
    val theme: String = "dark",
    val powerLog: List<String> = emptyList(),
    val powerBusy: Boolean = false,
    val installedApps: List<TvApp> = emptyList(),
    val pinnedApps: List<TvApp> = TvApps.take(3),
    val stConfigured: Boolean = false,
    val stDevice: StDevice? = null,
    val stDevices: List<StDevice> = emptyList()
)

// ---------------------------------------------------------------------------
// Root screen
// ---------------------------------------------------------------------------

@Composable
fun RemoteScreen(state: RemoteUiState, actions: RemoteActions) {
    val palette = when (state.theme) {
        "light" -> LightPalette
        "system" -> if (isSystemInDarkTheme()) DarkPalette else LightPalette
        else -> DarkPalette
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        RemoteScreenContent(state, actions)
    }
}

@Composable
private fun RemoteScreenContent(state: RemoteUiState, actions: RemoteActions) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val scale = min(maxWidth.value / DESIGN_W, maxHeight.value / DESIGN_H)
        val d = Ds(scale, LocalDensity.current.fontScale)
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var showPicker by remember { mutableStateOf(false) }
        val pager = rememberPagerState { 3 }

        CompositionLocalProvider(LocalDs provides d, LocalHaptics provides state.haptics) {
            Box(Modifier.align(Alignment.Center).size(d.dp(DESIGN_W), d.dp(DESIGN_H))) {
                Header(state, actions, onPickTv = { showPicker = true })

                AnimatedContent(
                    targetState = tab,
                    modifier = Modifier.at(d, 0, 225, DESIGN_W, 1085),
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 6 * dir } +
                            fadeIn(tween(260, delayMillis = 40))) togetherWith
                            (slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 8 * dir } +
                                fadeOut(tween(180)))
                    },
                    label = "tab"
                ) { t ->
                    Box(Modifier.fillMaxSize()) {
                        when (t) {
                            0 -> {
                                HorizontalPager(pager, Modifier.fillMaxWidth().height(d.dp(1015))) { page ->
                                    when (page) {
                                        0 -> MainPage(state.pinnedApps, actions)
                                        1 -> NumbersPage(actions)
                                        else -> TouchPage(actions)
                                    }
                                }
                                PageDots(pager.currentPage, pager.currentPageOffsetFraction, top = 1032)
                            }
                            1 -> AppsTab(state, actions)
                            2 -> CastTab(actions)
                            else -> SettingsTab(state, actions)
                        }
                    }
                }

                ConnectionLostBanner(state.connectionLost, state.connecting, actions.reconnect)
                if (!state.connectionLost) StatusToast(state.status, state.statusSerial)
                BottomNav(tab) { tab = it }
            }
        }

        if (showPicker) {
            CompositionLocalProvider(LocalDs provides d, LocalHaptics provides state.haptics) {
                TvPickerDialog(state.status, state.scanning, state.tvs, state.selectedTv, actions) { showPicker = false }
            }
        }
    }
}

@Composable
private fun ConnectionLostBanner(visible: Boolean, connecting: Boolean, onReconnect: () -> Unit) {
    val d = LocalDs.current
    Box(Modifier.at(d, 46, 1212, 600, 104), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            Row(
                Modifier.background(pal.toastBg, RoundedCornerShape(d.dp(30)))
                    .border(1.dp, Color(0xFFE5484D).copy(.5f), RoundedCornerShape(d.dp(30)))
                    .padding(start = d.dp(26), end = d.dp(10), top = d.dp(10), bottom = d.dp(10)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(d.dp(12)).background(Color(0xFFE5484D), CircleShape))
                Spacer(Modifier.width(d.dp(14)))
                Label("Conexión perdida", 22)
                Spacer(Modifier.width(d.dp(20)))
                PillButton(if (connecting) "Conectando…" else "Reconectar", Modifier.width(d.dp(200)), onClick = onReconnect)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header: power · title + TV model · mute
// ---------------------------------------------------------------------------

@Composable
private fun Header(state: RemoteUiState, actions: RemoteActions, onPickTv: () -> Unit) {
    val d = LocalDs.current
    val tv = state.selectedTv
    val connected = state.connected
    val powerBusy = state.powerBusy
    RoundButton(Modifier.at(d, 43, 93, 90, 90), "Encender o apagar", onClick = actions.power) {
        Icon("power", Modifier.size(d.dp(40)))
    }
    if (powerBusy) {
        val spin = rememberInfiniteTransition(label = "powerSpin")
        val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "angle")
        val ink = Ink
        Canvas(Modifier.at(d, 39, 89, 98, 98)) {
            drawArc(
                Brush.sweepGradient(listOf(Color.Transparent, ink.copy(.9f))), angle, 300f, false,
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
    RoundButton(Modifier.at(d, 561, 94, 88, 88), "Silenciar", onClick = { actions.key("KEY_MUTE") }) {
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
            Modifier.clip(RoundedCornerShape(d.dp(17))).pressable(description = "Elegir televisor", onClick = onPickTv)
                .padding(horizontal = d.dp(10)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Icon("tv", Modifier.size(d.dp(30), d.dp(26)), Ink.copy(alpha = .85f))
                val busy = state.connecting || powerBusy
                if (connected || busy) {
                    val pulse = rememberInfiniteTransition(label = "pulse")
                    val k by pulse.animateFloat(
                        .55f, 1f, infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "k"
                    )
                    val dot by animateColorAsState(if (connected && !busy) OnlineGreen else Color(0xFFF5A623), label = "dot")
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(d.dp(5), d.dp(-3)).size(d.dp(9))
                            .graphicsLayer { if (busy && !connected) { alpha = k; scaleX = .8f + .3f * k; scaleY = scaleX } }
                            .background(dot, CircleShape).border(d.dp(2), Bg, CircleShape)
                    )
                }
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
private fun MainPage(pinned: List<TvApp>, actions: RemoteActions) {
    val d = LocalDs.current
    var playing by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        // Up to three shortcuts, spread over the same 600 px row as the mock-up.
        val n = pinned.size.coerceIn(1, 3)
        val w = (600f - 24f * (n - 1)) / n
        pinned.take(3).forEachIndexed { i, app ->
            BrandTile(brandOf(app), Modifier.at(d, 46 + i * (w + 24f), 16, w, 100), "Abrir ${app.name}", app.name) {
                actions.launchApp(app.id)
            }
        }

        DPad(Modifier.at(d, 86, 161, 520, 520), actions.key)

        RockerPill(Modifier.at(d, 46, 706, 118, 285), "volume",
            onUp = { actions.key("KEY_VOLUP") }, onDown = { actions.key("KEY_VOLDOWN") })
        RockerPill(Modifier.at(d, 528, 706, 118, 285), "channel",
            onUp = { actions.key("KEY_CHUP") }, onDown = { actions.key("KEY_CHDOWN") })

        GlassButton(Modifier.at(d, 186, 743, 92, 93), RoundedCornerShape(d.dp(24)), repeat = true, description = "Retroceder",
            onClick = { actions.key("KEY_REWIND") }) { SkipIcon(forward = false) }
        GlassButton(Modifier.at(d, 300, 743, 92, 93), RoundedCornerShape(d.dp(24)), description = "Reproducir o pausar",
            onClick = {
                actions.key(if (playing) "KEY_PAUSE" else "KEY_PLAY")
                playing = !playing
            }) { Icon("playPause", Modifier.size(d.dp(44), d.dp(30))) }
        GlassButton(Modifier.at(d, 414, 743, 92, 93), RoundedCornerShape(d.dp(24)), repeat = true, description = "Avanzar",
            onClick = { actions.key("KEY_FF") }) { SkipIcon(forward = true) }

        GlassButton(Modifier.at(d, 192, 873, 143, 102), RoundedCornerShape(d.dp(26)), description = "Atrás",
            onClick = { actions.key("KEY_RETURN") }) { Icon("back", Modifier.size(d.dp(38), d.dp(32))) }
        GlassButton(Modifier.at(d, 358, 873, 143, 102), RoundedCornerShape(d.dp(26)), description = "Inicio",
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
    val p = pal

    val nudge by animateFloatAsState(if (pressed != null && pressed != "KEY_ENTER") 1f else 0f,
        spring(dampingRatio = .5f, stiffness = 700f), label = "nudge")
    val okScale by animateFloatAsState(if (pressed == "KEY_ENTER") .95f else 1f,
        spring(dampingRatio = .5f, stiffness = 700f), label = "ok")
    val lastArrow = remember { mutableStateOf("KEY_UP") }
    if (pressed != null && pressed != "KEY_ENTER") lastArrow.value = pressed!!
    fun arrowShift(k: String): Offset {
        if (lastArrow.value != k) return Offset.Zero
        val n = nudge * 6f * d.s
        return when (k) { "KEY_UP" -> Offset(0f, -n); "KEY_DOWN" -> Offset(0f, n); "KEY_LEFT" -> Offset(-n, 0f); else -> Offset(n, 0f) }
    }
    Box(
        modifier.semantics {
            contentDescription = "Cruceta de navegación"
            customActions = listOf(
                CustomAccessibilityAction("Arriba") { key("KEY_UP"); true },
                CustomAccessibilityAction("Abajo") { key("KEY_DOWN"); true },
                CustomAccessibilityAction("Izquierda") { key("KEY_LEFT"); true },
                CustomAccessibilityAction("Derecha") { key("KEY_RIGHT"); true },
                CustomAccessibilityAction("OK") { key("KEY_ENTER"); true }
            )
        }.pointerInput(Unit) {
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
                    p.dial,
                    center = Offset(size.width * .40f, size.height * .30f), radius = r * 1.35f
                ), r
            )
            val sweep = when (pressed) {
                "KEY_RIGHT" -> -45f; "KEY_DOWN" -> 45f; "KEY_LEFT" -> 135f; "KEY_UP" -> 225f; else -> null
            }
            if (sweep != null) drawArc(p.fg.copy(alpha = .07f), sweep, 90f, true)
            drawCircle(
                Brush.verticalGradient(listOf(p.fg.copy(.20f), p.fg.copy(.03f), p.fg.copy(.07f))),
                r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx())
            )
            // Shadow cast by the raised OK button.
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.Black.copy(p.shadow), Color.Black.copy(p.shadow * .45f), Color.Transparent),
                    center = Offset(center.x, center.y + r * .03f), radius = r * .58f
                ), r * .58f, center = Offset(center.x, center.y + r * .03f)
            )
        }
        Icon("chevronUp", Modifier.align(Alignment.TopCenter).padding(top = d.dp(70)).offset(arrowShift("KEY_UP")).size(d.dp(38), d.dp(20)))
        Icon("chevronDown", Modifier.align(Alignment.BottomCenter).padding(bottom = d.dp(70)).offset(arrowShift("KEY_DOWN")).size(d.dp(38), d.dp(20)))
        Icon("chevronLeft", Modifier.align(Alignment.CenterStart).padding(start = d.dp(66)).offset(arrowShift("KEY_LEFT")).size(d.dp(20), d.dp(38)))
        Icon("chevronRight", Modifier.align(Alignment.CenterEnd).padding(end = d.dp(66)).offset(arrowShift("KEY_RIGHT")).size(d.dp(20), d.dp(38)))

        // Raised OK button.
        Box(Modifier.size(d.dp(228)).scale(okScale), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(
                    Brush.radialGradient(
                        p.ok,
                        center = Offset(size.width * .42f, size.height * .32f), radius = r * 1.3f
                    ), r
                )
                if (pressed == "KEY_ENTER") drawCircle(p.fg.copy(.07f), r)
                drawCircle(
                    Brush.verticalGradient(listOf(p.fg.copy(.18f), p.fg.copy(.02f), p.fg.copy(.05f))),
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
                .background(if (upPressed) pal.fg.copy(.07f) else Color.Transparent)
                .pressable(repeat = true, onPressedChange = { upPressed = it },
                    description = if (kind == "volume") "Subir volumen" else "Canal siguiente", onClick = onUp),
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
                .background(if (downPressed) pal.fg.copy(.07f) else Color.Transparent)
                .pressable(repeat = true, onPressedChange = { downPressed = it },
                    description = if (kind == "volume") "Bajar volumen" else "Canal anterior", onClick = onDown),
            contentAlignment = Alignment.Center
        ) {
            if (kind == "volume") Icon("minus", Modifier.size(d.dp(36)))
            else Icon("chevronDown", Modifier.size(d.dp(36), d.dp(19)))
        }
    }
}

@Composable
private fun BrandTile(brand: String, modifier: Modifier, description: String, name: String, onClick: () -> Unit) {
    val d = LocalDs.current
    GlassButton(modifier, RoundedCornerShape(d.dp(24)), description = description, onClick = onClick) { BrandLogo(brand, 1f, name) }
}

/** Known apps get their logo; anything else is drawn as its name. */
private fun brandOf(app: TvApp): String {
    val n = app.name.lowercase()
    return when {
        app.id == "3201910019365" || "prime" in n -> "prime"
        app.id == "3201901017640" || "disney" in n -> "disney"
        app.id == "3201907018807" || app.id == "11101200001" || "netflix" in n -> "netflix"
        app.id == "111299001912" || n == "youtube" -> "youtube"
        "spotify" in n -> "spotify"
        n == "max" || "hbo" in n -> "max"
        else -> "text"
    }
}

@Composable
private fun BrandLogo(brand: String, k: Float, name: String = "") {
    val d = LocalDs.current
    when (brand) {
        "prime" -> Image(
            painterResource(R.drawable.logo_prime), contentDescription = "Prime Video",
            modifier = Modifier.size(d.dp(90 * k), d.dp(43 * k)).offset(y = d.dp(3 * k))
        )
        "disney" -> Image(
            painterResource(R.drawable.logo_disney_plus), contentDescription = "Disney+",
            // The official logo is white; on the light theme draw it in ink colour.
            colorFilter = if (pal.dark) null else ColorFilter.tint(Ink),
            modifier = Modifier.size(d.dp(112 * k), d.dp(61 * k))
        )
        "netflix" -> Text(
            "NETFLIX", color = NetflixRed, fontSize = d.sp(40 * k), fontWeight = FontWeight.Black,
            fontFamily = CondensedFont, letterSpacing = d.sp(-0.5 * k),
            style = TextStyle(textGeometricTransform = TextGeometricTransform(scaleX = .86f))
        )
        "youtube" -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(d.dp(46 * k), d.dp(32 * k)).background(Color(0xFFFF0000), RoundedCornerShape(d.dp(9 * k))),
                contentAlignment = Alignment.Center
            ) { Icon("play", Modifier.size(d.dp(14 * k)), Color.White) }
            Spacer(Modifier.width(d.dp(7 * k)))
            Text("YouTube", color = Ink, fontSize = d.sp(28 * k), fontWeight = FontWeight.Bold, letterSpacing = d.sp(-1 * k))
        }
        "spotify" -> Text("Spotify", color = Color(0xFF1ED760), fontSize = d.sp(32 * k), fontWeight = FontWeight.Bold)
        "max" -> Text("max", color = Color(0xFF6E8BFF), fontSize = d.sp(38 * k), fontWeight = FontWeight.Black, letterSpacing = d.sp(-1 * k))
        else -> Text(
            name, color = Ink, fontSize = d.sp(26 * k), fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = d.dp(10))
        )
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
    val clipboard = LocalClipboardManager.current
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
            GlassButton(Modifier.size(d.dp(76), d.dp(76)), RoundedCornerShape(d.dp(20)), description = "Pegar en la TV",
                onClick = {
                    val clip = clipboard.getText()?.text.orEmpty()
                    if (clip.isNotEmpty()) actions.text(clip)
                }) { Icon("paste", Modifier.size(d.dp(32))) }
            Spacer(Modifier.width(d.dp(10)))
            GlassButton(Modifier.size(d.dp(120), d.dp(76)), RoundedCornerShape(d.dp(20)),
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
                description = mapOf("stop" to "Detener", "back" to "Atrás", "home" to "Inicio", "exit" to "Salir")[icon],
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
private fun AppsTab(state: RemoteUiState, actions: RemoteActions) {
    val d = LocalDs.current
    val fromTv = state.installedApps.isNotEmpty()
    // Apps reported by the TV first; otherwise the common ones.
    val apps = if (fromTv) state.installedApps else TvApps
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle(
            "Aplicaciones",
            if (fromTv) "${apps.size} instaladas en tu TV · mantén pulsada para fijarla en el mando"
            else "Mantén pulsada una app para fijarla en el mando"
        )
        apps.chunked(2).forEach { pair ->
            Row(Modifier.padding(horizontal = d.dp(46)).padding(bottom = d.dp(24)),
                horizontalArrangement = Arrangement.spacedBy(d.dp(24))) {
                pair.forEach { app ->
                    val pinned = state.pinnedApps.any { it.id == app.id }
                    GlassButton(
                        Modifier.weight(1f).height(d.dp(150)), RoundedCornerShape(d.dp(28)),
                        description = "Abrir ${app.name}",
                        onLongClick = { actions.togglePin(app) },
                        onClick = { actions.launchApp(app.id) }
                    ) {
                        BrandLogo(brandOf(app), 1.15f, app.name)
                        if (pinned) Icon(
                            "pin", Modifier.align(Alignment.TopEnd).padding(d.dp(16)).size(d.dp(22)), InkMuted
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (!fromTv) Label(
            "Cuando la TV esté conectada se mostrará su lista de apps (si el modelo la comparte).",
            20, InkMuted, Modifier.padding(start = d.dp(52), end = d.dp(52), bottom = d.dp(40))
        )
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
private fun SettingsTab(state: RemoteUiState, actions: RemoteActions) {
    val d = LocalDs.current
    val selectedTv = state.selectedTv
    val connected = state.connected
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
                Label(if (connected) "Conectado" else if (state.connecting) "Conectando…" else "Sin conexión", 22, Ink)
            }
            if (state.status.isNotBlank()) {
                Spacer(Modifier.height(d.dp(10)))
                Label(state.status, 21, InkMuted)
            }
            if (selectedTv != null) {
                Spacer(Modifier.height(d.dp(20)))
                Row(horizontalArrangement = Arrangement.spacedBy(d.dp(18))) {
                    PillButton(if (connected) "Reconectar" else "Conectar", Modifier.weight(1f)) { actions.connect(selectedTv) }
                    PillButton("Desconectar", Modifier.weight(1f), onClick = actions.disconnect)
                }
            }
        }
        Card { TvList(state.scanning, state.tvs, selectedTv, actions, onChosen = {}) }
        if (selectedTv != null) Card { PowerLab(selectedTv, state.powerLog, state.powerBusy, actions) }
        Card { SmartThingsCard(state, actions) }
        Card {
            Text("Tema", color = Ink, fontSize = d.sp(28), fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(d.dp(14)))
            Row(horizontalArrangement = Arrangement.spacedBy(d.dp(12))) {
                listOf("dark" to "Oscuro", "light" to "Claro", "system" to "Sistema").forEach { (value, label) ->
                    Segment(label, state.theme == value, Modifier.weight(1f)) { actions.setTheme(value) }
                }
            }
            Spacer(Modifier.height(d.dp(26)))
            ToggleRow("Vibración al pulsar", "Respuesta háptica en cada botón", state.haptics) { actions.setHaptics(it) }
            Spacer(Modifier.height(d.dp(22)))
            ToggleRow("Botones de volumen del móvil", "Suben y bajan el volumen de la TV con la app abierta", state.hardwareVolume) {
                actions.setHardwareVolume(it)
            }
        }
        Card { KeyTester(actions) }
        Label(
            "TvRemote · Samsung Smart TV (Tizen 2016+)\nEl móvil y el televisor deben estar en la misma Wi-Fi.",
            20, InkMuted.copy(alpha = .7f), Modifier.padding(start = d.dp(52), top = d.dp(4), bottom = d.dp(40))
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val d = LocalDs.current
    Row(Modifier.fillMaxWidth().pressable(description = title) { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = d.sp(26), fontWeight = FontWeight.Medium)
            Label(subtitle, 20, InkMuted)
        }
        Spacer(Modifier.width(d.dp(14)))
        Switch(on)
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val d = LocalDs.current
    val bg by animateColorAsState(if (selected) pal.pillTop else pal.fg.copy(.03f), label = "seg")
    Box(
        modifier.height(d.dp(70)).clip(RoundedCornerShape(d.dp(35))).background(bg)
            .border(1.dp, pal.fg.copy(if (selected) .16f else .06f), RoundedCornerShape(d.dp(35)))
            .pressable(description = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Label(label, 23, if (selected) Ink else InkMuted) }
}

@Composable
private fun InputField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier, mono: Boolean = false, onDone: () -> Unit = {}) {
    val d = LocalDs.current
    Box(
        modifier.height(d.dp(76)).background(pal.fg.copy(.04f), RoundedCornerShape(d.dp(22)))
            .border(1.dp, pal.fg.copy(.08f), RoundedCornerShape(d.dp(22))).padding(horizontal = d.dp(22)),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) Label(hint, 21, InkMuted)
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = d.sp(24), fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default),
            cursorBrush = SolidColor(Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SmartThingsCard(state: RemoteUiState, actions: RemoteActions) {
    val d = LocalDs.current
    var token by remember { mutableStateOf("") }
    Text("Encender con SmartThings", color = Ink, fontSize = d.sp(28), fontWeight = FontWeight.Medium)
    Label(
        if (state.stConfigured) "Activo${state.stDevice?.let { " · ${it.name}" } ?: " · elige tu TV"}"
        else "Para TVs que no despiertan por Wake-on-LAN. Crea un token en account.smartthings.com/tokens " +
            "con permisos de dispositivos. Samsung hace que estos tokens caduquen en 24 h.",
        20, InkMuted
    )
    Spacer(Modifier.height(d.dp(16)))
    Row(verticalAlignment = Alignment.CenterVertically) {
        InputField(token, { token = it }, if (state.stConfigured) "Nuevo token (vacío = quitar)" else "Token de SmartThings",
            Modifier.weight(1f), mono = true) { actions.saveSmartThingsToken(token); token = "" }
        Spacer(Modifier.width(d.dp(14)))
        PillButton("Guardar", Modifier.width(d.dp(150))) { actions.saveSmartThingsToken(token); token = "" }
    }
    state.stDevices.forEach { dev ->
        Spacer(Modifier.height(d.dp(12)))
        val selected = state.stDevice?.id == dev.id
        Segment(dev.name.ifBlank { dev.id.take(8) }, selected, Modifier.fillMaxWidth()) { actions.chooseSmartThingsDevice(dev) }
    }
}

@Composable
private fun KeyTester(actions: RemoteActions) {
    val d = LocalDs.current
    var key by remember { mutableStateOf("KEY_") }
    Text("Probar teclas", color = Ink, fontSize = d.sp(28), fontWeight = FontWeight.Medium)
    Label("Envía cualquier código para ver qué acepta tu modelo (p. ej. KEY_PLAY_BACK, KEY_AMBIENT).", 20, InkMuted)
    Spacer(Modifier.height(d.dp(16)))
    Row(verticalAlignment = Alignment.CenterVertically) {
        InputField(key, { key = it.uppercase().replace(' ', '_') }, "KEY_…", Modifier.weight(1f), mono = true) {
            if (key.length > 4) actions.key(key)
        }
        Spacer(Modifier.width(d.dp(14)))
        PillButton("Enviar", Modifier.width(d.dp(150))) { if (key.length > 4) actions.key(key) }
    }
    Spacer(Modifier.height(d.dp(14)))
    Row(horizontalArrangement = Arrangement.spacedBy(d.dp(10))) {
        listOf("KEY_PLAY_BACK", "KEY_PAUSE", "KEY_PLAY").forEach { k ->
            Segment(k.removePrefix("KEY_"), false, Modifier.weight(1f)) { actions.key(k) }
        }
    }
}

@Composable
private fun PowerLab(tv: SamsungTv, log: List<String>, busy: Boolean, actions: RemoteActions) {
    val d = LocalDs.current
    var mac by remember(tv.macAddress) { mutableStateOf(tv.macAddress ?: "") }
    Text("Pruebas de encendido", color = Ink, fontSize = d.sp(28), fontWeight = FontWeight.Medium)
    Label(
        "Para encender por red, activa en la TV: Ajustes › General › Red › Configuración experta › " +
            "Encender con móvil. Si la TV va por cable, usa la MAC del cable.", 20, InkMuted
    )
    Spacer(Modifier.height(d.dp(18)))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).height(d.dp(76)).background(pal.fg.copy(.04f), RoundedCornerShape(d.dp(22)))
                .border(1.dp, pal.fg.copy(.08f), RoundedCornerShape(d.dp(22))).padding(horizontal = d.dp(22)),
            contentAlignment = Alignment.CenterStart
        ) {
            if (mac.isEmpty()) Label("MAC de la TV (AA:BB:CC:DD:EE:FF)", 22, InkMuted)
            BasicTextField(
                mac, { mac = it.take(17) }, singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = d.sp(25), fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { actions.saveMac(mac) }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(d.dp(14)))
        PillButton("Guardar", Modifier.width(d.dp(150))) { actions.saveMac(mac) }
    }
    Spacer(Modifier.height(d.dp(16)))
    Row(horizontalArrangement = Arrangement.spacedBy(d.dp(14))) {
        PillButton("Estado", Modifier.weight(1f), onClick = actions.probeTv)
        PillButton("Wake-on-LAN", Modifier.weight(1.3f), onClick = actions.wakeTest)
        PillButton(if (busy) "…" else "Encender", Modifier.weight(1.1f), onClick = actions.power)
    }
    if (log.isNotEmpty()) {
        Spacer(Modifier.height(d.dp(18)))
        Column(
            Modifier.fillMaxWidth().background(pal.fg.copy(if (pal.dark) .0f else .04f)).background(Color.Black.copy(if (pal.dark) .35f else 0f), RoundedCornerShape(d.dp(20)))
                .padding(d.dp(18))
        ) {
            log.take(14).forEach {
                Text(it, color = InkMuted, fontSize = d.sp(17), fontFamily = FontFamily.Monospace)
            }
        }
        Text(
            "Borrar registro", color = InkMuted, fontSize = d.sp(20),
            modifier = Modifier.padding(top = d.dp(10)).pressable(onClick = actions.clearLog).padding(d.dp(6))
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
            .background(if (on) pal.switchOn else pal.switchOff, CircleShape)
            .border(1.dp, pal.fg.copy(.10f), CircleShape).padding(d.dp(5))
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
                .background(if (selected) pal.fg.copy(.07f) else pal.fg.copy(.03f), RoundedCornerShape(d.dp(22)))
                .border(1.dp, pal.fg.copy(if (selected) .14f else .05f), RoundedCornerShape(d.dp(22)))
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
            Modifier.fillMaxWidth().background(pal.dialogBg, RoundedCornerShape(d.dp(40)))
                .border(1.dp, pal.fg.copy(.10f), RoundedCornerShape(d.dp(40)))
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
private fun PageDots(current: Int, offset: Float, top: Number) {
    val d = LocalDs.current
    // Position follows the finger while swiping between pages.
    val pos = (current + offset).coerceIn(0f, 2f)
    repeat(3) { i ->
        val near = (1f - abs(pos - i)).coerceIn(0f, 1f)
        val color = lerp(DotOff, Ink, near)
        Box(
            Modifier.at(d, 302 + i * 38.5f, top, 13, 13)
                .graphicsLayer { val k = .85f + .15f * near; scaleX = k; scaleY = k }
                .background(color, CircleShape)
        )
    }
}

private val NavLabels = listOf("Mando", "Aplicaciones", "Transmitir", "Ajustes")

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val d = LocalDs.current
    val shape = RoundedCornerShape(d.dp(53))
    val slot = 623f / 4f
    val pillW = 133f
    val targetLeft = selected * slot + (slot - pillW) / 2f
    var previous by remember { mutableIntStateOf(selected) }
    val movingRight = selected >= previous
    SideEffect { previous = selected }
    // Leading edge moves fast, trailing edge lags: the pill stretches like a drop of liquid.
    val fast = spring<Float>(dampingRatio = .75f, stiffness = 520f)
    val slow = spring<Float>(dampingRatio = .82f, stiffness = 210f)
    val left by animateFloatAsState(targetLeft, if (movingRight) slow else fast, label = "pillL")
    val right by animateFloatAsState(targetLeft + pillW, if (movingRight) fast else slow, label = "pillR")

    Box(
        Modifier.at(d, 35, 1326, 623, 105)
            .background(Brush.verticalGradient(listOf(pal.navTop, pal.navBg)), shape)
            .border(1.dp, Brush.verticalGradient(listOf(pal.fg.copy(.14f), pal.fg.copy(.04f))), shape)
    ) {
        Box(
            Modifier.at(d, left, 16.5f, right - left, 72)
                .background(Brush.verticalGradient(listOf(pal.pillTop, pal.pillBottom)), CircleShape)
                .border(1.dp, Brush.verticalGradient(listOf(pal.fg.copy(.14f), pal.fg.copy(.03f))), CircleShape)
        )
        listOf("remote", "apps", "cast", "settings").forEachIndexed { i, icon ->
            NavItem(icon, NavLabels[i], i == selected, Modifier.at(d, i * slot, 0, slot, 105)) { onSelect(i) }
        }
    }
}

@Composable
private fun NavItem(icon: String, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val d = LocalDs.current
    var pressed by remember { mutableStateOf(false) }
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            bounce.snapTo(.72f)
            bounce.animateTo(1f, spring(dampingRatio = .38f, stiffness = 420f))
        }
    }
    val pressScale by animateFloatAsState(if (pressed) .86f else 1f, spring(stiffness = 900f), label = "navPress")
    val tint by animateColorAsState(if (selected) Ink else Ink.copy(alpha = .62f), tween(220), label = "navTint")
    val lift by animateFloatAsState(if (selected) -2f else 0f, spring(dampingRatio = .5f), label = "navLift")
    Box(
        modifier.pressable(description = label, onPressedChange = { pressed = it }, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center
    ) {
        val iconSize = when (icon) { "remote" -> d.dp(34); "settings" -> d.dp(38); else -> d.dp(36) }
        Icon(
            icon,
            Modifier.size(iconSize).graphicsLayer {
                val k = bounce.value * pressScale
                scaleX = k; scaleY = k
                translationY = lift * d.s * density
            },
            tint
        )
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
                modifier = Modifier.background(pal.toastBg, RoundedCornerShape(d.dp(26)))
                    .border(1.dp, pal.fg.copy(.10f), RoundedCornerShape(d.dp(26)))
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
@Composable
private fun Modifier.glass(shape: Shape, pressed: Boolean = false): Modifier =
    this.clip(shape)
        .background(Brush.verticalGradient(listOf(pal.surfaceTop, pal.surfaceBottom)))
        .background(if (pressed) pal.fg.copy(.07f) else Color.Transparent)
        .border(1.dp, Brush.verticalGradient(listOf(pal.fg.copy(.20f), pal.fg.copy(.04f), pal.fg.copy(.07f))), shape)

/**
 * Fires on release (so swiping the pager never presses a key). With [repeat], holding the
 * button keeps firing, like a physical remote.
 */
@Composable
private fun Modifier.pressable(
    repeat: Boolean = false,
    onPressedChange: (Boolean) -> Unit = {},
    description: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val click by rememberUpdatedState(onClick)
    val pressedCb by rememberUpdatedState(onPressedChange)
    val longCb by rememberUpdatedState(onLongClick)
    val hasLong = onLongClick != null
    return this.semantics {
        role = Role.Button
        if (description != null) contentDescription = description
        semanticsClick { click(); true }
        if (hasLong) semanticsLongClick { longCb?.invoke(); true }
    }.pointerInput(repeat, haptics, hasLong) {
        awaitEachGesture {
            awaitFirstDown()
            pressedCb(true)
            var fired = false
            val job = if (repeat) scope.launch {
                delay(420)
                while (true) { fired = true; view.tick(haptics); click(); delay(120) }
            } else if (hasLong) scope.launch {
                delay(480)
                fired = true
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                longCb?.invoke()
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
    modifier: Modifier, shape: Shape, repeat: Boolean = false, description: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit, content: @Composable BoxScope.() -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val k by animateFloatAsState(if (pressed) .96f else 1f, label = "press")
    Box(
        modifier.scale(k).glass(shape, pressed)
            .pressable(repeat, onPressedChange = { pressed = it }, description = description, onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center, content = content
    )
}

@Composable
private fun RoundButton(modifier: Modifier, description: String, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val k by animateFloatAsState(if (pressed) .94f else 1f, label = "press")
    Box(
        modifier.scale(k).clip(CircleShape)
            .background(Brush.radialGradient(pal.round))
            .background(if (pressed) pal.fg.copy(.08f) else Color.Transparent)
            .border(1.dp, Brush.verticalGradient(listOf(pal.fg.copy(.12f), pal.fg.copy(.03f))), CircleShape)
            .pressable(onPressedChange = { pressed = it }, description = description, onClick = onClick),
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
    val holeColor = pal.navBg
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
                val hole = holeColor
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
            "pin" -> {
                drawCircle(tint, m * .22f, p(.5f, .30f))
                line(.5f, .50f, .5f, .94f)
            }
            "paste" -> {
                drawRoundRect(tint, p(.18f, .16f), Size(w * .64f, h * .78f), CornerRadius(m * .08f), style = stroke)
                drawRoundRect(tint, p(.34f, .06f), Size(w * .32f, h * .18f), CornerRadius(m * .05f))
                line(.32f, .46f, .68f, .46f); line(.32f, .64f, .58f, .64f)
            }
            "touch" -> {
                drawCircle(tint, m * .12f, p(.5f, .32f), style = stroke)
                drawCircle(tint, m * .26f, p(.5f, .32f), style = Stroke(sw * .7f))
                line(.5f, .58f, .5f, .92f)
            }
        }
    }
}
