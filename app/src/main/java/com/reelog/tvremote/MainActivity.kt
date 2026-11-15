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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private val powerLog = mutableStateListOf<String>()
    private var powerJob: Job? = null
    private val powerBusy = mutableStateOf(false)
    private lateinit var tvPrefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goImmersive()

        tvPrefs = getSharedPreferences("tvremote_selected_tv", Context.MODE_PRIVATE)
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
            setHaptics = { on -> haptics.value = on; settings.edit().putBoolean("haptics", on).apply() },
            probeTv = { probeTv() },
            wakeTest = { wakeTest() },
            saveMac = { saveMacFromUser(it) },
            clearLog = { powerLog.clear() }
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
                powerLog = powerLog,
                powerBusy = powerBusy.value,
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

    private fun log(line: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        powerLog.add(0, "$time  $line")
        while (powerLog.size > 60) powerLog.removeAt(powerLog.lastIndex)
    }

    private fun saveMac(tv: SamsungTv, mac: String) {
        if (tv.macAddress.equals(mac, ignoreCase = true)) return
        val updated = tv.copy(macAddress = mac)
        selectedTv.value = updated
        tvPrefs.edit().putString("mac", mac).apply()
        log("MAC guardada: $mac")
    }

    private fun saveMacFromUser(input: String) {
        val tv = selectedTv.value ?: return setStatus("Elige primero un televisor")
        val mac = WakeOnLan.normalizeMac(input) ?: return setStatus("MAC no válida. Formato: AA:BB:CC:DD:EE:FF")
        saveMac(tv, mac)
        setStatus("MAC guardada")
    }

    private suspend fun awaitConnected(timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (remote.isConnected()) return true
            delay(150)
        }
        return remote.isConnected()
    }

    private fun launchPowerJob(block: suspend () -> Unit) {
        if (powerJob?.isActive == true) return
        powerBusy.value = true
        powerJob = lifecycleScope.launch {
            try { block() } finally { powerJob = null; powerBusy.value = false }
        }
    }

    /**
     * Power button.
     *  - Connected: KEY_POWER (turns the TV off, or on from network standby).
     *  - TV answers on its REST API but we are not connected: it is on, or in a standby that
     *    keeps the network alive. KEY_POWER toggles both, so connect and send it.
     *  - TV does not answer: deep standby. Send Wake-on-LAN rounds until it answers, connect,
     *    and if it only woke its network (PowerState = standby) send KEY_POWER to light the screen.
     */
    private fun powerToggle() {
        if (remote.isConnected()) {
            log("Conectado: envío KEY_POWER")
            remote.sendKey("KEY_POWER")
            return
        }
        val tv = selectedTv.value ?: return setStatus("Elige primero un televisor")
        launchPowerJob {
            setStatus("Comprobando el televisor…")
            val probe = WakeOnLan.probe(tv.ip)
            if (probe.reachable) {
                log("La TV responde (estado: ${probe.powerState ?: "no informado"}, red: ${probe.networkType ?: "?"})")
                probe.mac?.let { saveMac(tv, it) }
                remote.connect(tv.ip)
                if (awaitConnected(8_000)) {
                    log("Conectado: envío KEY_POWER")
                    remote.sendKey("KEY_POWER")
                } else {
                    log("No se pudo abrir el canal de control")
                }
                return@launchPowerJob
            }
            val mac = selectedTv.value?.macAddress
            if (mac == null) {
                log("La TV no responde y no hay MAC guardada")
                setStatus("Conecta la TV encendida una vez (o escribe su MAC en Ajustes) para poder encenderla.")
                return@launchPowerJob
            }
            log("La TV no responde: envío Wake-on-LAN a $mac")
            setStatus("Encendiendo el televisor…")
            val woke = WakeOnLan.wakeAndWait(this, mac, tv.ip, log = ::log)
            if (woke == null) {
                setStatus("La TV no respondió. Activa \"Encender con móvil\" en Ajustes > General > Red > Configuración experta de la TV.")
                return@launchPowerJob
            }
            woke.mac?.let { saveMac(tv, it) }
            remote.connect(tv.ip)
            val up = awaitConnected(10_000)
            if (woke.powerState == "standby" && up) {
                log("Red despierta pero pantalla apagada: envío KEY_POWER")
                remote.sendKey("KEY_POWER")
            }
            setStatus("Televisor encendido")
        }
    }

    private fun probeTv() {
        val tv = selectedTv.value ?: return setStatus("Elige primero un televisor")
        launchPowerJob {
            log("Comprobando ${tv.ip}…")
            val p = WakeOnLan.probe(tv.ip, 1500)
            if (p.reachable) {
                log("Responde · estado: ${p.powerState ?: "no informado"} · red: ${p.networkType ?: "?"} · MAC: ${p.mac ?: "?"}")
                p.mac?.let { saveMac(tv, it) }
            } else {
                log("No responde (apagada del todo, en reposo profundo o IP cambiada)")
            }
        }
    }

    private fun wakeTest() {
        val tv = selectedTv.value ?: return setStatus("Elige primero un televisor")
        val mac = tv.macAddress ?: return setStatus("No hay MAC guardada")
        launchPowerJob {
            log("Prueba Wake-on-LAN → $mac")
            WakeOnLan.wakeAndWait(this, mac, tv.ip, log = ::log)
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
