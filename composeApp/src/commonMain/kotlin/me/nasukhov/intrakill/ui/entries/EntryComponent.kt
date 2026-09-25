package me.nasukhov.intrakill.ui.entries

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.backhandler.BackCallback
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.domain.model.Attachment
import me.nasukhov.intrakill.domain.model.Entry
import me.nasukhov.intrakill.domain.model.Tag
import me.nasukhov.intrakill.domain.model.combine
import me.nasukhov.intrakill.domain.model.moveDownwards
import me.nasukhov.intrakill.domain.model.moveUpwards
import me.nasukhov.intrakill.domain.model.remove
import me.nasukhov.intrakill.domain.repository.MediaRepository
import me.nasukhov.intrakill.kmp.coroutineScope
import me.nasukhov.intrakill.storage.FilePicker
import me.nasukhov.intrakill.storage.Sharing
import me.nasukhov.intrakill.ui.root.Request
import me.nasukhov.intrakill.ui.view.Notification
import qrcode.QRCode

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
        scope.launch {
            sharing?.stop()
            navigate(Request.Back)
        }
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

            sharing = Sharing(entry)

            scope.launch {
                update { it.copy(sharingQRCode = sharing?.start()) }
            }
        }
    }

    override fun stopSharing() {
        scope.launch {
            sharing?.stop()
        }
        mutableState.update { it.copy(sharingQRCode = null) }
    }
}
