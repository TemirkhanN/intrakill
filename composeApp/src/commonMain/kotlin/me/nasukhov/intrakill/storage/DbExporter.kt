package me.nasukhov.intrakill.storage

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.RoutingHandler
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.nasukhov.intrakill.Security

enum class ExportProcess {
    BEGUN,
    END,
}

class DbExporter(
    private val db: SecureDatabase,
) {
    private var stopServer: (() -> Unit)? = null

    suspend fun start(
        plainPassword: String,
        port: Int = 8080,
        onExportStateChange: (ExportProcess) -> Unit = {},
    ): Boolean =
        withContext(Dispatchers.Default) {
            if (!db.open(plainPassword)) {
                return@withContext false
            }
            val server =
                embeddedServer(getServerFactory(), port = port) {
                    install(ContentNegotiation) {
                        json(
                            Json {
                                encodeDefaults = true
                                prettyPrint = false
                            },
                        )
                    }
                    routing {
                        get(
                            "/entriesIds",
                            requiresAuth(plainPassword) {
                                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                                val list =
                                    db
                                        .findEntries(EntriesFilter(limit = limit, offset = offset))
                                        .map { it.id }
                                call.respond(list)
                            },
                        )
                        get(
                            "/entries/{id}",
                            requiresAuth(plainPassword) {
                                val entryId = call.parameters["id"]
                                if (entryId == null) {
                                    call.respond(HttpStatusCode.BadRequest)
                                } else {
                                    call.respond(db.getById(entryId))
                                }
                            },
                        )
                        get(
                            "/attachments/{id}/content",
                            requiresAuth(plainPassword) {
                                val attachmentId = call.parameters["id"]
                                if (attachmentId == null) {
                                    call.respond(HttpStatusCode.BadRequest)
                                } else {
                                    call.respondOutputStream(ContentType.Application.OctetStream) {
                                        db.getAttachmentContent(attachmentId).use { inputStream ->
                                            inputStream.copyTo(this)
                                        }
                                    }
                                }
                            },
                        )
                        get(
                            "/dump",
                            requiresAuth(plainPassword) {
                                onExportStateChange(ExportProcess.BEGUN)
                                val dbFile = db.dumpDatabase()
                                try {
                                    call.respondFile(dbFile)
                                } finally {
                                    dbFile.delete()
                                    onExportStateChange(ExportProcess.END)
                                }
                            },
                        )
                    }
                }

            try {
                server.start(wait = false)
            } catch (_: Exception) {
                // TODO add some logging, I don't know
                return@withContext false
            }

            stopServer = { server.stop(gracePeriodMillis = 1000, timeoutMillis = 5000) }

            return@withContext true
        }

    suspend fun stop() =
        withContext(Dispatchers.IO) {
            stopServer?.invoke()
        }

    private fun requiresAuth(
        plainPassword: String,
        body: RoutingHandler,
    ): RoutingHandler =
        {
            val token = call.request.headers[HttpHeaders.Authorization]

            if (token == null || !Security.verify(plainPassword, token)) {
                call.respond(HttpStatusCode.Forbidden)
            } else {
                this.body()
            }
        }
}

expect fun getServerFactory(): ApplicationEngineFactory<ApplicationEngine, out ApplicationEngine.Configuration>
