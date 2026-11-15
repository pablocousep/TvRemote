package com.reelog.tvremote

import android.content.Context
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** Everything the app remembers between launches. */
class TvStore(context: Context) {
    private val tvPrefs = context.getSharedPreferences("tvremote_selected_tv", Context.MODE_PRIVATE)
    private val prefs = context.getSharedPreferences("tvremote_settings", Context.MODE_PRIVATE)

    var selectedTv: SamsungTv?
        get() = tvPrefs.getString("ip", null)?.let { ip ->
            SamsungTv(
                ip,
                tvPrefs.getString("name", "Samsung Smart TV") ?: "Samsung Smart TV",
                tvPrefs.getString("model", "") ?: "",
                tvPrefs.getString("mac", null)
            )
        }
        set(tv) {
            if (tv == null) tvPrefs.edit().clear().apply()
            else tvPrefs.edit().putString("ip", tv.ip).putString("name", tv.name)
                .putString("model", tv.model).putString("mac", tv.macAddress).apply()
        }

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(v) = prefs.edit().putBoolean("haptics", v).apply()

    /** Phone volume keys control the TV while the app is open. */
    var hardwareVolume: Boolean
        get() = prefs.getBoolean("hw_volume", true)
        set(v) = prefs.edit().putBoolean("hw_volume", v).apply()

    /** "dark", "light" or "system". */
    var theme: String
        get() = prefs.getString("theme", "dark") ?: "dark"
        set(v) = prefs.edit().putString("theme", v).apply()

    /** The three shortcuts on the main page. */
    var pinnedApps: List<TvApp>
        get() = prefs.getString("pinned_apps", null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.getJSONObject(it).let { o -> TvApp(o.getString("id"), o.getString("name")) } }
            }.getOrNull()
        } ?: TvApps.take(3)
        set(v) = prefs.edit().putString(
            "pinned_apps",
            JSONArray(v.take(3).map { JSONObject().put("id", it.id).put("name", it.name) }).toString()
        ).apply()

    var smartThingsToken: String?
        get() = prefs.getString("st_token", null)?.takeIf { it.isNotBlank() }
        set(v) = prefs.edit().putString("st_token", v?.trim()).apply()

    var smartThingsDevice: StDevice?
        get() = prefs.getString("st_device_id", null)?.let { StDevice(it, prefs.getString("st_device_name", "") ?: "") }
        set(v) = prefs.edit().putString("st_device_id", v?.id).putString("st_device_name", v?.name).apply()
}

/**
 * Connection and power logic shared by the app, the home-screen widget and the
 * Quick Settings tile.
 */
