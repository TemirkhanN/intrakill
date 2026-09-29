package me.nasukhov.intrakill.ui.tag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.domain.model.Tag
import me.nasukhov.intrakill.domain.repository.MediaRepository
import me.nasukhov.intrakill.kmp.TextSizeByWeightScaler
import me.nasukhov.intrakill.kmp.coroutineScope
import me.nasukhov.intrakill.ui.root.Request
import me.nasukhov.intrakill.ui.view.ReturnButton

data class TagsState(
    val activeTag: Tag? = null,
    val knownTags: Set<Tag> = emptySet(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val newName: String = "",
)

class TagsComponent(
    private val mediaRepository: MediaRepository = MediaRepository,
    private val navigate: (Request) -> Unit,
    val context: ComponentContext,
) : ComponentContext by context {
    private val mutableState = MutableValue(TagsState())

    val state: Value<TagsState>
        get() = mutableState

    private val scope = instanceKeeper.coroutineScope()

    init {
        scope.launch {
            val allTags = mediaRepository.listTags()
            mutableState.update { it.copy(knownTags = allTags) }
        }
    }

    fun selectTag(tag: Tag) {
        mutableState.update { it.copy(activeTag = tag) }
    }

    fun back() = navigate(Request.Back)

    fun showTaggedEntries(tag: Tag) = navigate(Request.ListEntries(setOf(tag.name)))

    fun deleteTag(tag: Tag) {
        scope.launch {
            mediaRepository.deleteTag(tag)
        }
        back()
    }

    fun startEditing() {
        val activeTag = state.value.activeTag
        check(!state.value.isSaving && activeTag != null)

        mutableState.update {
            it.copy(
                isEditing = true,
                newName = activeTag.name,
            )
        }
    }

    fun setNewTagName(newName: String) {
        check(state.value.isEditing && !state.value.isSaving)

        mutableState.update { it.copy(newName = newName) }
    }

    fun saveChanges() {
        val isSaving = state.value.isSaving
        val isEditing = state.value.isEditing
        val activeTag = state.value.activeTag

        if (isSaving || !isEditing || activeTag == null) {
            return
        }

        val oldName = activeTag.name
        val newName = state.value.newName
        val isUnchanged = oldName == newName
        if (isUnchanged) {
            return
        }

        mutableState.update { it.copy(isSaving = true) }

        scope.launch {
            try {
                val newActiveTag = mediaRepository.renameTag(oldName, newName)
                mutableState.update {
                    it.copy(isEditing = false, isSaving = false, activeTag = newActiveTag)
                }
            } finally {
                mutableState.update { it.copy(isSaving = false) }
            }
        }
    }
}

@Composable
fun TagsScene(component: TagsComponent) {
    val state by component.state.subscribeAsState()

    val activeTag = state.activeTag
    if (activeTag != null) {
        TagScene(activeTag, component)

        return
    }

    val tags = state.knownTags

    val fontSizeScaler =
        TextSizeByWeightScaler(
            minFontSize = 10.sp,
            maxFontSize = 50.sp,
            minWeight = tags.minOfOrNull { it.frequency } ?: 1,
            maxWeight = tags.maxOfOrNull { it.frequency } ?: 1,
        )

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    ReturnButton(component::back)
                }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tags.forEach {
                            SuggestionChip(
                                onClick = { component.selectTag(it) },
                                label = { Text(it.name, fontSize = fontSizeScaler.getSize(it.frequency)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagScene(
    activeTag: Tag,
    component: TagsComponent,
) {
    val state by component.state.subscribeAsState()

    val isEditing = state.isEditing
    val isSaving = state.isSaving

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ReturnButton(component::back)
            if (isEditing) {
                OutlinedTextField(value = state.newName, onValueChange = component::setNewTagName, enabled = !isSaving)
                Button(onClick = component::saveChanges, enabled = !isSaving) { Text("Save") }

                return
            }

            Text(text = activeTag.name, style = MaterialTheme.typography.headlineLarge)
            TextButton(onClick = { component.showTaggedEntries(activeTag) }) {
                Text("meets ${activeTag.frequency} times", style = MaterialTheme.typography.bodyLarge)
            }
            Button(onClick = component::startEditing) { Text("Edit") }
            Button({ component.deleteTag(activeTag) }) { Text("Delete") }
        }
    }
}
