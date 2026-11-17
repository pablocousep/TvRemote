package com.reelog.tvremote

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings

/** What the phone's Wi-Fi looks like right now. */
sealed class PhoneNetwork {
    /** On Wi-Fi and its name could be read. */
    data class Wifi(val ssid: String) : PhoneNetwork()
    /** On Wi-Fi but Android hides the name (no location permission or location off). */
    object WifiUnknown : PhoneNetwork()
    /** Not on Wi-Fi at all (mobile data or offline). */
    object NoWifi : PhoneNetwork()
}

/**
 * Reads the current Wi-Fi network name. Android only reveals it to apps with the location
 * permission and with location turned on; apps cannot switch networks themselves (Android 10+),
 * so [openWifiPanel] lets the user pick one with a single tap.
 */
object WifiNetworks {
    fun hasLocationPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun current(context: Context): PhoneNetwork {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return PhoneNetwork.NoWifi
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return PhoneNetwork.NoWifi
        if (!hasLocationPermission(context)) return PhoneNetwork.WifiUnknown
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return PhoneNetwork.WifiUnknown
        @Suppress("DEPRECATION")
        val raw = wifi.connectionInfo?.ssid ?: return PhoneNetwork.WifiUnknown
        val ssid = raw.removeSurrounding("\"")
        return if (ssid.isBlank() || ssid == WifiManager.UNKNOWN_SSID || ssid == "<unknown ssid>") PhoneNetwork.WifiUnknown
        else PhoneNetwork.Wifi(ssid)
    }

    /** Shows Android's Wi-Fi picker on top of the app (Android 10+), else the Wi-Fi settings. */
    fun openWifiPanel(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI)
        else Intent(Settings.ACTION_WIFI_SETTINGS)
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
