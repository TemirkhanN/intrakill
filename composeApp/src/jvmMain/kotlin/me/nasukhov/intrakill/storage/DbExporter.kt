package me.nasukhov.intrakill.storage

import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.nasukhov.intrakill.Security

object DbExporter {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start(plainPassword: String, port: Int = 8080) {
        server = embeddedServer(Netty, port = port) {
            routing {
                get("/dump") {
                    val token = call.request.headers[HttpHeaders.Authorization]

                    // BCrypt.checkpw handles the logic of extracting the salt
                    // and comparing it correctly.
                    if (token != null && Security.verify(plainPassword, token)) {
                        call.respondOutputStream {
                            SecureDatabase.dumpDatabase(this)
                        }
                    } else {
                        call.respond(HttpStatusCode.Forbidden)
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