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

/** Holds the remote's state so it survives configuration changes. */
class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val store = TvStore(app)

    val status = mutableStateOf("")
    val statusSerial = mutableIntStateOf(0)
    val connected = mutableStateOf(false)
    val connecting = mutableStateOf(false)
    val connectionLost = mutableStateOf(false)
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
        status = { msg -> viewModelScope.launch { setStatus(msg) } },
        onTvChanged = { tv -> viewModelScope.launch { selectedTv.value = tv } }
    )

    init {
        // TVs paired before the saved list existed: keep the current one in it.
        selectedTv.value?.let { tv ->
            if (savedTvs.none { it.tv.ip == tv.ip }) {
                store.rememberTv(tv, connectedAt = 0L)
                savedTvs.clear(); savedTvs.addAll(store.savedTvs)
            }
        }
        selectedTv.value?.let { connect(it, auto = true) }
        // Quietly look for a new release at most every 6 hours.
        if (System.currentTimeMillis() - store.lastUpdateCheck > 6 * 3600_000L) checkForUpdates(silent = true)
    }

    /** True when a switched-off TV can be woken, so "no connection" is not an error. */
    private fun canWake() = selectedTv.value?.macAddress != null

    /** Connection errors are expected while the TV sleeps; keep them in the log instead. */
    private fun onRemoteStatus(msg: String) {
        val connectionNoise = msg.startsWith("No se pudo conectar") || msg == "Conexión cerrada" ||
            msg.startsWith("Conexión TLS verificada")
        if (connectionNoise && canWake()) log(msg) else setStatus(msg)
    }

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
        if (selectedTv.value?.ip != tv.ip) {
            installedApps.clear()
            store.selectedTv = tv
        }
        selectedTv.value = tv
        connecting.value = true
        viewModelScope.launch {
            // On launch the TV is often just asleep: don't scan the network for a new IP then.
            val ok = session.connect(tv, relocate = !auto)
            connecting.value = false
            if (!ok) {
                if (canWake()) {
                    tvAsleep.value = true
                    if (!auto) setStatus("${tv.name} está apagada. Pulsa encender para despertarla.")
                } else {
                    setStatus("No se pudo conectar con ${tv.name}")
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
        setStatus("Buscando televisores en tu red local…")
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
            try { block() } finally { powerBusy.value = false }
        }
    }

    fun power() {
        if (remote.isConnected()) { log("Conectado: envío KEY_POWER"); remote.sendKey("KEY_POWER"); return }
        powerJob { session.power() }
    }


    // --- Preferences --------------------------------------------------------------------------

    fun setHaptics(on: Boolean) { haptics.value = on; store.haptics = on }
    fun setTheme(value: String) { theme.value = value; store.theme = value }

    fun togglePin(app: TvApp) {
        val current = pinnedApps.toMutableList()
        val index = current.indexOfFirst { it.id == app.id }
        if (index >= 0) {
            if (current.size == 1) return setStatus("Deja al menos un acceso directo")
            current.removeAt(index)
            setStatus("${app.name} quitado del mando")
        } else {
            if (current.size >= 3) current.removeAt(current.lastIndex)
            current.add(app)
            setStatus("${app.name} añadido al mando")
        }
        pinnedApps.clear(); pinnedApps.addAll(current)
        store.pinnedApps = current
    }

    // --- Updates ------------------------------------------------------------------------------

    fun checkForUpdates(silent: Boolean = false) {
        if (updateChecking.value) return
        updateChecking.value = true
        viewModelScope.launch {
            Updater.check(getApplication())
                .onSuccess { info ->
                    store.lastUpdateCheck = System.currentTimeMillis()
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
        remote.close()
        super.onCleared()
    }
}
