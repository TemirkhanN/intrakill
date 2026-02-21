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
import me.nasukhov.intrakill.navigation.Request
import me.nasukhov.intrakill.scene.coroutineScope

data class EntryState(
    val entryId: String,
    val entry: Entry? = null,
    val knownTags: Set<Tag> = emptySet(),
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
)

interface EntryComponent {
    val state: Value<EntryState>

    fun close()

    fun deleteEntry()

    fun onTagsChanged(newTags: Set<String>)

    fun toggleEditMode()

    fun deleteAttachment(attachment: Attachment)

    fun changeTags(tags: Set<String>)
}

class DefaultEntryComponent(
    context: ComponentContext,
    entryId: String,
    private val navigate: (Request) -> Unit
): EntryComponent, ComponentContext by context {
    private val mutableState = MutableValue(EntryState(entryId=entryId, isLoading = true))
    override val state: Value<EntryState> = mutableState
    private val scope = instanceKeeper.coroutineScope()

    init {
        scope.launch {
            var entry = MediaRepository.findById(entryId)
            val knownTags = MediaRepository.listTags()
            mutableState.update { it.copy(
                entry = entry,
                isLoading = false,
                knownTags = knownTags,
            ) }
        }

        context.backHandler.register(BackCallback(onBack = ::close))
    }

    override fun close() {
        navigate(Request.Back)
    }

    override fun deleteEntry() {
        mutableState.value.let {
            require(it.isEditing && it.entry != null)
            scope.launch {
                MediaRepository.deleteById(it.entry.id)
                navigate(Request.Back)
            }
        }
    }

    override fun onTagsChanged(newTags: Set<String>) {
        navigate(Request.ListEntries(filterByTags = newTags))
    }

    override fun toggleEditMode() {
        require(mutableState.value.entry != null)
        mutableState.update { it.copy(isEditing = !it.isEditing) }
    }

    override fun deleteAttachment(attachment: Attachment) {
        mutableState.value.let { current ->
            require(current.entry != null)
            require(current.entry.attachments.size > 1) { "Entry must have at least 1 attachment." }

            scope.launch {
                val updatedEntry = MediaRepository.save(
                    current.entry.copy(attachments = current.entry.attachments.minus(attachment))
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
}