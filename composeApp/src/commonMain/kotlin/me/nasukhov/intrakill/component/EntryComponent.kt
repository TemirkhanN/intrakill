package me.nasukhov.intrakill.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.MediaRepository
import me.nasukhov.intrakill.navigation.Request
import me.nasukhov.intrakill.scene.coroutineScope

data class EntryState(
    val entryId: String,
    val entry: Entry? = null,
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
)

interface EntryComponent {
    val state: Value<EntryState>

    fun onReturnClicked()

    fun deleteEntry()

    fun onTagsChanged(newTags: Set<String>)

    fun toggleEditMode()
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
            mutableState.update { it.copy(
                entry = entry,
                isLoading = false,
            ) }
        }
    }

    override fun onReturnClicked() {
        navigate(Request.Back)
    }

    override fun deleteEntry() {
        state.value.let {
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
        require(state.value.entry != null)
        mutableState.update { it.copy(isEditing = !it.isEditing) }
    }
}