package me.nasukhov.intrakill.storage

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

object DbExporter {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    // TODO add some basic authentication just in case
    fun start(port: Int = 8080) {
        if (server != null) return // already running

        server = embeddedServer(Netty, port = port) {
            routing {
                get("/dump") {
                    call.respondOutputStream {
                        SecureDatabase.dumpDatabase(this)
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 1000, timeoutMillis = 5000)
        server = null
    }
}