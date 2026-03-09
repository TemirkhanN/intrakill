package me.nasukhov.intrakill.storage

import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory

actual fun getServerFactory(): ApplicationEngineFactory<ApplicationEngine, out ApplicationEngine.Configuration> = CIO
