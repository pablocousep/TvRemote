package com.reelog.tvremote

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Holds the remote's state so it survives configuration changes. */
class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val store = TvStore(app)

    val status = mutableStateOf("")
    val statusSerial = mutableIntStateOf(0)
    val connected = mutableStateOf(false)
    val connecting = mutableStateOf(false)
    val connectionLost = mutableStateOf(false)
    val scanning = mutableStateOf(false)
    val foundTvs = mutableStateListOf<SamsungTv>()
    val selectedTv = mutableStateOf(store.selectedTv)
    val haptics = mutableStateOf(store.haptics)
    val hardwareVolume = mutableStateOf(store.hardwareVolume)
    val theme = mutableStateOf(store.theme)
    val powerLog = mutableStateListOf<String>()
    val powerBusy = mutableStateOf(false)
    val installedApps = mutableStateListOf<TvApp>()
    val pinnedApps = mutableStateListOf<TvApp>().apply { addAll(store.pinnedApps) }
    val stConfigured = mutableStateOf(store.smartThingsToken != null)
    val stDevice = mutableStateOf(store.smartThingsDevice)
    val stDevices = mutableStateListOf<StDevice>()

    private var userDisconnected = false
    private var powerJob: Job? = null

    private val remote = SamsungRemote(
        app,
        onStatus = { msg -> viewModelScope.launch { setStatus(msg) } },
        onConnectionChanged = { up -> viewModelScope.launch { onConnection(up) } },
        onInstalledApps = { apps -> viewModelScope.launch { installedApps.clear(); installedApps.addAll(apps) } }
    )
    private val discovery = SamsungTvDiscovery(app)
    private val session = TvSession(
        app, store, remote,
        log = { line -> viewModelScope.launch { log(line) } },
        status = { msg -> viewModelScope.launch { setStatus(msg) } },
        onTvChanged = { tv -> viewModelScope.launch { selectedTv.value = tv } }
    )

    init {
        selectedTv.value?.let { connect(it) }
    }

    private fun onConnection(up: Boolean) {
        val wasConnected = connected.value
        connected.value = up
        if (up) {
            connecting.value = false
            connectionLost.value = false
            remote.requestInstalledApps()
        } else if (wasConnected && !userDisconnected) {
            connectionLost.value = true
        }
    }

    fun setStatus(message: String) {
        status.value = message
        statusSerial.intValue++
    }

    private fun log(line: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        powerLog.add(0, "$time  $line")
        while (powerLog.size > 60) powerLog.removeAt(powerLog.lastIndex)
    }

    // --- Remote keys -------------------------------------------------------------------------

    fun key(k: String) {
        if (remote.isConnected()) remote.sendKey(k)
        else viewModelScope.launch { if (!session.sendKey(k)) setStatus("No hay conexión con el televisor") }
    }

    fun text(value: String) = remote.sendText(value)
    fun launchApp(id: String) = remote.launchApp(id)
    fun isConnected() = remote.isConnected()

    // --- Connection ---------------------------------------------------------------------------

    fun connect(tv: SamsungTv) {
        userDisconnected = false
        connectionLost.value = false
        if (selectedTv.value?.ip != tv.ip) {
            installedApps.clear()
            store.selectedTv = tv
        }
        selectedTv.value = tv
        connecting.value = true
        viewModelScope.launch {
            val ok = session.connect(tv)
            connecting.value = false
            if (!ok) setStatus("No se pudo conectar con ${tv.name}")
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

    fun probeTv() {
        val tv = selectedTv.value ?: return setStatus("Elige primero un televisor")
        powerJob {
            log("Comprobando ${tv.ip}…")
            val p = WakeOnLan.probe(tv.ip, 1500)
            if (p.reachable) {
                log("Responde · estado: ${p.powerState ?: "no informado"} · red: ${p.networkType ?: "?"} · MAC: ${p.mac ?: "?"}")
                if (p.mac != null && !p.mac.equals(tv.macAddress, true)) saveMac(p.mac)
            } else {
                log("No responde (apagada del todo, en reposo profundo o con otra IP)")
            }
            val token = store.smartThingsToken
            val dev = store.smartThingsDevice
            if (token != null && dev != null) {
                SmartThings.switchState(token, dev.id)
                    .onSuccess { log("SmartThings dice: ${it ?: "?"}") }
                    .onFailure { log("SmartThings: ${it.localizedMessage}") }
            }
        }
    }

    fun wakeTest() {
        val tv = selectedTv.value ?: return setStatus("Elige primero un televisor")
        val mac = tv.macAddress ?: return setStatus("No hay MAC guardada")
        powerJob {
            log("Prueba Wake-on-LAN → $mac")
            WakeOnLan.wakeAndWait(getApplication(), mac, tv.ip, log = ::log)
        }
    }

    fun saveMac(input: String) {
        val tv = selectedTv.value ?: return setStatus("Elige primero un televisor")
        val mac = WakeOnLan.normalizeMac(input) ?: return setStatus("MAC no válida. Formato: AA:BB:CC:DD:EE:FF")
        val updated = tv.copy(macAddress = mac)
        store.selectedTv = updated
        selectedTv.value = updated
        log("MAC guardada: $mac")
    }

    fun clearLog() = powerLog.clear()

    // --- SmartThings --------------------------------------------------------------------------

    fun saveSmartThingsToken(token: String) {
        val clean = token.trim()
        if (clean.isEmpty()) {
            store.smartThingsToken = null
            store.smartThingsDevice = null
            stConfigured.value = false
            stDevice.value = null
            stDevices.clear()
            setStatus("SmartThings desactivado")
            return
        }
        store.smartThingsToken = clean
        stConfigured.value = true
        viewModelScope.launch {
            setStatus("Consultando SmartThings…")
            SmartThings.listTvs(clean)
                .onSuccess { list ->
                    stDevices.clear(); stDevices.addAll(list)
                    if (list.size == 1) chooseSmartThingsDevice(list[0])
                    setStatus(if (list.isEmpty()) "No hay televisores en tu cuenta SmartThings" else "Elige tu televisor de SmartThings")
                }
                .onFailure { setStatus("SmartThings: ${it.localizedMessage}") }
        }
    }

    fun chooseSmartThingsDevice(device: StDevice) {
        store.smartThingsDevice = device
        stDevice.value = device
        log("SmartThings: usaré ${device.name}")
    }

    // --- Preferences --------------------------------------------------------------------------

    fun setHaptics(on: Boolean) { haptics.value = on; store.haptics = on }
    fun setHardwareVolume(on: Boolean) { hardwareVolume.value = on; store.hardwareVolume = on }
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

    override fun onCleared() {
        remote.close()
        super.onCleared()
    }
}
