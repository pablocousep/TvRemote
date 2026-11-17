package com.reelog.tvremote

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The TV lives on another Wi-Fi than the one the phone is on (current = null: no Wi-Fi). */
data class WifiMismatch(val tv: SamsungTv, val expected: String, val current: String?)

/** Holds the remote's state so it survives configuration changes. */
class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val store = TvStore(app)

    val status = mutableStateOf("")
    val statusSerial = mutableIntStateOf(0)
    val connected = mutableStateOf(false)
    val connecting = mutableStateOf(false)
    val connectionLost = mutableStateOf(false)
    /** Short "what's happening" line under the TV name (connecting, waking…), instead of toasts. */
    val activity = mutableStateOf<String?>(null)
    val wifiMismatch = mutableStateOf<WifiMismatch?>(null)
    /** Shows the "how to turn the TV on from the phone" steps. */
    val wolGuide = mutableStateOf(false)
    /** The TV is off/asleep but can be woken with Wake-on-LAN (its MAC is known). */
    val tvAsleep = mutableStateOf(false)
    val savedTvs = mutableStateListOf<SavedTv>().apply { addAll(store.savedTvs) }
    val scanning = mutableStateOf(false)
    val foundTvs = mutableStateListOf<SamsungTv>()
    val selectedTv = mutableStateOf(store.selectedTv)
    val haptics = mutableStateOf(store.haptics)
    val hardwareVolume = mutableStateOf(store.hardwareVolume)
    val theme = mutableStateOf(store.theme)
    val powerBusy = mutableStateOf(false)
    val installedApps = mutableStateListOf<TvApp>()
    /** Icons sent by the TV itself, by app id. */
    val appIcons = mutableStateMapOf<String, ImageBitmap>()
    val pinnedApps = mutableStateListOf<TvApp>().apply { addAll(store.pinnedApps) }

    val versionName: String = Updater.currentVersionName(app)
    val update = mutableStateOf<UpdateInfo?>(null)
    val updatePrompt = mutableStateOf(false)
    val updateChecking = mutableStateOf(false)
    val updateProgress = mutableStateOf<Float?>(null)
    private var downloadedApk: java.io.File? = null

    private var userDisconnected = false
    private var powerJob: Job? = null

    private val remote = SamsungRemote(
        app,
        onStatus = { msg -> viewModelScope.launch { onRemoteStatus(msg) } },
        onConnectionChanged = { up -> viewModelScope.launch { onConnection(up) } },
        onInstalledApps = { apps -> viewModelScope.launch { onApps(apps) } },
        onAppIcon = { path, png -> viewModelScope.launch { onIcon(path, png) } }
    )
    private val discovery = SamsungTvDiscovery(app)
    private val session = TvSession(
        app, store, remote,
        log = { line -> viewModelScope.launch { log(line) } },
        status = { msg -> viewModelScope.launch { onSessionStatus(msg) } },
        onTvChanged = { tv -> viewModelScope.launch { selectedTv.value = tv } }
    )

    /** When the phone changes network while the Wi-Fi notice is shown, retry automatically. */
    private val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) = recheckWifi()
        override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) = recheckWifi()
    }

    private fun recheckWifi() {
        viewModelScope.launch {
            val pending = wifiMismatch.value ?: return@launch
            kotlinx.coroutines.delay(800) // let Android finish switching
            val still = wifiCheck(pending.tv)
            wifiMismatch.value = still
            if (still == null && wifiMismatch.value == null) connect(pending.tv)
        }
    }

    /** Null when the phone is on the TV's Wi-Fi, or when the network name can't be known. */
    private fun wifiCheck(tv: SamsungTv): WifiMismatch? {
        val expected = tv.ssid ?: return null
        return when (val now = WifiNetworks.current(getApplication())) {
            is PhoneNetwork.Wifi -> if (now.ssid == expected) null else WifiMismatch(tv, expected, now.ssid)
            PhoneNetwork.NoWifi -> WifiMismatch(tv, expected, null)
            PhoneNetwork.WifiUnknown -> null
        }
    }

    /** Remembers the Wi-Fi the phone is on as this TV's network. */
    private fun recordWifi() {
        val tv = selectedTv.value ?: return
        val now = WifiNetworks.current(getApplication()) as? PhoneNetwork.Wifi ?: return
        if (tv.ssid == now.ssid) return
        val updated = tv.copy(ssid = now.ssid)
        store.selectedTv = updated
        store.rememberTv(updated)
        selectedTv.value = updated
        savedTvs.clear(); savedTvs.addAll(store.savedTvs)
    }

    fun onLocationGranted() {
        if (connected.value) recordWifi()
    }

    fun dismissWifiMismatch() { wifiMismatch.value = null }

    init {
        runCatching {
            app.getSystemService(android.net.ConnectivityManager::class.java)
                ?.registerDefaultNetworkCallback(networkCallback)
        }
        // TVs paired before the saved list existed: keep the current one in it.
        selectedTv.value?.let { tv ->
            if (savedTvs.none { it.tv.ip == tv.ip }) {
                store.rememberTv(tv, connectedAt = 0L)
                savedTvs.clear(); savedTvs.addAll(store.savedTvs)
            }
        }
        selectedTv.value?.let { connect(it, auto = true) }
        // Schedule a network-aware check every six hours, including while the app is closed.
        // The Google Play build gets its updates from Play, never from GitHub.
        if (BuildConfig.SELF_UPDATE) {
            UpdateCheckWorker.schedule(app)
            // A release found in the background is shown right away; otherwise check every 6 hours.
            // Silent: the prompt still appears for a new, not-dismissed release, but there is no
            // "you're up to date" message when the pending one was just installed.
            if (store.pendingUpdateTag != null || System.currentTimeMillis() - store.lastUpdateCheck > 6 * 3600_000L) {
                checkForUpdates(silent = true)
            }
        }
    }

    /** True when a switched-off TV can be woken, so "no connection" is not an error. */
    private fun canWake() = selectedTv.value?.macAddress != null

    /** Connection errors are expected while the TV sleeps; keep them in the log instead. */
    private fun onRemoteStatus(msg: String) {
        // Routine connection progress is shown by the line under the TV name, not as toasts.
        val progress = msg.startsWith("Conectando") || msg.startsWith("Conexión TLS verificada") ||
            msg == "Conectado al Samsung TV"
        val connectionNoise = msg.startsWith("No se pudo conectar") || msg == "Conexión cerrada"
        when {
            progress -> log(msg)
            connectionNoise && canWake() -> log(msg)
            else -> setStatus(msg)
        }
    }

    /** Power/connect steps ending in "…" are progress (shown under the TV name); the rest are results. */
    private fun onSessionStatus(msg: String) {
        when {
            msg.endsWith("…") -> activity.value = msg
            // Waking failed or isn't set up yet: show the simple steps instead of an error.
            msg.startsWith("La TV no respondió") || msg.startsWith("Conecta la TV encendida una vez") -> wolGuide.value = true
            else -> setStatus(msg)
        }
    }

    fun showWolGuide(show: Boolean) { wolGuide.value = show }

    private fun onConnection(up: Boolean) {
        val wasConnected = connected.value
        connected.value = up
        if (up) {
            connecting.value = false
            connectionLost.value = false
            tvAsleep.value = false
            selectedTv.value?.let { tv ->
                store.rememberTv(tv)
                savedTvs.clear(); savedTvs.addAll(store.savedTvs)
            }
            wifiMismatch.value = null
            recordWifi()
            remote.requestInstalledApps()
        } else if (wasConnected && !userDisconnected) {
            if (canWake()) {
                // Usually the TV was switched off. Reconnect quietly if it is still there.
                tvAsleep.value = true
                viewModelScope.launch {
                    kotlinx.coroutines.delay(1500)
                    val tv = selectedTv.value ?: return@launch
                    if (WakeOnLan.probe(tv.ip).reachable) {
                        log("La TV sigue en la red: reconectando")
                        session.connect(tv, relocate = false)
                    } else {
                        log("La TV se ha apagado")
                    }
                }
            } else {
                connectionLost.value = true
            }
        }
    }

    private fun onApps(apps: List<TvApp>) {
        installedApps.clear()
        installedApps.addAll(apps)
        // Ask for the TV's own icon of every app that has one.
        apps.filter { it.id !in appIcons }.mapNotNull { it.iconPath }.distinct()
            .take(60).forEach { remote.requestAppIcon(it) }
    }

    private fun onIcon(path: String, png: ByteArray) {
        val bmp = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return
        installedApps.filter { it.iconPath == path }.forEach { appIcons[it.id] = bmp.asImageBitmap() }
    }

    fun setStatus(message: String) {
        status.value = message
        statusSerial.intValue++
    }

    private fun log(line: String) {
        android.util.Log.d("TvRemote", line)
    }

    // --- Remote keys -------------------------------------------------------------------------

    fun key(k: String) {
        if (remote.isConnected()) remote.sendKey(k)
        else viewModelScope.launch {
            if (!session.sendKey(k)) {
                setStatus(if (canWake()) "La TV está apagada. Pulsa encender." else "No hay conexión con el televisor")
            }
        }
    }

    fun text(value: String) = remote.sendText(value)
    fun launchApp(app: TvApp) {
        viewModelScope.launch {
            if (!session.launchApp(app, installedApps.toList(), brandOf(app))) {
                setStatus(if (canWake()) "La TV está apagada. Pulsa encender." else "No hay conexión con el televisor")
            }
        }
    }
    fun isConnected() = remote.isConnected()

    // --- Connection ---------------------------------------------------------------------------

    fun connect(tv: SamsungTv, auto: Boolean = false) {
        userDisconnected = false
        connectionLost.value = false
        val previous = selectedTv.value
        val discoveredMac = tv.macAddress?.let(WakeOnLan::normalizeMac)
        val enrichedTv = tv.copy(
            macAddress = discoveredMac ?: previous?.takeIf { it.ip == tv.ip }?.macAddress
        )
        if (previous?.ip != enrichedTv.ip) installedApps.clear()
        // Persist the discovered MAC even when the selected TV keeps the same IP.
        store.selectedTv = enrichedTv
        selectedTv.value = enrichedTv
        // On another Wi-Fi the TV can't be reached: say so instead of failing.
        wifiCheck(enrichedTv)?.let { mismatch ->
            wifiMismatch.value = mismatch
            connecting.value = false
            activity.value = null
            return
        }
        wifiMismatch.value = null
        connecting.value = true
        activity.value = "Conectando a ${enrichedTv.displayName}…"
        viewModelScope.launch {
            // On launch the TV is often just asleep: don't scan the network for a new IP then.
            val ok = session.connect(enrichedTv, relocate = !auto)
            connecting.value = false
            if (!powerBusy.value) activity.value = null
            if (ok) {
                // Some Samsung models expose their MAC only through the REST device endpoint.
                val mac = enrichedTv.macAddress ?: WakeOnLan.probe(enrichedTv.ip, 1200).mac
                if (mac != null) {
                    val updated = enrichedTv.copy(macAddress = mac)
                    store.selectedTv = updated
                    store.rememberTv(updated)
                    selectedTv.value = updated
                    savedTvs.clear(); savedTvs.addAll(store.savedTvs)
                }
            }
            if (!ok) {
                if (canWake()) {
                    tvAsleep.value = true
                    if (!auto) setStatus("${enrichedTv.displayName} está apagada. Pulsa encender para despertarla.")
                } else {
                    setStatus("No se pudo conectar con ${enrichedTv.displayName}")
                }
            }
        }
    }

    fun reconnect() = selectedTv.value?.let { connect(it) }

    fun disconnect() {
        userDisconnected = true
        connectionLost.value = false
        remote.close()
        setStatus("Desconectado")
    }

    fun forget(tv: SamsungTv) = remote.forgetPairing(tv.ip)

    /** Removes a TV from the saved list (and its pairing). */
    fun forgetSaved(tv: SamsungTv) {
        store.forgetSavedTv(tv)
        remote.forgetPairing(tv.ip)
        savedTvs.clear(); savedTvs.addAll(store.savedTvs)
        setStatus("${tv.name} eliminada de tus televisores")
    }

    fun discover() {
        if (scanning.value) return
        scanning.value = true
        viewModelScope.launch {
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

    // --- Power --------------------------------------------------------------------------------

    private fun powerJob(block: suspend () -> Unit) {
        if (powerJob?.isActive == true) return
        powerBusy.value = true
        powerJob = viewModelScope.launch {
            try { block() } finally { powerBusy.value = false; activity.value = null }
        }
    }

    fun power() {
        if (remote.isConnected()) { log("Conectado: envío KEY_POWER"); remote.sendKey("KEY_POWER"); return }
        // Wake-on-LAN only works on the TV's own network.
        selectedTv.value?.let { tv -> wifiCheck(tv)?.let { wifiMismatch.value = it; return } }
        powerJob { session.power() }
    }


    // --- Preferences --------------------------------------------------------------------------

    fun setHaptics(on: Boolean) { haptics.value = on; store.haptics = on }
    fun setTheme(value: String) { theme.value = value; store.theme = value }

    /** Adds or removes an app from the main remote's shortcut row (up to 3). */
    fun togglePin(app: TvApp) {
        val current = pinnedApps.toMutableList()
        val index = current.indexOfFirst { it.id == app.id }
        if (index >= 0) {
            current.removeAt(index)
        } else {
            if (current.size >= 3) return setStatus("El mando ya tiene 3 apps. Quita una para añadir ${app.name}.")
            current.add(app)
        }
        pinnedApps.clear(); pinnedApps.addAll(current)
        store.pinnedApps = current
    }

    /** Sets or clears the user's note for a TV ("Tele salón"). */
    fun renameTv(tv: SamsungTv, label: String?) {
        store.renameTv(tv, label)
        savedTvs.clear(); savedTvs.addAll(store.savedTvs)
        store.selectedTv?.let { selectedTv.value = it }
    }

    // --- Updates ------------------------------------------------------------------------------

    fun checkForUpdates(silent: Boolean = false) {
        if (!BuildConfig.SELF_UPDATE) return
        if (updateChecking.value) return
        updateChecking.value = true
        viewModelScope.launch {
            Updater.check(getApplication())
                .onSuccess { info ->
                    store.lastUpdateCheck = System.currentTimeMillis()
                    store.pendingUpdateTag = null
                    update.value = info
                    when {
                        info == null -> if (!silent) setStatus("Tienes la última versión ($versionName)")
                        !silent || store.dismissedUpdate != info.tag -> updatePrompt.value = true
                    }
                }
                .onFailure { if (!silent) setStatus("No se pudo comprobar: ${it.localizedMessage}") }
            updateChecking.value = false
        }
    }

    fun dismissUpdate() {
        updatePrompt.value = false
        update.value?.let { store.dismissedUpdate = it.tag }
    }

    fun startUpdate() {
        if (!BuildConfig.SELF_UPDATE) return
        val info = update.value ?: return
        val app = getApplication<Application>()
        if (!Updater.canInstall(app)) {
            setStatus("Permite a TvRemote instalar apps y vuelve a pulsar Actualizar")
            Updater.openInstallPermission(app)
            return
        }
        downloadedApk?.takeIf { it.exists() && it.name.contains(info.tag) }?.let {
            Updater.install(app, it)
            return
        }
        if (updateProgress.value != null) return
        updateProgress.value = 0f
        viewModelScope.launch {
            Updater.download(app, info) { p -> viewModelScope.launch { updateProgress.value = p } }
                .onSuccess { file ->
                    downloadedApk = file
                    updatePrompt.value = false
                    Updater.install(app, file)
                }
                .onFailure { setStatus("No se pudo descargar: ${it.localizedMessage}") }
            updateProgress.value = null
        }
    }

    override fun onCleared() {
        runCatching {
            getApplication<Application>().getSystemService(android.net.ConnectivityManager::class.java)
                ?.unregisterNetworkCallback(networkCallback)
        }
        remote.close()
        super.onCleared()
    }
}
