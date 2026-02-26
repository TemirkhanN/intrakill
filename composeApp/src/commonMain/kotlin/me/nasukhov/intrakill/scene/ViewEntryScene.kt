package me.nasukhov.intrakill.scene

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import me.nasukhov.intrakill.view.AttachmentView
import me.nasukhov.intrakill.component.EntryComponent
import me.nasukhov.intrakill.view.ReturnButton
import me.nasukhov.intrakill.view.TagsInput

@Composable
fun ViewEntryScene(component: EntryComponent) {
    val state by component.state.subscribeAsState()

    Crossfade(targetState = state.isLoading) { isLoading ->
        val currentEntry = state.entry
        val isEditing = state.isEditing
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (currentEntry == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column {
                    Text("Entry does not exist. It was probably deleted")
                    ReturnButton(component::close)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ReturnButton(component::close)
                    TextButton(onClick = component::toggleEditMode) {
                        Text(if (isEditing) "View mode" else "Edit mode")
                    }
                    Text(currentEntry.name)
                    if (isEditing) {
                        TextButton(onClick = component::deleteEntry) {
                            Text("Delete entirely")
                        }
                    }
                }

                item {
                    if (isEditing) {
                        TagsInput(
                            knownTags = state.knownTags,
                            selectedTags = currentEntry.tags,
                            onTagsChanged = component::changeTags,
                            isEnabled = !state.isSaving
                        )
                    } else {
                        TagList(
                            tags = currentEntry.tags,
                            onTagsChanged = component::onTagsChanged
                        )
                    }
                }

                items(currentEntry.attachments) { attachment ->
                    AttachmentView(
                        attachment,
                        editMode = isEditing,
                        onMoveUp = {}, // TODO
                        onMoveDown = {}, // TODO
                        onDelete = { if (currentEntry.attachments.size > 1) { component.deleteAttachment(attachment) } },
                    )
                }

                item {
                    ReturnButton(component::close)
                }
            }
        }
    }
}
