package me.nasukhov.intrakill

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform