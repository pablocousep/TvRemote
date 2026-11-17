package com.reelog.tvremote

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
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
private val OnlineGreen = Color(0xFF3DDC84)

/** Converts design pixels of the mock-up into real dp / sp for this screen. */
@Stable
private class Ds(val s: Float, private val fontScale: Float) {
    fun dp(px: Number): Dp = (px.toFloat() * s).dp
    fun sp(px: Number): TextUnit = (px.toFloat() * s / fontScale).sp
}

private val LocalDs = staticCompositionLocalOf { Ds(0.5f, 1f) }
private val LocalHaptics = staticCompositionLocalOf { true }
private val LocalAppIcons = compositionLocalOf { emptyMap<String, ImageBitmap>() }

private fun Modifier.at(d: Ds, x: Number, y: Number, w: Number, h: Number) =
    this.offset(d.dp(x), d.dp(y)).size(d.dp(w), d.dp(h))

private fun Modifier.offset(shift: Offset) = this.offset(shift.x.dp, shift.y.dp)

private fun View.tick(enabled: Boolean) {
    if (enabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}

data class TvApp(val id: String, val name: String, val iconPath: String? = null, val appType: Int? = null)

val TvApps = listOf(
    TvApp("3201910019365", "Prime Video"),
    TvApp("3201901017640", "Disney+"),
    TvApp("3201907018807", "Netflix"),
    TvApp("111299001912", "YouTube"),
    TvApp("3201807016597", "Apple TV"),
    TvApp("3201606009684", "Spotify"),
    TvApp("3202301029760", "Max"),
    TvApp("3202010022079", "Navegador")
)

class RemoteActions(
    val key: (String) -> Unit,
    val text: (String) -> Unit,
    val launchApp: (TvApp) -> Unit,
    val power: () -> Unit,
    val discover: () -> Unit,
    val connect: (SamsungTv) -> Unit,
    val forget: (SamsungTv) -> Unit,
    val disconnect: () -> Unit,
    val openCastSettings: () -> Unit,
    val setHaptics: (Boolean) -> Unit,
    val mouseMove: (Int, Int) -> Unit = { _, _ -> },
    val mouseClick: () -> Unit = {},
    val reconnect: () -> Unit = {},
    val togglePin: (TvApp) -> Unit = {},
    val setTheme: (String) -> Unit = {},
    val checkForUpdates: () -> Unit = {},
    val startUpdate: () -> Unit = {},
    val dismissUpdate: () -> Unit = {},
    val forgetSaved: (SamsungTv) -> Unit = {},
    val renameTv: (SamsungTv, String?) -> Unit = { _, _ -> },
    val openWifiPanel: () -> Unit = {},
    val dismissWifiMismatch: () -> Unit = {},
    val showWolGuide: (Boolean) -> Unit = {}
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
    val theme: String = "dark",
    val powerBusy: Boolean = false,
    val installedApps: List<TvApp> = emptyList(),
    val pinnedApps: List<TvApp> = TvApps.take(3),
    val appIcons: Map<String, ImageBitmap> = emptyMap(),
    val versionName: String = "",
    /** False in the Google Play build, which is updated by Play. */
    val selfUpdate: Boolean = true,
    val update: UpdateInfo? = null,
    val updatePrompt: Boolean = false,
    val updateChecking: Boolean = false,
    val updateProgress: Float? = null,
    val savedTvs: List<SavedTv> = emptyList(),
    val tvAsleep: Boolean = false,
    val activity: String? = null,
    val wifiMismatch: WifiMismatch? = null,
    val wolGuide: Boolean = false
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
    CompositionLocalProvider(LocalPalette provides palette, LocalAppIcons provides state.appIcons) {
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
                ActivityLine(state.activity)

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
                                        0 -> MainPage(state.pinnedApps, actions, onEditApps = { tab = 1 })
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

                ConnectionLostBanner(state.connectionLost && state.wifiMismatch == null, state.connecting, actions.reconnect)
                WifiBanner(state.wifiMismatch, actions)
                if (!state.connectionLost && state.wifiMismatch == null) StatusToast(state.status, state.statusSerial)
                BottomNav(tab, settingsBadge = state.update != null) { tab = it }
            }
        }

        if (state.wolGuide) {
            CompositionLocalProvider(LocalDs provides d, LocalHaptics provides state.haptics) {
                WolGuideDialog(state, actions)
            }
        }

        if (state.updatePrompt && state.update != null) {
            CompositionLocalProvider(LocalDs provides d, LocalHaptics provides state.haptics) {
                UpdateDialog(state.update, state.updateProgress, actions)
            }
        }

        if (showPicker) {
            CompositionLocalProvider(LocalDs provides d, LocalHaptics provides state.haptics) {
                TvPickerDialog(state, actions) { showPicker = false }
            }
        }
    }
}

