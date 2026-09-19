package me.nasukhov.intrakill.ui.entries

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.backhandler.BackCallback
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import me.nasukhov.intrakill.domain.model.Attachment
import me.nasukhov.intrakill.domain.model.Entry
import me.nasukhov.intrakill.domain.model.Tag
import me.nasukhov.intrakill.domain.model.combine
import me.nasukhov.intrakill.domain.model.moveDownwards
import me.nasukhov.intrakill.domain.model.moveUpwards
import me.nasukhov.intrakill.domain.model.remove
import me.nasukhov.intrakill.domain.repository.MediaRepository
import me.nasukhov.intrakill.getLocalIpAddress
import me.nasukhov.intrakill.kmp.coroutineScope
import me.nasukhov.intrakill.storage.Content
import me.nasukhov.intrakill.storage.FilePicker
import me.nasukhov.intrakill.storage.StorageSource
import me.nasukhov.intrakill.storage.getServerFactory
import me.nasukhov.intrakill.ui.root.Request
import me.nasukhov.intrakill.ui.view.Notification
import qrcode.QRCode
import java.net.HttpURLConnection
import java.net.URI
import kotlin.random.Random

data class EntryState(
    val entryId: String,
    val entry: Entry? = null,
    val knownTags: Set<Tag> = emptySet(),
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isWaitingForActionConfirmation: Boolean = false,
    val notifications: List<Notification> = emptyList(),
    val sharingQRCode: QRCode? = null,
)

interface EntryComponent {
    val state: Value<EntryState>

    fun close()

    fun deleteEntry(forced: Boolean = false)

    fun confirmDelete()

    fun cancelDelete()

    fun onTagsChanged(newTags: Set<String>)

    fun toggleEditMode()

    fun deleteAttachment(attachment: Attachment)

    fun moveAttachmentUpwards(attachment: Attachment)

    fun moveAttachmentDownwards(attachment: Attachment)

    fun changeTags(tags: Set<String>)

    fun promptAttachmentSelection()

    fun share()

    fun stopSharing()
}

