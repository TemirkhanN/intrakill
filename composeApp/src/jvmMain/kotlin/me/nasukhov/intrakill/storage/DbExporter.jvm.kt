package me.nasukhov.intrakill.storage

import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.netty.Netty

actual fun getServerFactory(): ApplicationEngineFactory<ApplicationEngine, out ApplicationEngine.Configuration> = Netty
