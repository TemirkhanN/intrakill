package me.nasukhov.intrakill.storage

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import me.nasukhov.intrakill.Security

actual object DbExporter {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    actual fun start(
        plainPassword: String,
        port: Int,
        onExportStateChange: (ExportProcess) -> Unit,
    ): Boolean {
        if (!SecureDatabase.open(plainPassword)) {
            return false
        }
        server =
            embeddedServer(Netty, port = port) {
                routing {
                    get("/dump") {
                        val token = call.request.headers[HttpHeaders.Authorization]

                        // BCrypt.checkpw handles the logic of extracting the salt
                        // and comparing it correctly.
                        if (token != null && Security.verify(plainPassword, token)) {
                            onExportStateChange(ExportProcess.BEGUN)
                            val dbFile = SecureDatabase.dumpDatabase()
                            try {
                                call.respondFile(dbFile)
                            } finally {
                                dbFile.delete()
                                onExportStateChange(ExportProcess.END)
                            }
                        } else {
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }
                }
            }.start(wait = false)

        return true
    }

    actual fun stop() {
        server?.stop(gracePeriodMillis = 1000, timeoutMillis = 5000)
        server = null
    }
}