class TvSession(
    private val context: Context,
    private val store: TvStore,
    val remote: SamsungRemote,
    private val log: (String) -> Unit = {},
    private val status: (String) -> Unit = {},
    private val onTvChanged: (SamsungTv) -> Unit = {}
) {
    private val discovery = SamsungTvDiscovery(context)

    suspend fun awaitConnected(timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (remote.isConnected()) return true
            delay(120)
        }
        return remote.isConnected()
    }

    /** Connects to [tv]; if it does not answer, looks for it at a new IP (DHCP change). */
    suspend fun connect(tv: SamsungTv, relocate: Boolean = true): Boolean {
        remote.connect(tv.ip)
        if (awaitConnected(7_000)) return true
        if (!relocate) return false
        val moved = relocate(tv) ?: return false
        remote.connect(moved.ip)
        return awaitConnected(7_000)
    }

    /** Finds the same TV (by MAC, else by name + model) after its IP changed. */
    suspend fun relocate(tv: SamsungTv): SamsungTv? {
        log("Buscando la TV por si ha cambiado de IP…")
        val found = runCatching { discovery.discover() }.getOrDefault(emptyList())
        val byMac = found.firstOrNull { tv.macAddress != null && it.macAddress.equals(tv.macAddress, ignoreCase = true) }
        val match = byMac ?: found.firstOrNull { it.name == tv.name && it.model == tv.model }
        if (match == null || match.ip == tv.ip) {
            log(if (match == null) "No se encontró la TV en la red" else "La TV sigue en ${tv.ip}")
            return null
        }
        // Only carry the token and certificate pin over when the MAC proves it is the same TV.
        if (byMac != null) remote.migratePairing(tv.ip, match.ip)
        val updated = match.copy(macAddress = match.macAddress ?: tv.macAddress)
        store.selectedTv = updated
        onTvChanged(updated)
        log("TV encontrada en su nueva IP ${match.ip}")
        return updated
    }

    suspend fun sendKey(key: String): Boolean {
        val tv = store.selectedTv ?: return false
        if (!remote.isConnected() && !connect(tv)) return false
        remote.sendKey(key)
        return true
    }

    private fun learnMac(tv: SamsungTv, mac: String?) {
        if (mac == null || tv.macAddress.equals(mac, ignoreCase = true)) return
        val updated = tv.copy(macAddress = mac)
        store.selectedTv = updated
        onTvChanged(updated)
        log("MAC guardada: $mac")
    }

    private suspend fun waitReachable(ip: String, timeoutMs: Long): TvProbe? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val p = WakeOnLan.probe(ip, 700)
            if (p.reachable) return p
            delay(600)
        }
        return null
    }

    /**
     * Power button.
     *  - Connected: KEY_POWER.
     *  - TV answers on its REST API: it is on or in a standby that keeps the network alive;
     *    KEY_POWER toggles both.
     *  - TV does not answer (deep standby): SmartThings "on" if configured, plus
     *    Wake-on-LAN rounds until it answers; then connect, and light the screen if only
     *    the network woke up. If it never answers, check whether its IP changed.
     */
    suspend fun power() {
        if (remote.isConnected()) {
            log("Conectado: envío KEY_POWER")
            remote.sendKey("KEY_POWER")
            return
        }
        val tv = store.selectedTv ?: return status("Elige primero un televisor")
        status("Comprobando el televisor…")
        val probe = WakeOnLan.probe(tv.ip)
        if (probe.reachable) {
            log("La TV responde (estado: ${probe.powerState ?: "no informado"}, red: ${probe.networkType ?: "?"})")
            learnMac(tv, probe.mac)
            if (connect(tv, relocate = false)) {
                log("Conectado: envío KEY_POWER")
                remote.sendKey("KEY_POWER")
            } else {
                log("No se pudo abrir el canal de control")
                status("La TV responde pero no acepta la conexión. Revisa el permiso en la TV.")
            }
            return
        }

        val token = store.smartThingsToken
        val stDevice = store.smartThingsDevice
        val useSt = token != null && stDevice != null
        if (useSt) {
            log("SmartThings: enviando \"encender\" a ${stDevice!!.name}")
            SmartThings.setSwitch(token!!, stDevice.id, true)
                .onSuccess { log("SmartThings aceptó la orden") }
                .onFailure { log("SmartThings: ${it.localizedMessage}") }
        }
        val mac = store.selectedTv?.macAddress
        if (mac == null && !useSt) {
            log("La TV no responde y no hay MAC guardada")
            status("Conecta la TV encendida una vez (o escribe su MAC en Ajustes) para poder encenderla.")
            return
        }
        status("Encendiendo el televisor…")
        var current = store.selectedTv ?: tv
        var woke = if (mac != null) {
            log("Wake-on-LAN a $mac")
            WakeOnLan.wakeAndWait(context, mac, current.ip, log = log)
        } else {
            waitReachable(current.ip, 20_000)
        }
        if (woke == null) {
            val moved = relocate(current)
            if (moved != null) {
                current = moved
                woke = WakeOnLan.probe(moved.ip)
            }
        }
        if (woke == null || !woke.reachable) {
            status(
                "La TV no respondió. Activa \"Encender con móvil\" en Ajustes › General › Red › " +
                    "Configuración experta de la TV, o configura SmartThings."
            )
            return
        }
        learnMac(current, woke.mac)
        val up = connect(current, relocate = false)
        if (woke.powerState == "standby" && up) {
            log("Red despierta pero pantalla apagada: envío KEY_POWER")
            remote.sendKey("KEY_POWER")
        }
        status("Televisor encendido")
    }
}
