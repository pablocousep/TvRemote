package com.reelog.tvremote

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
 * Renders every screen to PNG (build/screenshots) so each CI build shows what changed,
 * and fails if any screen crashes while composing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val dir = System.getProperty("screenshots.dir") ?: "build/screenshots"
    private val tv = SamsungTv("192.168.1.20", "Samsung QN95A", "QE65QN95AATXXC", "AA:BB:CC:DD:EE:FF")
    private val actions = RemoteActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private fun show(state: RemoteUiState = RemoteUiState(connected = true, tvs = listOf(tv), selectedTv = tv)) =
        rule.setContent { RemoteScreen(state, actions) }

    private fun shot(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("$dir/$name.png")
    }

    @Test fun remote() { show(); shot("01-remote") }

    @Test fun numbers() {
        show()
        rule.onNodeWithContentDescription("Cruceta de navegación").performTouchInput { swipeLeft() }
        shot("02-numbers")
    }

    @Test fun apps() {
        show(RemoteUiState(connected = true, selectedTv = tv, pinnedApps = TvApps.take(2)))
        rule.onNodeWithContentDescription("Aplicaciones").performClick(); shot("03-apps")
    }

    @Test fun cast() { show(); rule.onNodeWithContentDescription("Transmitir").performClick(); shot("04-cast") }

    @Test fun settings() {
        show(RemoteUiState(
            connected = true, selectedTv = tv,
            savedTvs = listOf(
                SavedTv(tv.copy(label = "Tele salón"), System.currentTimeMillis() - 3_600_000),
                SavedTv(SamsungTv("192.168.1.40", "Samsung Q60", "QE43Q60", label = "Tele dormitorio"), System.currentTimeMillis() - 90_000_000)
            ),
            tvs = listOf(SamsungTv("192.168.1.31", "Samsung The Frame", "QE55LS03BAUXXC"))
        ))
        rule.onNodeWithContentDescription("Ajustes").performClick()
        shot("05-settings")
    }

    @Test fun nameDialogAndActivity() {
        show(RemoteUiState(selectedTv = tv.copy(label = "Tele salón"), connecting = true, activity = "Conectando a Tele salón…"))
        shot("08-connecting")
    }

    @Test fun lightTheme() {
        show(RemoteUiState(connected = true, selectedTv = tv, theme = "light"))
        shot("06-remote-light")
    }

    @Test fun connectionLost() {
        show(RemoteUiState(selectedTv = tv, connectionLost = true))
        shot("07-connection-lost")
    }
}
