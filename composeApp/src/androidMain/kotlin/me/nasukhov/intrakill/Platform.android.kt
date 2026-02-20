package me.nasukhov.intrakill

import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun getLocalIpAddress(): String = try {
    // Android-specific: Prefer the standard Java way for simplicity
    // but filtered for common mobile interface names like "wlan0"
    val interfaces = NetworkInterface.getNetworkInterfaces().toList()
    for (intf in interfaces) {
        if (!intf.isUp || intf.isLoopback) continue

        val addrs = intf.inetAddresses.toList()
        for (addr in addrs) {
            if (!addr.isLoopbackAddress && addr is Inet4Address) {
                return addr.hostAddress.orEmpty()
            }
        }
    }
    ""
} catch (e: Exception) {
    ""
}