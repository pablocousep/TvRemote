package com.reelog.tvremote

import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Raw screens for the Google Play listing (framed later by fastlane/store-art). They use
 * neutral sample apps, without third-party logos, which Play does not allow in listings.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class StoreScreens {
    @get:Rule val rule = createComposeRule()

    private val dir = (System.getProperty("screenshots.dir") ?: "build/screenshots") + "/store"
    private val salon = SamsungTv("192.168.1.20", "QN95A", "QE65QN95A", "AA:BB:CC:DD:EE:FF", "Tele salón", "Casa")
    private val dormitorio = SamsungTv("192.168.1.40", "Q60", "QE43Q60", "AA:BB:CC:00:11:22", "Tele dormitorio", "Casa")
    private val apps = listOf(
        TvApp("s1", "Navegador"), TvApp("s2", "Galería"), TvApp("s3", "Música"), TvApp("s4", "Deportes"),
        TvApp("s5", "Noticias"), TvApp("s6", "Juegos"), TvApp("s7", "Fitness"), TvApp("s8", "El tiempo")
    )
    private val actions = RemoteActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    private val base = RemoteUiState(
        connected = true, selectedTv = salon, installedApps = apps, pinnedApps = apps.take(3),
        savedTvs = listOf(SavedTv(salon, System.currentTimeMillis()), SavedTv(dormitorio, System.currentTimeMillis() - 90_000_000)),
        versionName = "1.0"
    )

    private fun show(state: RemoteUiState = base) = rule.setContent { RemoteScreen(state, actions) }
    private fun shot(name: String) { rule.waitForIdle(); rule.onRoot().captureRoboImage("$dir/$name.png") }

    @Test fun s1Remote() { show(); shot("1-remote") }
    @Test fun s2Apps() { show(); rule.onNodeWithContentDescription("Aplicaciones").performClick(); shot("2-apps") }
    @Test fun s3Tvs() { show(); rule.onNodeWithContentDescription("Ajustes").performClick(); shot("3-settings") }
    @Test fun s4Power() {
        show(base.copy(connected = false, tvAsleep = true, wolGuide = true))
        rule.waitForIdle(); rule.onNode(isDialog()).captureRoboImage("$dir/4-power.png")
    }
    @Test fun s5Numbers() {
        show(); rule.onNodeWithContentDescription("Cruceta de navegación").performTouchInput { swipeLeft() }; shot("5-numbers")
    }
    @Test fun s6Light() { show(base.copy(theme = "light")); shot("6-light") }
}
