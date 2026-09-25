package me.nasukhov.intrakill.storage

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.nasukhov.intrakill.Security
import me.nasukhov.intrakill.domain.model.Entry
import me.nasukhov.intrakill.getLocalIpAddress
import qrcode.QRCode
import kotlin.random.Random

class Sharing(
    private val entry: Entry,
) {
    @Serializable
    data class Code(
        val source: StorageSource,
        val entryId: String,
        val key: Int = Random.nextInt(),
    ) {
        fun getQR(): QRCode =
            QRCode
                .ofSquares()
                .build(Json.encodeToString(this))
    }

    private var cancellation: () -> Unit = {}

    // Heavily reliant on ExternalStorage.kt
    fun start(): QRCode {
        val ip = getLocalIpAddress()
        val port = 8083

        val sharingCode = Code(StorageSource(ip, port), entry.id)

        val server =
            embeddedServer(getServerFactory(), host = ip, port = port) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            encodeDefaults = true
                            prettyPrint = false
                        },
                    )
                }
                routing {
                    get("/entries/${entry.id}") {
                        val token = call.request.headers[HttpHeaders.Authorization]
                        if (token == null || !Security.verify(sharingCode.key.toString(), token)) {
                            call.respond(HttpStatusCode.Forbidden)
                        }

                        call.respond(entry)
                    }
                    get("/attachments/{id}/content") {
                        val token = call.request.headers[HttpHeaders.Authorization]
                        if (token == null || !Security.verify(sharingCode.key.toString(), token)) {
                            call.respond(HttpStatusCode.Forbidden)
                        }
                        val attachmentId = call.parameters["id"] ?: ""
                        val attachment = entry.attachments.find { it.id == attachmentId }
                        if (attachment == null) {
                            call.respond(HttpStatusCode.BadRequest)
                        } else {
                            call.respondOutputStream(ContentType.Application.OctetStream) {
                                attachment.content.use { inputStream ->
                                    inputStream.copyTo(this)
                                }
                            }
                        }
                    }
                }
            }.start(wait = false)

        cancellation = server::stop

        return sharingCode.getQR()
    }

    suspend fun stop() =
        withContext(Dispatchers.IO) {
            cancellation()
            cancellation = {}
        }
}
