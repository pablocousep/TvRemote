package com.reelog.tvremote

import android.content.Context
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** A TV this phone has connected to before, with when it last did. */
data class SavedTv(val tv: SamsungTv, val lastConnected: Long)

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

    /** Every TV this phone has connected to, most recent first. */
    val savedTvs: List<SavedTv>
        get() = prefs.getString("saved_tvs", null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    SavedTv(
                        SamsungTv(
                            o.getString("ip"), o.optString("name", "Samsung Smart TV"), o.optString("model", ""),
                            o.optString("mac").takeIf { it.isNotBlank() }
                        ),
                        o.optLong("last")
                    )
                }
            }.getOrNull()
        } ?: emptyList()

    /** Adds or refreshes [tv] in the saved list. The same TV is matched by MAC, else by IP. */
    fun rememberTv(tv: SamsungTv, connectedAt: Long = System.currentTimeMillis()) {
        val others = savedTvs.filterNot { sameTv(it.tv, tv) }
        writeSaved(listOf(SavedTv(tv, connectedAt)) + others)
    }

    fun forgetSavedTv(tv: SamsungTv) = writeSaved(savedTvs.filterNot { sameTv(it.tv, tv) })

    private fun sameTv(a: SamsungTv, b: SamsungTv) =
        (a.macAddress != null && a.macAddress.equals(b.macAddress, ignoreCase = true)) || a.ip == b.ip

    private fun writeSaved(list: List<SavedTv>) {
        val arr = JSONArray(list.sortedByDescending { it.lastConnected }.take(20).map {
            JSONObject().put("ip", it.tv.ip).put("name", it.tv.name).put("model", it.tv.model)
                .put("mac", it.tv.macAddress ?: "").put("last", it.lastConnected)
        })
        prefs.edit().putString("saved_tvs", arr.toString()).apply()
    }

    var lastUpdateCheck: Long
        get() = prefs.getLong("last_update_check", 0L)
        set(v) = prefs.edit().putLong("last_update_check", v).apply()

    /** Update found by the background worker; the app presents the regular in-app prompt. */
    var pendingUpdateTag: String?
        get() = prefs.getString("pending_update_tag", null)
        set(v) = prefs.edit().putString("pending_update_tag", v).apply()

    /** Prevent repeated notifications for the same release. */
    var notifiedUpdateTag: String?
        get() = prefs.getString("notified_update_tag", null)
        set(v) = prefs.edit().putString("notified_update_tag", v).apply()

    /** Release the user chose to skip in the automatic prompt. */
    var dismissedUpdate: String?
        get() = prefs.getString("dismissed_update", null)
        set(v) = prefs.edit().putString("dismissed_update", v).apply()
}

/**
 * Connection and power logic shared by the app, the home-screen widget and the
 * Quick Settings tile.
 */
/** Known Tizen app ids per brand (they vary by model year and region). */
val KnownAppIds = mapOf(
    "prime" to listOf("3201910019365", "3201512006785"),
    "disney" to listOf("3201901017640"),
    "netflix" to listOf("3201907018807", "11101200001"),
    "youtube" to listOf("111299001912"),
    "spotify" to listOf("3201606009684"),
    "appletv" to listOf("3201807016597"),
    "max" to listOf("3202301029760")
)

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

    /**
     * Opens an app on the TV. App ids differ between models and regions, so this tries the id
     * the TV itself reported for that app first, then the known ids for the brand. Each one is
     * launched through the TV's REST API (works on most Tizen TVs), falling back to the remote
     * control channel.
     */
    suspend fun launchApp(app: TvApp, installed: List<TvApp>, brand: String?): Boolean {
        val tv = store.selectedTv ?: return false
        val ids = linkedSetOf<String>()
        installed.firstOrNull { it.id == app.id }?.let { ids += it.id }
        if (brand != null) installed.filter { brandOf(it) == brand }.forEach { ids += it.id }
        installed.filter { it.name.equals(app.name, ignoreCase = true) }.forEach { ids += it.id }
        ids += app.id
        brand?.let { KnownAppIds[it] }?.let { ids += it }

        for (id in ids) {
            when (WakeOnLan.restLaunch(tv.ip, id)) {
                true -> { log("App abierta (REST): ${app.name} [$id]"); return true }
                false -> continue          // the TV says it is not installed
                null -> break              // REST not available on this model
            }
        }
        if (!remote.isConnected() && !connect(tv)) return false
        val id = ids.first()
        // The TV reports app_type 2 for web apps (DEEP_LINK) and 4 for native ones (NATIVE_LAUNCH).
        val type = installed.firstOrNull { it.id == id }?.appType
        val action = if (type == 4) "NATIVE_LAUNCH" else "DEEP_LINK"
        log("App abierta (canal de control, $action): ${app.name} [$id]")
        remote.launchApp(id, action)
        return true
    }

    private fun learnMac(tv: SamsungTv, mac: String?) {
        if (mac == null || tv.macAddress.equals(mac, ignoreCase = true)) return
        val updated = tv.copy(macAddress = mac)
        store.selectedTv = updated
        onTvChanged(updated)
        log("MAC guardada: $mac")
    }

    /**
     * Power button.
     *  - Connected: KEY_POWER.
     *  - TV answers on its REST API: it is on or in a standby that keeps the network alive;
     *    KEY_POWER toggles both.
     *  - TV does not answer (deep standby): Wake-on-LAN rounds until it answers; then connect,
     *    and light the screen if only the network woke up. If it never answers, check whether its IP changed.
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

        val mac = store.selectedTv?.macAddress
        if (mac == null) {
            log("La TV no responde y no hay MAC guardada")
            status("Conecta la TV encendida una vez para que la app aprenda a encenderla.")
            return
        }
        status("Encendiendo el televisor…")
        var current = store.selectedTv ?: tv
        log("Wake-on-LAN a $mac")
        var woke = WakeOnLan.wakeAndWait(context, mac, current.ip, log = log)
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
                    "Configuración experta de la TV."
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
