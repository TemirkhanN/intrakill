package me.nasukhov.intrakill.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.backhandler.BackCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.nasukhov.intrakill.content.Attachment
import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.MediaRepository
import me.nasukhov.intrakill.content.Tag
import me.nasukhov.intrakill.content.moveDownwards
import me.nasukhov.intrakill.content.moveUpwards
import me.nasukhov.intrakill.navigation.Request
import me.nasukhov.intrakill.scene.coroutineScope
import me.nasukhov.intrakill.storage.FilePicker

interface AddEntryComponent {
    val state: Value<NewEntryState>

    fun changeName(name: String)

    fun changeTags(newTags: Set<String>)

    fun save()

    fun close()

    fun promptAttachmentSelection()

    fun removeAttachment(attachment: Attachment)

    fun moveAttachmentUpwards(attachment: Attachment)

    fun moveAttachmentDownwards(attachment: Attachment)
}

data class NewEntryState(
    val name: String = "",
    val selectedTags: Set<String> = emptySet(),
    val knownTags: Set<Tag> = emptySet(),
    val attachments: List<Attachment> = emptyList(),
    val violations: List<String> = emptyList(),
    val isSaving: Boolean = false,
)

class DefaultAddEntryComponent(
    context: ComponentContext,
    private val navigate: (Request) -> Unit,
) : AddEntryComponent,
    ComponentContext by context {
    private val scope = instanceKeeper.coroutineScope()
    private val mutableState = MutableValue(NewEntryState())
    override val state: Value<NewEntryState> = mutableState

    init {
        scope.launch {
            val knownTags = MediaRepository.listTags()
            val freshState = NewEntryState(knownTags = knownTags)

            mutableState.value = freshState
        }

        context.backHandler.register(BackCallback(onBack = ::close))
    }

    override fun close() {
        navigate(Request.Back)
    }

    override fun changeName(name: String) {
        mutableState.update {
            it.copy(
                name = name,
                violations = emptyList(),
            )
        }
    }

    override fun changeTags(newTags: Set<String>) {
        mutableState.update {
            it.copy(
                selectedTags = newTags,
                violations = emptyList(),
            )
        }
    }

    override fun promptAttachmentSelection() {
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
            val violations = picked.filter { it.isFailure }.map { it.exceptionOrNull()!!.message ?: "Unknown error" }
            if (!newAttachments.isEmpty()) {
                mutableState.update {
                    it.copy(
                        attachments = newAttachments,
                        violations = violations,
                    )
                }
            } else if (violations.isNotEmpty()) {
                mutableState.update {
                    it.copy(
                        violations = violations,
                    )
                }
            }
        }
    }

    override fun removeAttachment(attachment: Attachment) {
        mutableState.update {
            it.copy(attachments = it.attachments - attachment)
        }
    }

    override fun moveAttachmentUpwards(attachment: Attachment) {
        mutableState.update {
            it.copy(attachments = it.attachments.moveUpwards(attachment))
        }
    }

    override fun moveAttachmentDownwards(attachment: Attachment) {
        mutableState.update {
            it.copy(attachments = it.attachments.moveDownwards(attachment))
        }
    }

    override fun save() {
        val current = state.value
        if (current.isSaving) {
            return
        }

        val violations = validate(current)
        if (!violations.isEmpty()) {
            mutableState.update { it.copy(violations = violations, isSaving = false) }
            return
        }

        scope.launch {
            mutableState.update { it.copy(isSaving = true, violations = violations) }
            try {
                val entry =
                    withContext(Dispatchers.IO) {
                        MediaRepository.save(
                            Entry(
                                name = current.name,
                                preview = current.attachments.first().preview,
                                attachments = current.attachments,
                                tags = current.selectedTags,
                            ),
                        )
                    }
                navigate(Request.ViewEntry(entry.id))
            } catch (e: Exception) {
                mutableState.update { it.copy(violations = listOf("[Fatal] Failed to save: ${e.message}"), isSaving = false) }
            }
        }
    }

    private fun validate(state: NewEntryState): List<String> {
        val violations = mutableListOf<String>()
        if (state.name.isEmpty()) {
            violations.add("Name can't be empty.")
        }

        if (state.attachments.isEmpty()) {
            violations.add("At least one attachment is required.")
        }

        if (state.selectedTags.isEmpty()) {
            violations.add("At least one tags is required.")
        }

        return violations
    }
}
