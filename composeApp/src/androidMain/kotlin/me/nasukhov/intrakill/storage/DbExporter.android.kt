package me.nasukhov.intrakill.storage

import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import me.nasukhov.intrakill.Security

actual object DbExporter {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    actual fun start(plainPassword: String, port: Int, onExportStateChange: (ExportProcess) -> Unit): Boolean {
        if (!SecureDatabase.open(plainPassword)) {
            return false
        }

        try {
            server = embeddedServer(CIO, port = port) {
                routing {
                    get("/dump") {
                        val token = call.request.headers[HttpHeaders.Authorization]

                        if (token != null && Security.verify(plainPassword, token)) {
                            onExportStateChange(ExportProcess.BEGUN)

                            call.respondOutputStream(ContentType.Application.OctetStream) {
                                SecureDatabase.dumpDatabase(this)
                            }

                            onExportStateChange(ExportProcess.END)
                        } else {
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }
                }
            }.start(wait = false)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    actual fun stop() {
        server?.stop(gracePeriodMillis = 1000, timeoutMillis = 3000)
        server = null
    }
}