/** "This TV is on another Wi-Fi": one tap opens Android's Wi-Fi picker; we reconnect once it changes. */
@Composable
private fun WifiBanner(mismatch: WifiMismatch?, actions: RemoteActions) {
    val d = LocalDs.current
    var last by remember { mutableStateOf(mismatch) }
    if (mismatch != null) last = mismatch
    Box(Modifier.at(d, 30, 1146, 632, 170), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            mismatch != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            val m = last ?: return@AnimatedVisibility
            Row(
                Modifier.fillMaxWidth().background(pal.toastBg, RoundedCornerShape(d.dp(32)))
                    .border(1.dp, Color(0xFF3E8EF7).copy(.55f), RoundedCornerShape(d.dp(32)))
                    .padding(start = d.dp(24), end = d.dp(12), top = d.dp(16), bottom = d.dp(16)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon("wifi", Modifier.size(d.dp(34)), Color(0xFF3E8EF7))
                Spacer(Modifier.width(d.dp(18)))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${m.tv.displayName} está en «${m.expected}»", color = Ink, fontSize = d.sp(21),
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        m.current?.let { "Estás en «$it»" } ?: "No estás conectado a una Wi-Fi",
                        color = InkMuted, fontSize = d.sp(19), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(d.dp(10)))
                Text(
                    "Cambiar Wi-Fi", color = Color.White, fontSize = d.sp(20), fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(d.dp(26))).background(Color(0xFF3E8EF7))
                        .pressable(description = "Cambiar Wi-Fi", onClick = actions.openWifiPanel)
                        .padding(horizontal = d.dp(20), vertical = d.dp(14))
                )
                Box(
                    Modifier.size(d.dp(50)).clip(CircleShape)
                        .pressable(description = "Cerrar aviso", onClick = actions.dismissWifiMismatch),
                    contentAlignment = Alignment.Center
                ) { Icon("close", Modifier.size(d.dp(18)), InkMuted) }
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
                if (!connected && !busy && state.tvAsleep) Box(
                    Modifier.align(Alignment.TopEnd).offset(d.dp(5), d.dp(-3)).size(d.dp(9))
                        .background(DotOff, CircleShape).border(d.dp(2), Bg, CircleShape)
                )
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
                tv?.label?.takeIf { it.isNotBlank() }
                    ?: tv?.model?.takeIf { it.isNotBlank() && !it.startsWith("Modelo") } ?: tv?.name ?: "Seleccionar TV",
                color = Ink, fontSize = d.sp(25), fontWeight = FontWeight.SemiBold,
                letterSpacing = d.sp(0.3), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = d.dp(420))
            )
            Spacer(Modifier.width(d.dp(16)))
            Icon("chevronDown", Modifier.size(d.dp(20), d.dp(12)), Ink.copy(alpha = .85f))
        }
    }
}

