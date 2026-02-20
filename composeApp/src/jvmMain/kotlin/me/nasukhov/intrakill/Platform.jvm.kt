package me.nasukhov.intrakill

import java.net.NetworkInterface

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

actual fun getLocalIpAddress(): String = try {
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .filter { it is java.net.Inet4Address }
        .map { it.hostAddress }
        .firstOrNull().orEmpty()
} catch (e: Exception) {
    ""
}