package me.nasukhov.intrakill.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.backhandler.BackCallback
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.content.Attachment
import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.MediaRepository
import me.nasukhov.intrakill.content.Tag
import me.nasukhov.intrakill.content.moveDownwards
import me.nasukhov.intrakill.content.moveUpwards
import me.nasukhov.intrakill.content.remove
import me.nasukhov.intrakill.navigation.Request
import me.nasukhov.intrakill.scene.coroutineScope
import me.nasukhov.intrakill.storage.FilePicker
import me.nasukhov.intrakill.view.Notification

data class EntryState(
    val entryId: String,
    val entry: Entry? = null,
    val knownTags: Set<Tag> = emptySet(),
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isWaitingForActionConfirmation: Boolean = false,
    val notifications: List<Notification> = emptyList(),
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

    init {
        scope.launch {
            var entry = MediaRepository.findById(entryId)
            val knownTags = MediaRepository.listTags()
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
                MediaRepository.deleteById(it.entry.id)
                navigate(Request.Back)
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
                    MediaRepository.save(
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
                    MediaRepository.save(
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
                    MediaRepository.save(
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
                val updatedEntry = MediaRepository.save(current.entry.copy(tags = tags))

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
                        MediaRepository.save(
                            current.entry.copy(attachments = newAttachments + current.entry.attachments),
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
}