class DefaultEntryComponent(
    context: ComponentContext,
    entryId: String,
    private val navigate: (Request) -> Unit,
) : EntryComponent,
    ComponentContext by context {
    private val mutableState = MutableValue(EntryState(entryId = entryId, isLoading = true))
    override val state: Value<EntryState> = mutableState
    private val scope = instanceKeeper.coroutineScope()

    private val mediaRepository = MediaRepository
    private var sharing: Sharing? = null

    init {
        scope.launch {
            var entry = mediaRepository.findById(entryId)
            val knownTags = mediaRepository.listTags()
            mutableState.update {
                it.copy(
                    entry = entry,
                    isLoading = false,
                    knownTags = knownTags,
                )
            }
        }

        context.backHandler.register(BackCallback(onBack = ::close))
    }

    override fun close() {
        sharing?.stop()
        navigate(Request.Back)
    }

    override fun deleteEntry(forced: Boolean) {
        if (!forced) {
            mutableState.update { it.copy(isWaitingForActionConfirmation = true) }
            return
        }

        mutableState.value.let {
            require(it.isEditing && it.entry != null)
            scope.launch {
                mediaRepository.deleteById(it.entry.id)
                close()
            }
        }
    }

    override fun confirmDelete() {
        state.value.let { current ->
            require(current.isWaitingForActionConfirmation)
            deleteEntry(forced = true)
        }
    }

    override fun cancelDelete() {
        mutableState.update { it.copy(isWaitingForActionConfirmation = false) }
    }

    override fun onTagsChanged(newTags: Set<String>) {
        navigate(Request.ListEntries(filterByTags = newTags))
    }

    override fun toggleEditMode() {
        require(mutableState.value.entry != null)
        mutableState.update { it.copy(isEditing = !it.isEditing, notifications = emptyList()) }
    }

    override fun deleteAttachment(attachment: Attachment) {
        mutableState.value.let { current ->
            require(current.entry != null)

            val modifiedAttachments = current.entry.attachments.remove(attachment)
            if (modifiedAttachments.isEmpty()) return deleteEntry(forced = true)

            scope.launch {
                val updatedEntry =
                    mediaRepository.save(
                        current.entry.copy(attachments = modifiedAttachments),
                    )

                mutableState.update { it.copy(entry = updatedEntry) }
            }
        }
    }

    override fun moveAttachmentUpwards(attachment: Attachment) {
        mutableState.value.let { current ->
            require(current.entry != null)

            scope.launch {
                val updatedEntry =
                    mediaRepository.save(
                        current.entry.copy(attachments = current.entry.attachments.moveUpwards(attachment)),
                    )

                mutableState.update { it.copy(entry = updatedEntry) }
            }
        }
    }

    override fun moveAttachmentDownwards(attachment: Attachment) {
        mutableState.value.let { current ->
            require(current.entry != null)

            scope.launch {
                val updatedEntry =
                    mediaRepository.save(
                        current.entry.copy(attachments = current.entry.attachments.moveDownwards(attachment)),
                    )

                mutableState.update { it.copy(entry = updatedEntry) }
            }
        }
    }

    override fun changeTags(tags: Set<String>) {
        mutableState.value.let { current ->
            require(current.entry != null)

            scope.launch {
                val updatedEntry = mediaRepository.save(current.entry.copy(tags = tags))

                mutableState.update { it.copy(entry = updatedEntry) }
            }
        }
    }

    override fun promptAttachmentSelection() {
        state.value.let { current ->
            require(current.entry != null)

            scope.launch {
                val picked = FilePicker.pickMultiple()
                val newAttachments =
                    picked.filter { it.isSuccess }.mapIndexed { index, result ->
                        val it = result.getOrThrow()
                        Attachment(
                            mimeType = it.mimeType,
                            content = it.content,
                            preview = it.rawPreview,
                            size = it.size,
                            position = index,
                        )
                    }
                val violations =
                    picked.filter { it.isFailure }.map { it.exceptionOrNull()!!.message ?: "Unknown error" }

                if (!newAttachments.isEmpty()) {
                    val updatedEntry =
                        mediaRepository.save(
                            current.entry.copy(attachments = current.entry.attachments.combine(newAttachments)),
                        )

                    mutableState.update { it.copy(entry = updatedEntry, notifications = emptyList()) }
                } else if (violations.isNotEmpty()) {
                    mutableState.update {
                        it.copy(
                            notifications = Notification.errors(violations),
                        )
                    }
                }
            }
        }
    }

    override fun share() {
        mutableState.apply {
            val entry = value.entry
            if (entry == null) {
                return@apply
            }

            val share = Sharing(entry)

            update { it.copy(sharingQRCode = share.start()) }
        }
    }

    override fun stopSharing() {
        sharing?.stop()
        mutableState.update { it.copy(sharingQRCode = null) }
    }
}

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

        @OptIn(ExperimentalSerializationApi::class)
        suspend fun resolve(): Entry =
            withContext(Dispatchers.IO) {
                val secretKey = key.toString()

                request(source.urlTo("/shared/$entryId"), secretKey) {
                    val entry = Json.decodeFromStream<Entry>(inputStream)

                    entry.copy(
                        isPersisted = false,
                        attachments = entry.attachments.map { it.copy(content = getAttachmentContent(it.id), isPersisted = false) },
                    )
                }
            }

        private fun getAttachmentContent(attachmentId: String) =
            Content {
                val secretKey = key.toString()

                request(source.urlTo("/attachments/$attachmentId/content"), secretKey) {
                    inputStream
                }
            }

        private fun <R> request(
            uri: URI,
            secretKey: String,
            block: HttpURLConnection.() -> R,
        ): R =
            (uri.toURL().openConnection() as HttpURLConnection)
                .apply {
                    connectTimeout = 5000
                    readTimeout = 300000
                    requestMethod = "GET"
                    doInput = true
                    setRequestProperty("Authorization", secretKey)

                    check(responseCode == HttpURLConnection.HTTP_OK) {
                        "Error $responseCode occurred"
                    }
                }.block()
    }

    private var cancellation: () -> Unit = {}

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
                    get("/shared/${entry.id}") {
                        val secretKey = call.request.headers[HttpHeaders.Authorization]
                        if (secretKey != sharingCode.key.toString()) {
                            call.respond(HttpStatusCode.Forbidden)
                        }

                        call.respond(entry)
                    }
                    get("/attachments/{id}/content") {
                        val secretKey = call.request.headers[HttpHeaders.Authorization]
                        if (secretKey != sharingCode.key.toString()) {
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

    fun stop() {
        cancellation()
        cancellation = {}
    }
}
