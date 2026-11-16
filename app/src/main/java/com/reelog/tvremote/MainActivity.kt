package com.reelog.tvremote

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private val vm: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goImmersive()

        val actions = RemoteActions(
            key = vm::key,
            text = vm::text,
            launchApp = vm::launchApp,
            power = vm::power,
            discover = vm::discover,
            connect = { vm.connect(it) },
            forget = vm::forget,
            disconnect = vm::disconnect,
            openCastSettings = ::openCastSettings,
            setHaptics = vm::setHaptics,
            reconnect = { vm.reconnect() },
            togglePin = vm::togglePin,
            setTheme = vm::setTheme,
            checkForUpdates = { vm.checkForUpdates() },
            startUpdate = vm::startUpdate,
            dismissUpdate = vm::dismissUpdate,
            forgetSaved = vm::forgetSaved
        )

        setContent {
            val state = RemoteUiState(
                status = vm.status.value,
                statusSerial = vm.statusSerial.intValue,
                connected = vm.connected.value,
                connecting = vm.connecting.value,
                connectionLost = vm.connectionLost.value,
                scanning = vm.scanning.value,
                tvs = vm.foundTvs.toList(),
                selectedTv = vm.selectedTv.value,
                haptics = vm.haptics.value,
                theme = vm.theme.value,
                powerBusy = vm.powerBusy.value,
                installedApps = vm.installedApps.toList(),
                pinnedApps = vm.pinnedApps.toList(),
                appIcons = vm.appIcons.toMap(),
                versionName = vm.versionName,
                update = vm.update.value,
                updatePrompt = vm.updatePrompt.value,
                updateChecking = vm.updateChecking.value,
                updateProgress = vm.updateProgress.value,
                savedTvs = vm.savedTvs.toList(),
                tvAsleep = vm.tvAsleep.value
            )
            RemoteScreen(state, actions)
        }
    }

    /** Phone volume keys drive the TV volume while the remote is open and connected. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (vm.hardwareVolume.value && vm.isConnected()) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> { vm.key("KEY_VOLUP"); return true }
                KeyEvent.KEYCODE_VOLUME_DOWN -> { vm.key("KEY_VOLDOWN"); return true }
                KeyEvent.KEYCODE_VOLUME_MUTE -> { vm.key("KEY_MUTE"); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (vm.hardwareVolume.value && vm.isConnected() &&
            keyCode in intArrayOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)
        ) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    private fun goImmersive() {
        window.setBackgroundDrawableResource(android.R.color.black)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun openCastSettings() {
        val intents = listOf(
            Intent(Settings.ACTION_CAST_SETTINGS),
            Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )
        for (intent in intents) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
        vm.setStatus("Abre Smart View desde el panel rápido del móvil para duplicar la pantalla.")
    }
}
