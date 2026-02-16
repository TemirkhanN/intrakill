package me.nasukhov.intrakill.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.MediaRepository
import me.nasukhov.intrakill.navigation.Request
import me.nasukhov.intrakill.scene.OptionalValue
import me.nasukhov.intrakill.scene.coroutineScope

interface EntryComponent {
    val entry: Value<OptionalValue<Entry>>
    val isEditing: Value<Boolean>

    fun onReturnClicked()

    fun onDeletePressed()

    fun onTagsChanged(newTags: Set<String>)

    fun toggleEditMode()
}

class DefaultEntryComponent(
    context: ComponentContext,
    entryId: String,
    private val navigate: (Request) -> Unit
): EntryComponent, ComponentContext by context {
    override val isEditing = MutableValue(false)
    private val scope = instanceKeeper.coroutineScope()

    override val entry = MutableValue(OptionalValue<Entry>())

    init {
        scope.launch {
            var entryValue = MediaRepository.getById(entryId)
            entry.update { OptionalValue(entryValue) }
        }
    }

    override fun onReturnClicked() {
        navigate(Request.Back)
    }

    override fun onDeletePressed() {
        require(isEditing.value)
        scope.launch {
            MediaRepository.deleteById(entry.value.get().id)
            navigate(Request.Back)
        }
    }

    override fun onTagsChanged(newTags: Set<String>) {
        navigate(Request.ListEntries(filterByTags = newTags))
    }

    override fun toggleEditMode() {
        isEditing.value = !isEditing.value
    }
}