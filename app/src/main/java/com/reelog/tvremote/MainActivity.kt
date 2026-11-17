package com.reelog.tvremote

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
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
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    /** Location is only needed so Android reveals the Wi-Fi name (to remember each TV's network). */
    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> if (granted.values.any { it }) vm.onLocationGranted() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.SELF_UPDATE && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        goImmersive()

        val actions = RemoteActions(
            key = vm::key,
            text = vm::text,
            launchApp = vm::launchApp,
            power = vm::power,
            discover = vm::discover,
            connect = { tv -> askLocationOnce(); vm.connect(tv) },
            forget = vm::forget,
            disconnect = vm::disconnect,
            openCastSettings = ::openCastSettings,
            setHaptics = vm::setHaptics,
            mouseMove = vm::mouseMove,
            mouseClick = vm::mouseClick,
            reconnect = { vm.reconnect() },
            togglePin = vm::togglePin,
            setTheme = vm::setTheme,
            checkForUpdates = { vm.checkForUpdates() },
            startUpdate = vm::startUpdate,
            dismissUpdate = vm::dismissUpdate,
            forgetSaved = vm::forgetSaved,
            renameTv = vm::renameTv,
            openWifiPanel = { WifiNetworks.openWifiPanel(this) },
            dismissWifiMismatch = vm::dismissWifiMismatch,
            showWolGuide = vm::showWolGuide
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
                selfUpdate = BuildConfig.SELF_UPDATE,
                update = vm.update.value,
                updatePrompt = vm.updatePrompt.value,
                updateChecking = vm.updateChecking.value,
                updateProgress = vm.updateProgress.value,
                savedTvs = vm.savedTvs.toList(),
                tvAsleep = vm.tvAsleep.value,
                activity = vm.activity.value,
                wifiMismatch = vm.wifiMismatch.value,
                wolGuide = vm.wolGuide.value
            )
            RemoteScreen(state, actions)
        }
    }

    /** Asks for location the first time the user connects to a TV, never again after an answer. */
    private fun askLocationOnce() {
        if (WifiNetworks.hasLocationPermission(this)) return
        val prefs = getSharedPreferences("tvremote_settings", MODE_PRIVATE)
        if (prefs.getBoolean("asked_location", false)) return
        prefs.edit().putBoolean("asked_location", true).apply()
        locationPermission.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
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

    /**
     * Samsung TVs receive screen mirroring through Smart View (Miracast), not Google Cast, so
     * Android's generic Cast settings would never find the TV. Open Samsung's Smart View on
     * Samsung phones, the Miracast screen on phones that still have one, else explain.
     */
    private fun openCastSettings() {
        val smartView = packageManager.getLaunchIntentForPackage("com.samsung.android.smartmirroring")
        val miracast = Intent("android.settings.WIFI_DISPLAY_SETTINGS")
        val candidates = listOfNotNull(smartView, miracast.takeIf { it.resolveActivity(packageManager) != null })
        for (intent in candidates) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
        vm.setStatus(
            if (Build.MANUFACTURER.equals("samsung", ignoreCase = true))
                "Desliza el panel rápido y toca Smart View para duplicar la pantalla."
            else "Este móvil no tiene Miracast. La TV Samsung no admite Google Cast para duplicar la pantalla."
        )
    }
}
