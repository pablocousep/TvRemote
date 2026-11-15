package com.reelog.tvremote

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var remote: SamsungRemote
    private lateinit var discovery: SamsungTvDiscovery

    private val status = mutableStateOf("")
    private val statusSerial = mutableIntStateOf(0)
    private val connected = mutableStateOf(false)
    private val scanning = mutableStateOf(false)
    private val foundTvs = mutableStateListOf<SamsungTv>()
    private val selectedTv = mutableStateOf<SamsungTv?>(null)
    private val haptics = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goImmersive()

        val tvPrefs = getSharedPreferences("tvremote_selected_tv", Context.MODE_PRIVATE)
        val settings = getSharedPreferences("tvremote_settings", Context.MODE_PRIVATE)
        haptics.value = settings.getBoolean("haptics", true)
        selectedTv.value = tvPrefs.getString("ip", null)?.let { ip ->
            SamsungTv(
                ip,
                tvPrefs.getString("name", "Samsung Smart TV") ?: "Samsung Smart TV",
                tvPrefs.getString("model", "") ?: "",
                tvPrefs.getString("mac", null)
            )
        }

        remote = SamsungRemote(
            applicationContext,
            onStatus = { message -> runOnUiThread { setStatus(message) } },
            onConnectionChanged = { isUp -> runOnUiThread { connected.value = isUp } }
        )
        discovery = SamsungTvDiscovery(applicationContext)

        val actions = RemoteActions(
            key = { remote.sendKey(it) },
            text = { remote.sendText(it) },
            launchApp = { remote.launchApp(it) },
            power = { powerToggle() },
            discover = { discover() },
            connect = { tv ->
                selectedTv.value = tv
                tvPrefs.edit().putString("ip", tv.ip).putString("name", tv.name)
                    .putString("model", tv.model).putString("mac", tv.macAddress).apply()
                remote.connect(tv.ip)
            },
            forget = { remote.forgetPairing(it.ip) },
            disconnect = { remote.close(); setStatus("Desconectado") },
            openCastSettings = { openCastSettings() },
            setHaptics = { on -> haptics.value = on; settings.edit().putBoolean("haptics", on).apply() }
        )

        setContent {
            RemoteScreen(
                status = status.value,
                statusSerial = statusSerial.intValue,
                connected = connected.value,
                scanning = scanning.value,
                tvs = foundTvs,
                selectedTv = selectedTv.value,
                haptics = haptics.value,
                actions = actions
            )
        }

        selectedTv.value?.let { remote.connect(it.ip) }
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

    private fun setStatus(message: String) {
        status.value = message
        statusSerial.intValue++
    }

    private fun discover() {
        if (scanning.value) return
        scanning.value = true
        setStatus("Buscando televisores en tu red local…")
        lifecycleScope.launch {
            try {
                val result = discovery.discover()
                foundTvs.clear()
                foundTvs.addAll(result)
                setStatus(
                    if (result.isEmpty()) "No se encontraron televisores. Comprueba que estén encendidos y en la misma Wi-Fi."
                    else "${result.size} televisor(es) encontrado(s)"
                )
            } catch (e: Exception) {
                setStatus("Error al buscar: ${e.localizedMessage ?: "red no disponible"}")
            } finally {
                scanning.value = false
            }
        }
    }

    private fun powerToggle() {
        val tv = selectedTv.value
        when {
            remote.isConnected() -> remote.sendKey("KEY_POWER")
            tv?.macAddress != null -> {
                setStatus("Enviando señal para encender el televisor…")
                lifecycleScope.launch {
                    val result = WakeOnLan.wake(tv.macAddress, tv.ip)
                    setStatus(
                        if (result.isSuccess) "Señal Wake-on-LAN enviada. Si no enciende, activa Encendido por red en el televisor."
                        else "No se pudo enviar la señal: " + (result.exceptionOrNull()?.localizedMessage ?: "error de red")
                    )
                }
            }
            else -> setStatus("Conecta la TV encendida una vez para guardar su dirección MAC y poder despertarla.")
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
        setStatus("Abre Smart View desde el panel rápido del móvil para duplicar la pantalla.")
    }

    override fun onDestroy() {
        if (::remote.isInitialized) remote.close()
        super.onDestroy()
    }
}
