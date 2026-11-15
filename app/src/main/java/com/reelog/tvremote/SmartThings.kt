package com.reelog.tvremote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class StDevice(val id: String, val name: String)

/**
 * Minimal SmartThings cloud client. Samsung TVs registered in SmartThings accept a
 * "switch on" command from the cloud even when they are in deep standby, which works
 * where Wake-on-LAN does not.
 *
 * Uses a personal access token (account.smartthings.com/tokens) with the
 * "devices: list, see, control" scopes. Samsung limits new personal tokens to 24 hours.
 */
object SmartThings {
    private const val API = "https://api.smartthings.com/v1"

    suspend fun listTvs(token: String): Result<List<StDevice>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = request("GET", "$API/devices", token)
            val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
            val all = (0 until items.length()).mapNotNull { items.optJSONObject(it) }
            val tvs = all.filter { d ->
                val type = d.optJSONObject("ocf")?.optString("ocfDeviceType").orEmpty()
                val caps = d.optJSONArray("components")?.optJSONObject(0)?.optJSONArray("capabilities")
                val capIds = (0 until (caps?.length() ?: 0)).map { caps!!.optJSONObject(it)?.optString("id").orEmpty() }
                type == "oic.d.tv" || "tvChannel" in capIds || "samsungvd.remoteControl" in capIds
            }.ifEmpty { all }
            tvs.map { StDevice(it.optString("deviceId"), it.optString("label").ifBlank { it.optString("name") }) }
        }
    }

    suspend fun setSwitch(token: String, deviceId: String, on: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().put(
                "commands", JSONArray().put(
                    JSONObject().put("component", "main").put("capability", "switch")
                        .put("command", if (on) "on" else "off")
                )
            ).toString()
            request("POST", "$API/devices/$deviceId/commands", token, payload)
            Unit
        }
    }

    /** "on" / "off" as last reported to the cloud. */
    suspend fun switchState(token: String, deviceId: String): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val body = request("GET", "$API/devices/$deviceId/components/main/capabilities/switch/status", token)
            JSONObject(body).optJSONObject("switch")?.optString("value")
        }
    }

    private fun request(method: String, url: String, token: String, body: String? = null): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Authorization", "Bearer ${token.trim()}")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 401) error("Token de SmartThings no válido o caducado")
            if (code == 403) error("El token no tiene permiso para controlar dispositivos")
            if (code !in 200..299) error("SmartThings respondió $code")
            return text
        } finally {
            c.disconnect()
        }
    }
}