@Composable
private fun ActivityLine(text: String?) {
    val d = LocalDs.current
    // Remember the last text so it can fade out with its content.
    var shown by remember { mutableStateOf(text ?: "") }
    if (text != null) shown = text
    Box(Modifier.at(d, 0, 182, DESIGN_W, 36), contentAlignment = Alignment.Center) {
        AnimatedVisibility(text != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spinner(Modifier.size(d.dp(18)))
                Spacer(Modifier.width(d.dp(10)))
                Text(shown, color = InkMuted, fontSize = d.sp(19), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Page 1 — the main remote from the mock-up
// ---------------------------------------------------------------------------

@Composable
private fun MainPage(pinned: List<TvApp>, actions: RemoteActions, onEditApps: () -> Unit) {
    val d = LocalDs.current
    var playing by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        // Up to three shortcuts over the mock-up's 600 px row; a free slot invites adding more.
        val apps = pinned.take(3)
        val slots = if (apps.size < 3) apps.size + 1 else 3
        val w = (600f - 24f * (slots - 1)) / slots
        apps.forEachIndexed { i, app ->
            GlassButton(
                Modifier.at(d, 46 + i * (w + 24f), 16, w, 100), RoundedCornerShape(d.dp(24)),
                description = "Abrir ${app.name}", onLongClick = onEditApps, onClick = { actions.launchApp(app) }
            ) { AppLogo(app, if (slots == 3) 1f else 1.15f) }
        }
        if (apps.size < 3) {
            AddSlot(
                Modifier.at(d, 46 + apps.size * (w + 24f), 16, w, 100),
                if (apps.isEmpty()) "Añadir apps al mando" else null, onEditApps
            )
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

/** Which bundled official logo an app uses, or null. */
fun brandOf(app: TvApp): String? {
    val n = app.name.lowercase()
    return when {
        app.id == "3201910019365" || "prime" in n -> "prime"
        app.id == "3201901017640" || "disney" in n -> "disney"
        app.id == "3201907018807" || app.id == "11101200001" || "netflix" in n -> "netflix"
        app.id == "111299001912" || n == "youtube" -> "youtube"
        app.id == "3201606009684" || "spotify" in n -> "spotify"
        app.id == "3201807016597" || "apple tv" in n -> "appletv"
        app.id == "3202301029760" || n == "max" || "hbo" in n -> "max"
        app.id in setOf("3202010022079", "3201907018784", "org.tizen.browser") || "navegador" in n || "internet" == n -> "browser"
        else -> null
    }
}

/**
 * Official logo of the app: bundled brand artwork for the well-known services (with the
 * brand's own variant for dark or light backgrounds), otherwise the icon the TV sends for
 * that app, and only as a last resort its name.
 */
@Composable
private fun AppLogo(app: TvApp, k: Float) {
    val d = LocalDs.current
    val dark = pal.dark
    // (dark-background artwork, light-background artwork, width, height in design px)
    val art: Triple<Int, Int, Pair<Int, Int>>? = when (brandOf(app)) {
        "prime" -> Triple(R.drawable.logo_primevideo, R.drawable.logo_primevideo_onlight, 140 to 43)
        "disney" -> Triple(R.drawable.logo_disney_plus, R.drawable.logo_disney_plus, 112 to 61)
        "netflix" -> Triple(R.drawable.logo_netflix, R.drawable.logo_netflix, 132 to 36)
        "youtube" -> Triple(R.drawable.logo_youtube, R.drawable.logo_youtube_onlight, 140 to 31)
        "spotify" -> Triple(R.drawable.logo_spotify, R.drawable.logo_spotify, 138 to 41)
        "appletv" -> Triple(R.drawable.logo_appletv, R.drawable.logo_appletv_onlight, 100 to 38)
        "max" -> Triple(R.drawable.logo_max, R.drawable.logo_max, 116 to 32)
        else -> null
    }
    val tvIcon = LocalAppIcons.current[app.id]
    when {
        art != null -> Image(
            painterResource(if (dark) art.first else art.second), contentDescription = app.name,
            contentScale = ContentScale.Fit,
            // Disney+ only ships a white logo; on light backgrounds it is shown in ink colour.
            colorFilter = if (!dark && brandOf(app) == "disney") ColorFilter.tint(Ink) else null,
            modifier = Modifier.size(d.dp(art.third.first * k), d.dp(art.third.second * k))
        )
        tvIcon != null -> Image(
            tvIcon, contentDescription = app.name, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(d.dp(14))
        )
        else -> Text(
            app.name, color = Ink, fontSize = d.sp(26 * k), fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = d.dp(10))
        )
    }
}


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

        // Real Samsung pointer: relative mouse movement and a mouse click.
        // Do not translate drags into D-pad key presses.
        Box(
            Modifier.at(d, 46, 146, 600, 600).glass(RoundedCornerShape(d.dp(44)))
                .pointerInput(actions) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var moved = false
                        var totalX = 0f
                        var totalY = 0f
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val delta = change.positionChange()
                            totalX += delta.x
                            totalY += delta.y
                            if (!moved && (totalX * totalX + totalY * totalY) > viewConfiguration.touchSlop * viewConfiguration.touchSlop) {
                                moved = true
                                view.tick(haptics)
                            }
                            if (moved && (delta.x != 0f || delta.y != 0f)) {
                                // Convert finger pixels to TV pointer movement. A little
                                // acceleration makes the full screen reachable without
                                // sacrificing precision on short movements.
                                val sensitivity = 2.2f
                                val dx = (delta.x * sensitivity).toInt()
                                val dy = (delta.y * sensitivity).toInt()
                                if (dx != 0 || dy != 0) actions.mouseMove(dx, dy)
                                change.consume()
                            }
                        } while (event.changes.any { it.id == down.id && it.pressed })
                        if (!moved) {
                            view.tick(haptics)
                            actions.mouseClick()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon("touch", Modifier.size(d.dp(64)), InkMuted)
                Spacer(Modifier.height(d.dp(18)))
                Label("Desliza para mover el puntero · toca para clic", 22, InkMuted)
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

/** Dashed "free slot" tile that leads to the Apps tab. */
@Composable
private fun AddSlot(modifier: Modifier, label: String?, onClick: () -> Unit) {
    val d = LocalDs.current
    val line = pal.fg.copy(.22f)
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier.clip(RoundedCornerShape(d.dp(24)))
            .background(if (pressed) pal.fg.copy(.06f) else Color.Transparent)
            .drawBehind {
                val sw = 1.4.dp.toPx()
                drawRoundRect(
                    line, topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw),
                    cornerRadius = CornerRadius(24 * d.s * density),
                    style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                )
            }
            .pressable(description = "Añadir apps al mando", onPressedChange = { pressed = it }, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon("plus", Modifier.size(d.dp(26)), InkMuted)
            if (label != null) {
                Spacer(Modifier.width(d.dp(14)))
                Label(label, 22, InkMuted)
            }
        }
    }
}

/** Round badge in a tile corner: "+" adds the app to the remote, a tick means it is there. */
@Composable
private fun PinBadge(pinned: Boolean, enabled: Boolean, modifier: Modifier, description: String, onClick: () -> Unit) {
    val d = LocalDs.current
    val bg by animateColorAsState(if (pinned) OnlineGreen else pal.surfaceTop, label = "pinBg")
    val k by animateFloatAsState(if (pinned) 1f else .92f, spring(dampingRatio = .45f, stiffness = 500f), label = "pinK")
    Box(
        modifier.size(d.dp(56)).scale(k).clip(CircleShape).background(bg)
            .border(1.dp, if (pinned) Color.Transparent else pal.fg.copy(.18f), CircleShape)
            .pressable(description = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (pinned) "check" else "plus", Modifier.size(d.dp(24)),
            when { pinned -> Color(0xFF0B2414); enabled -> Ink; else -> InkMuted.copy(.5f) }
        )
    }
}

@Composable
private fun AppsTab(state: RemoteUiState, actions: RemoteActions) {
    val d = LocalDs.current
    val fromTv = state.installedApps.isNotEmpty()
    // Apps reported by the TV first; otherwise the common ones.
    val apps = if (fromTv) state.installedApps else TvApps
    val pinned = state.pinnedApps.take(3)
    val full = pinned.size >= 3
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("Aplicaciones", "Toca una app para abrirla en la TV")

        // Shortcuts shown on the main remote.
        Column(Modifier.padding(horizontal = d.dp(46))) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("En el mando", color = Ink, fontSize = d.sp(28), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Label("${pinned.size} de 3", 21, InkMuted)
            }
            Label("Accesos directos de la pantalla principal", 20, InkMuted)
            Spacer(Modifier.height(d.dp(18)))
            Row(horizontalArrangement = Arrangement.spacedBy(d.dp(18))) {
                for (i in 0 until 3) {
                    val app = pinned.getOrNull(i)
                    if (app == null) {
                        Box(
                            Modifier.weight(1f).height(d.dp(104)).clip(RoundedCornerShape(d.dp(24)))
                                .drawBehind {
                                    val sw = 1.4.dp.toPx()
                                    drawRoundRect(
                                        Color.Gray.copy(.5f), topLeft = Offset(sw / 2, sw / 2),
                                        size = Size(size.width - sw, size.height - sw),
                                        cornerRadius = CornerRadius(24 * d.s * density),
                                        style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) { Label("Libre", 21, InkMuted) }
                    } else {
                        Box(Modifier.weight(1f).height(d.dp(104))) {
                            GlassButton(
                                Modifier.fillMaxSize(), RoundedCornerShape(d.dp(24)),
                                description = "Abrir ${app.name}", onClick = { actions.launchApp(app) }
                            ) { AppLogo(app, .8f) }
                            Box(
                                Modifier.align(Alignment.TopEnd).offset(d.dp(10), d.dp(-10)).size(d.dp(44))
                                    .clip(CircleShape).background(pal.surfaceTop)
                                    .border(1.dp, pal.fg.copy(.2f), CircleShape)
                                    .pressable(description = "Quitar ${app.name} del mando") { actions.togglePin(app) },
                                contentAlignment = Alignment.Center
                            ) { Icon("minus", Modifier.size(d.dp(18)), Ink) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(d.dp(36)))
        Column(Modifier.padding(horizontal = d.dp(46))) {
            Text(
                if (fromTv) "Instaladas en tu TV" else "Apps", color = Ink, fontSize = d.sp(28),
                fontWeight = FontWeight.SemiBold
            )
            Label(
                if (full) "El mando está completo: quita una app para añadir otra" else "Pulsa + para añadirla al mando",
                20, InkMuted
            )
        }
        Spacer(Modifier.height(d.dp(18)))
        apps.chunked(2).forEach { pair ->
            Row(Modifier.padding(horizontal = d.dp(46)).padding(bottom = d.dp(24)),
                horizontalArrangement = Arrangement.spacedBy(d.dp(24))) {
                pair.forEach { app ->
                    val isPinned = pinned.any { it.id == app.id }
                    Box(Modifier.weight(1f).height(d.dp(150))) {
                        GlassButton(
                            Modifier.fillMaxSize(), RoundedCornerShape(d.dp(28)),
                            description = "Abrir ${app.name}",
                            onLongClick = { actions.togglePin(app) },
                            onClick = { actions.launchApp(app) }
                        ) { AppLogo(app, .95f) }
                        PinBadge(
                            isPinned, enabled = isPinned || !full,
                            modifier = Modifier.align(Alignment.TopEnd).padding(d.dp(12)),
                            description = if (isPinned) "Quitar ${app.name} del mando" else "Añadir ${app.name} al mando"
                        ) { actions.togglePin(app) }
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
                    Label("Con Smart View (móviles Samsung) o Miracast", 22, InkMuted)
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("Ajustes")
        // Saved televisions
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon("tv")
                Spacer(Modifier.width(d.dp(20)))
                Column(Modifier.weight(1f)) {
                    Text("Televisores guardados", color = Ink, fontSize = d.sp(28), fontWeight = FontWeight.SemiBold)
                    Label("Toca uno para conectarte", 21, InkMuted)
                }
            }
            Spacer(Modifier.height(d.dp(24)))
            TvList(
                state.scanning, state.tvs, state.savedTvs, state.selectedTv, actions, onChosen = {},
                connected = state.connected, connecting = state.connecting || state.powerBusy, asleep = state.tvAsleep
            )
        }
        // Turning the TV on from the phone
        Card { WolCard(state, actions) }
        // Appearance
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon("theme")
                Spacer(Modifier.width(d.dp(20)))
                Column {
                    Text("Tema", color = Ink, fontSize = d.sp(28), fontWeight = FontWeight.SemiBold)
                    Label("Elige el aspecto de la aplicación", 21, InkMuted)
                }
            }
            Spacer(Modifier.height(d.dp(24)))
            Row(horizontalArrangement = Arrangement.spacedBy(d.dp(10))) {
                listOf("dark" to "Oscuro", "light" to "Claro", "system" to "Sistema").forEach { (value, label) ->
                    Segment(label, state.theme == value, Modifier.weight(1f)) { actions.setTheme(value) }
                }
            }
        }
        // Updates (GitHub build only; the Play build is updated by Play)
        if (state.selfUpdate) Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon("download")
                Spacer(Modifier.width(d.dp(20)))
                Column {
                    Text("Actualizaciones", color = Ink, fontSize = d.sp(28), fontWeight = FontWeight.SemiBold)
                    Label(
                        state.update?.let { "Nueva versión ${it.tag} disponible" }
                            ?: state.versionName.takeIf { it.isNotBlank() }?.let { "Versión instalada $it" }
                            ?: "Mantén la app siempre al día",
                        21, if (state.update != null) OnlineGreen else InkMuted
                    )
                }
            }
            Spacer(Modifier.height(d.dp(22)))
            val update = state.update
            when {
                state.updateProgress != null -> UpdateProgress(state.updateProgress)
                else -> Row(
                    Modifier.fillMaxWidth().height(d.dp(76)).clip(RoundedCornerShape(d.dp(38)))
                        .background(if (update != null) OnlineGreen.copy(.16f) else pal.fg.copy(.055f))
                        .border(1.dp, if (update != null) OnlineGreen.copy(.45f) else pal.fg.copy(.07f), RoundedCornerShape(d.dp(38)))
                        .pressable(
                            description = if (update != null) "Actualizar" else "Buscar actualizaciones",
                            onClick = if (update != null) actions.startUpdate else actions.checkForUpdates
                        ),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        state.updateChecking -> Spinner(Modifier.size(d.dp(26)))
                        update != null -> Icon("download", Modifier.size(d.dp(26)), Ink)
                        else -> Icon("refresh", Modifier.size(d.dp(26)), Ink)
                    }
                    Spacer(Modifier.width(d.dp(16)))
                    Label(
                        when {
                            state.updateChecking -> "Comprobando…"
                            update != null -> "Actualizar a ${update.tag}"
                            else -> "Buscar actualizaciones"
                        }, 23
                    )
                }
            }
        }
        Row(
            Modifier.padding(start = d.dp(48), top = d.dp(4), bottom = d.dp(36)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "TvRemote ${state.versionName}".trim() + " · Samsung Smart TV (Tizen 2016+)",
                color = InkMuted.copy(.75f), fontSize = d.sp(19)
            )
        }
    }
}

/** True when the app has what it needs to switch this TV on (its network address). */
private fun wolReady(tv: SamsungTv?) = tv?.macAddress != null

@Composable
private fun WolSteps() {
    val d = LocalDs.current
    val steps = listOf(
        "En la tele, abre Ajustes › General › Red › Configuración experta y activa «Encender con móvil». " +
            "En teles de 2022 o posteriores está en Ajustes › Conexión › Red › Configuración experta.",
        "Con la tele encendida, conéctate a ella una vez desde la app. Así aprende a despertarla.",
        "Listo: con la tele apagada, pulsa el botón de encendido de la app y se encenderá. El móvil tiene que estar en la misma Wi-Fi."
    )
    steps.forEachIndexed { i, text ->
        if (i > 0) Spacer(Modifier.height(d.dp(16)))
        Row {
            Box(
                Modifier.size(d.dp(40)).background(pal.fg.copy(.09f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("${i + 1}", color = Ink, fontSize = d.sp(20), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(d.dp(18)))
            Text(text, color = Ink.copy(.88f), fontSize = d.sp(21), modifier = Modifier.weight(1f).padding(top = d.dp(6)))
        }
    }
}

@Composable
private fun WolCard(state: RemoteUiState, actions: RemoteActions) {
    val d = LocalDs.current
    val tv = state.selectedTv
    val ready = wolReady(tv)
    Row(verticalAlignment = Alignment.CenterVertically) {
        SettingsIcon("power")
        Spacer(Modifier.width(d.dp(20)))
        Column(Modifier.weight(1f)) {
            Text("Encender desde el móvil", color = Ink, fontSize = d.sp(28), fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(d.dp(10)).background(if (ready) OnlineGreen else Color(0xFFF5A623), CircleShape))
                Spacer(Modifier.width(d.dp(8)))
                Label(
                    when {
                        tv == null -> "Añade una tele para empezar"
                        ready -> "Listo para encender ${tv.displayName}"
                        else -> "Falta conectarte una vez con la tele encendida"
                    }, 21, InkMuted
                )
            }
        }
    }
    Spacer(Modifier.height(d.dp(22)))
    PillButton(if (ready) "¿No se enciende? Ver los pasos" else "Cómo activarlo (3 pasos)", Modifier.fillMaxWidth()) {
        actions.showWolGuide(true)
    }
}

@Composable
private fun WolGuideDialog(state: RemoteUiState, actions: RemoteActions) {
    val d = LocalDs.current
    val tv = state.selectedTv
    Dialog(onDismissRequest = { actions.showWolGuide(false) }) {
        Column(
            Modifier.fillMaxWidth().background(pal.dialogBg, RoundedCornerShape(d.dp(40)))
                .border(1.dp, pal.fg.copy(.10f), RoundedCornerShape(d.dp(40)))
                .padding(d.dp(34)).verticalScroll(rememberScrollState())
        ) {
            Box(
                Modifier.size(d.dp(84)).background(pal.fg.copy(.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon("power", Modifier.size(d.dp(40)), Ink) }
            Spacer(Modifier.height(d.dp(22)))
            Text("Enciende la tele desde el móvil", color = Ink, fontSize = d.sp(32), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(d.dp(8)))
            Label(
                if (wolReady(tv)) "Si ${tv?.displayName ?: "la tele"} no se ha encendido, revisa el paso 1: suele venir desactivado."
                else "Solo hay que hacerlo una vez:",
                21, InkMuted
            )
            Spacer(Modifier.height(d.dp(26)))
            WolSteps()
            Spacer(Modifier.height(d.dp(30)))
            PillButton("Entendido", Modifier.fillMaxWidth()) { actions.showWolGuide(false) }
        }
    }
}

@Composable
private fun SettingsIcon(kind: String) {
    val d = LocalDs.current
    Box(
        Modifier.size(d.dp(78)).background(pal.fg.copy(.075f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(kind, Modifier.size(d.dp(38)), Ink)
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
private fun UpdateProgress(progress: Float) {
    val d = LocalDs.current
    val shown by animateFloatAsState(progress, label = "dl")
    Column {
        Label("Descargando… ${(progress * 100).toInt()} %", 21, InkMuted)
        Spacer(Modifier.height(d.dp(10)))
        Box(Modifier.fillMaxWidth().height(d.dp(12)).background(pal.fg.copy(.08f), CircleShape)) {
            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(OnlineGreen, CircleShape))
        }
    }
}

@Composable
private fun UpdateDialog(update: UpdateInfo, progress: Float?, actions: RemoteActions) {
    val d = LocalDs.current
    Dialog(onDismissRequest = { if (progress == null) actions.dismissUpdate() }) {
        Column(
            Modifier.fillMaxWidth().background(pal.dialogBg, RoundedCornerShape(d.dp(40)))
                .border(1.dp, pal.fg.copy(.10f), RoundedCornerShape(d.dp(40))).padding(d.dp(36))
        ) {
            Text("Nueva versión disponible", color = Ink, fontSize = d.sp(34), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(d.dp(6)))
            Label(
                "${update.tag}" + (if (update.sizeBytes > 0) " · %.1f MB".format(update.sizeBytes / 1_048_576.0) else ""),
                22, InkMuted
            )
            if (update.notes.isNotBlank()) {
                Spacer(Modifier.height(d.dp(20)))
                Column(Modifier.heightIn(max = d.dp(520)).verticalScroll(rememberScrollState())) {
                    Text(update.notes, color = Ink.copy(.85f), fontSize = d.sp(21))
                }
            }
            Spacer(Modifier.height(d.dp(28)))
            if (progress != null) {
                UpdateProgress(progress)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(d.dp(16))) {
                    PillButton("Más tarde", Modifier.weight(1f), onClick = actions.dismissUpdate)
                    PillButton("Actualizar", Modifier.weight(1f), onClick = actions.startUpdate)
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val d = LocalDs.current
    val shape = RoundedCornerShape(d.dp(32))
    Column(
        Modifier.padding(horizontal = d.dp(46)).padding(bottom = d.dp(24)).fillMaxWidth()
            .clip(shape).background(pal.surfaceBottom.copy(alpha = .92f))
            .border(1.dp, pal.fg.copy(.075f), shape).padding(d.dp(28)),
        content = content
    )
}

@Composable
private fun TvList(
    scanning: Boolean, tvs: List<SamsungTv>, saved: List<SavedTv>, selectedTv: SamsungTv?,
    actions: RemoteActions, onChosen: () -> Unit,
    connected: Boolean = false, connecting: Boolean = false, asleep: Boolean = false
) {
    val d = LocalDs.current
    fun same(a: SamsungTv, b: SamsungTv) =
        (a.macAddress != null && a.macAddress.equals(b.macAddress, true)) || a.ip == b.ip
    // Searching is only shown after the user asks to add a TV.
    var adding by rememberSaveable { mutableStateOf(false) }
    // TV being named: a new one (connect after naming) or a saved one (rename only).
    var naming by remember { mutableStateOf<Pair<SamsungTv, Boolean>?>(null) }
    naming?.let { (tv, isNew) ->
        NameTvDialog(
            tv, isNew,
            onDismiss = { naming = null },
            onConfirm = { label ->
                naming = null
                if (isNew) {
                    actions.connect(tv.copy(label = label))
                    adding = false
                    onChosen()
                } else {
                    actions.renameTv(tv, label)
                }
            }
        )
    }

    if (saved.isEmpty()) {
        Label("Aún no hay televisores guardados. Añade uno para empezar.", 20, InkMuted)
    }
    saved.forEachIndexed { i, entry ->
        if (i > 0) Spacer(Modifier.height(d.dp(12)))
        val current = selectedTv?.let { same(it, entry.tv) } == true
        val state = when {
            !current -> TvRowState.Idle
            connected -> TvRowState.Connected
            connecting -> TvRowState.Connecting
            asleep -> TvRowState.Asleep
            else -> TvRowState.Idle
        }
        val what = (if (entry.tv.label != null) entry.tv.name else entry.tv.model.ifBlank { entry.tv.ip }) +
            (entry.tv.ssid?.let { " · $it" } ?: "")
        TvRow(
            entry.tv, state,
            if (current && state != TvRowState.Idle) what else "$what · ${lastUsed(entry.lastConnected)}",
            trailingIcon = "trash", trailingDescription = "Quitar ${entry.tv.displayName}",
            onTrailing = { actions.forgetSaved(entry.tv) },
            onEdit = { naming = entry.tv to false }
        ) { actions.connect(entry.tv); onChosen() }
    }
    Spacer(Modifier.height(d.dp(if (saved.isEmpty()) 16 else 20)))
    Row(
        Modifier.fillMaxWidth().height(d.dp(70)).clip(RoundedCornerShape(d.dp(35)))
            .background(pal.fg.copy(.055f))
            .border(1.dp, pal.fg.copy(.07f), RoundedCornerShape(d.dp(35)))
            .pressable(description = "Añadir televisor", onClick = { adding = true; actions.discover() }),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (scanning) Spinner(Modifier.size(d.dp(26))) else Icon("plus", Modifier.size(d.dp(24)), Ink)
        Spacer(Modifier.width(d.dp(16)))
        Label(if (scanning) "Buscando televisores…" else "Añadir televisor", 23)
    }

    if (adding && !scanning) {
        val fresh = tvs.filter { tv -> saved.none { same(it.tv, tv) } }
        Spacer(Modifier.height(d.dp(14)))
        Label(
            if (fresh.isEmpty()) "No se encontraron televisores nuevos. Comprueba que estén encendidos y en la misma Wi-Fi."
            else "Elige el televisor que quieres añadir:",
            20, InkMuted
        )
        fresh.forEach { tv ->
            Spacer(Modifier.height(d.dp(12)))
            TvRow(
                tv, TvRowState.New, "${tv.model} · ${tv.ip}",
                trailingIcon = "close", trailingDescription = "Cerrar resultados",
                onTrailing = { adding = false }
            ) { naming = tv to true }
        }

        // Manual IP entry also works when SSDP/broadcast discovery is blocked
        // between the main router and a Wi-Fi repeater.
        var manualIp by rememberSaveable { mutableStateOf("") }
        Spacer(Modifier.height(d.dp(20)))
        Label("¿No aparece? Introduce la IP de la TV", 20, InkMuted)
        Spacer(Modifier.height(d.dp(10)))
        Row(
            Modifier.fillMaxWidth().height(d.dp(68))
                .background(pal.fg.copy(.055f), RoundedCornerShape(d.dp(22)))
                .border(1.dp, pal.fg.copy(.08f), RoundedCornerShape(d.dp(22)))
                .padding(horizontal = d.dp(18)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = manualIp,
                onValueChange = { value -> manualIp = value.filter { it.isDigit() || it == '.' }.take(15) },
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = d.sp(22)),
                cursorBrush = SolidColor(Ink),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (manualIp.isEmpty()) Label("Ej. 192.168.1.50", 21, InkMuted)
                        inner()
                    }
                }
            )
            Spacer(Modifier.width(d.dp(12)))
            Text(
                "Conectar", color = Ink, fontSize = d.sp(20), fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(d.dp(18)))
                    .background(pal.fg.copy(.12f))
                    .pressable(description = "Conectar por IP", onClick = {
                        val parts = manualIp.trim().split(".")
                        val valid = parts.size == 4 && parts.all { part ->
                            part.isNotEmpty() && part.toIntOrNull()?.let { it in 0..255 } == true
                        }
                        if (valid) {
                            val ip = parts.joinToString(".") { it.toInt().toString() }
                            actions.connect(SamsungTv(ip, "Samsung Smart TV", "IP manual"))
                            adding = false
                            onChosen()
                        }
                    })
                    .padding(horizontal = d.dp(20), vertical = d.dp(14))
            )
        }
    }
}

private fun lastUsed(time: Long): String {
    if (time <= 0) return "sin fecha"
    val mins = (System.currentTimeMillis() - time) / 60_000
    return when {
        mins < 1 -> "ahora mismo"
        mins < 60 -> "hace $mins min"
        mins < 24 * 60 -> "hace ${mins / 60} h"
        mins < 48 * 60 -> "ayer"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale("es")).format(java.util.Date(time))
    }
}

private enum class TvRowState { Idle, Connected, Connecting, Asleep, New }

/** One television: tap the row to connect; the round button on the right removes it. */
@Composable
private fun TvRow(
    tv: SamsungTv, state: TvRowState, subtitle: String,
    trailingIcon: String, trailingDescription: String, onTrailing: () -> Unit,
    onEdit: (() -> Unit)? = null, onConnect: () -> Unit
) {
    val d = LocalDs.current
    val active = state == TvRowState.Connected || state == TvRowState.Connecting || state == TvRowState.Asleep
    val shape = RoundedCornerShape(d.dp(26))
    var pressed by remember { mutableStateOf(false) }
    val k by animateFloatAsState(if (pressed) .98f else 1f, label = "tvrow")
    Row(
        Modifier.fillMaxWidth().scale(k).clip(shape)
            .background(pal.fg.copy(if (active) .07f else .035f))
            .background(if (pressed) pal.fg.copy(.05f) else Color.Transparent)
            .border(1.dp, pal.fg.copy(if (active) .13f else .06f), shape)
            .pressable(description = "Conectar con ${tv.displayName}", onPressedChange = { pressed = it }, onClick = onConnect)
            .padding(start = d.dp(20), end = d.dp(12), top = d.dp(18), bottom = d.dp(18)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(d.dp(66)).background(pal.fg.copy(if (active) .10f else .06f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon("tv", Modifier.size(d.dp(32), d.dp(28)), if (active) Ink else InkMuted)
        }
        Spacer(Modifier.width(d.dp(20)))
        Column(Modifier.weight(1f)) {
            Text(tv.displayName, color = Ink, fontSize = d.sp(25), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(d.dp(4)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (dot, label) = when (state) {
                    TvRowState.Connected -> OnlineGreen to "Conectado"
                    TvRowState.Connecting -> Color(0xFFF5A623) to "Conectando…"
                    TvRowState.Asleep -> DotOff to "En reposo"
                    else -> null to null
                }
                if (dot != null && label != null) {
                    Box(Modifier.size(d.dp(10)).background(dot, CircleShape))
                    Spacer(Modifier.width(d.dp(8)))
                    Text(label, color = Ink.copy(.85f), fontSize = d.sp(20), fontWeight = FontWeight.Medium)
                    Text("  ·  ", color = InkMuted, fontSize = d.sp(20))
                }
                Text(subtitle, color = InkMuted, fontSize = d.sp(20), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (state == TvRowState.New) {
            Spacer(Modifier.width(d.dp(10)))
            Text(
                "Añadir", color = Ink, fontSize = d.sp(21), fontWeight = FontWeight.SemiBold,
                modifier = Modifier.background(pal.fg.copy(.08f), RoundedCornerShape(d.dp(24)))
                    .padding(horizontal = d.dp(20), vertical = d.dp(12))
            )
        }
        Spacer(Modifier.width(d.dp(6)))
        if (onEdit != null) Box(
            Modifier.size(d.dp(58)).clip(CircleShape)
                .pressable(description = "Cambiar nombre de ${tv.displayName}", onClick = onEdit),
            contentAlignment = Alignment.Center
        ) { Icon("edit", Modifier.size(d.dp(24)), InkMuted) }
        Box(
            Modifier.size(d.dp(58)).clip(CircleShape)
                .pressable(description = trailingDescription, onClick = onTrailing),
            contentAlignment = Alignment.Center
        ) { Icon(trailingIcon, Modifier.size(d.dp(24)), InkMuted) }
    }
}

private val RoomSuggestions = listOf("Salón", "Dormitorio", "Habitación", "Cocina", "Despacho", "Comedor")

/** Asks for a note so each TV is easy to recognise ("Tele salón"). */
@Composable
private fun NameTvDialog(tv: SamsungTv, isNew: Boolean, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    val d = LocalDs.current
    var text by remember { mutableStateOf(tv.label ?: "") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(pal.dialogBg, RoundedCornerShape(d.dp(40)))
                .border(1.dp, pal.fg.copy(.10f), RoundedCornerShape(d.dp(40))).padding(d.dp(34))
        ) {
            Text(
                if (isNew) "¿Dónde está esta tele?" else "Nombre de la tele",
                color = Ink, fontSize = d.sp(34), fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(d.dp(6)))
            Label("${tv.name} · ${tv.model.ifBlank { tv.ip }}", 21, InkMuted)
            Spacer(Modifier.height(d.dp(26)))
            Box(
                Modifier.fillMaxWidth().height(d.dp(84)).background(pal.fg.copy(.05f), RoundedCornerShape(d.dp(24)))
                    .border(1.dp, pal.fg.copy(.12f), RoundedCornerShape(d.dp(24))).padding(horizontal = d.dp(26)),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) Label("Ej.: Tele salón", 25, InkMuted)
                BasicTextField(
                    text, { text = it.take(30) }, singleLine = true,
                    textStyle = TextStyle(color = Ink, fontSize = d.sp(26)),
                    cursorBrush = SolidColor(Ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(text.trim().ifEmpty { null }) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(d.dp(18)))
            // Quick picks: one tap fills a sensible name.
            RoomSuggestions.chunked(3).forEach { row ->
                Row(Modifier.padding(bottom = d.dp(12)), horizontalArrangement = Arrangement.spacedBy(d.dp(12))) {
                    row.forEach { room ->
                        val value = "Tele ${room.lowercase()}"
                        Segment(room, text == value, Modifier.weight(1f)) { text = value }
                    }
                }
            }
            Spacer(Modifier.height(d.dp(18)))
            Row(horizontalArrangement = Arrangement.spacedBy(d.dp(16))) {
                PillButton(if (isNew) "Omitir" else "Cancelar", Modifier.weight(1f)) {
                    if (isNew) onConfirm(null) else onDismiss()
                }
                PillButton(if (isNew) "Añadir" else "Guardar", Modifier.weight(1f)) { onConfirm(text.trim().ifEmpty { null }) }
            }
        }
    }
}

@Composable
private fun Spinner(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
    val ink = Ink
    Canvas(modifier) {
        drawArc(ink, angle, 270f, false, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun TvPickerDialog(state: RemoteUiState, actions: RemoteActions, onDismiss: () -> Unit) {
    val d = LocalDs.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(pal.dialogBg, RoundedCornerShape(d.dp(40)))
                .border(1.dp, pal.fg.copy(.10f), RoundedCornerShape(d.dp(40)))
                .padding(d.dp(32)).verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Elegir televisor", color = Ink, fontSize = d.sp(34), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(d.dp(58)).clip(CircleShape).background(pal.fg.copy(.06f))
                        .pressable(description = "Cerrar", onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { Icon("close", Modifier.size(d.dp(22)), Ink) }
            }
            Spacer(Modifier.height(d.dp(24)))
            TvList(
                state.scanning, state.tvs, state.savedTvs, state.selectedTv, actions, onChosen = onDismiss,
                connected = state.connected, connecting = state.connecting || state.powerBusy, asleep = state.tvAsleep
            )
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
private fun BottomNav(selected: Int, settingsBadge: Boolean = false, onSelect: (Int) -> Unit) {
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
            NavItem(icon, NavLabels[i], i == selected, Modifier.at(d, i * slot, 0, slot, 105), badge = settingsBadge && i == 3) { onSelect(i) }
        }
    }
}

@Composable
private fun NavItem(icon: String, label: String, selected: Boolean, modifier: Modifier, badge: Boolean = false, onClick: () -> Unit) {
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
        if (badge) Box(
            Modifier.offset(d.dp(18), d.dp(-18)).size(d.dp(12)).background(OnlineGreen, CircleShape)
                .border(d.dp(2), pal.navBg, CircleShape)
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
            "wifi" -> {
                drawArc(tint, 225f, 90f, false, p(.04f, .14f), Size(w * .92f, h * .92f), style = stroke)
                drawArc(tint, 225f, 90f, false, p(.20f, .32f), Size(w * .60f, h * .60f), style = stroke)
                drawCircle(tint, m * .08f, p(.5f, .80f))
            }
            "edit" -> {
                poly(.16f to .84f, .20f to .64f, .66f to .18f, .82f to .34f, .36f to .80f, close = true)
                line(.56f, .28f, .72f, .44f)
            }
            "check" -> poly(.16f to .52f, .40f to .76f, .86f to .26f)
            "trash" -> {
                line(.14f, .24f, .86f, .24f)
                poly(.38f to .24f, .40f to .10f, .60f to .10f, .62f to .24f)
                poly(.22f to .24f, .28f to .92f, .72f to .92f, .78f to .24f)
                line(.42f, .40f, .42f, .76f); line(.58f, .40f, .58f, .76f)
            }
            "close" -> { line(.18f, .18f, .82f, .82f); line(.82f, .18f, .18f, .82f) }
            "refresh" -> {
                drawArc(tint, -60f, 300f, false, p(.12f, .12f), Size(w * .76f, h * .76f), style = stroke)
                poly(.62f to .06f, .84f to .16f, .74f to .38f)
            }
            "theme" -> {
                drawCircle(tint, m * .38f, p(.5f, .5f), style = stroke)
                drawCircle(tint, m * .075f, p(.34f, .34f))
                drawCircle(tint, m * .075f, p(.66f, .34f))
                drawCircle(tint, m * .075f, p(.34f, .66f))
                drawCircle(tint, m * .075f, p(.66f, .66f))
            }
            "download" -> {
                line(.5f, .08f, .5f, .62f)
                poly(.22f to .42f, .5f to .70f, .78f to .42f)
                poly(.12f to .78f, .12f to .92f, .88f to .92f, .88f to .78f)
            }
            "info" -> {
                drawCircle(tint, m * .42f, p(.5f, .5f), style = stroke)
                drawCircle(tint, m * .045f, p(.5f, .32f))
                line(.5f, .46f, .5f, .72f)
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
