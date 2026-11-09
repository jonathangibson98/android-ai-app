package com.example.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    fun getDeviceIpAddress(context: Context): String {
        try {
            // First check Wi-Fi manager for current connection
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ipInt = wifiInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                val ipString = String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
                if (ipString != "0.0.0.0") return ipString
            }
        } catch (_: Exception) {}

        // Fallback to iterating network interfaces (works for Hotspot, Ethernet, and modern Wi-Fi)
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress ?: continue
                        if (!hostAddress.startsWith("127.")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return "127.0.0.1"
    }

    fun getAllIpAddresses(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        val name = when {
                            iface.name.startsWith("wlan") -> "Wi-Fi (${iface.name})"
                            iface.name.startsWith("eth") -> "Ethernet (${iface.name})"
                            iface.name.startsWith("ap") || iface.name.startsWith("swlan") -> "Hotspot (${iface.name})"
                            iface.isLoopback -> "Localhost (${iface.name})"
                            else -> iface.displayName ?: iface.name
                        }
                        result.add(name to host)
                    }
                }
            }
        } catch (_: Exception) {}
        if (result.isEmpty()) {
            result.add("Localhost" to "127.0.0.1")
        }
        return result
    }
}
