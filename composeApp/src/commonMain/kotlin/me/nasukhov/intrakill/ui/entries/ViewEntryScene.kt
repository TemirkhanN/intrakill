package me.nasukhov.intrakill.ui.entries

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import me.nasukhov.intrakill.ui.view.ConfirmationDialog
import me.nasukhov.intrakill.ui.view.Notifications
import me.nasukhov.intrakill.ui.view.ReturnButton

@Composable
fun ViewEntryScene(component: EntryComponent) {
    val state by component.state.subscribeAsState()

    if (state.isWaitingForActionConfirmation) {
        ConfirmationDialog(
            text = "Delete entirely?",
            onConfirm = component::confirmDelete,
            onCancel = component::cancelDelete,
        )
    }

    Crossfade(targetState = state.isLoading) { isLoading ->
        val currentEntry = state.entry
        val isEditing = state.isEditing
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (currentEntry == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column {
                    Text("Entry does not exist. It was probably deleted")
                    ReturnButton(component::close)
                }
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ReturnButton(component::close)

                    Text(currentEntry.name, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.padding(8.dp))

                    Row {
                        IconButton(
                            onClick = component::toggleEditMode,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            if (isEditing) {
                                Icon(Icons.Filled.Done, contentDescription = "Switch to view mode")
                            } else {
                                Icon(Icons.Default.Edit, contentDescription = "Switch to edit mode")
                            }
                        }
                        if (isEditing) {
                            IconButton(
                                onClick = component::deleteEntry,
                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete entirely")
                            }
                        }
                    }
                }

                item {
                    if (isEditing) {
                        TagsInput(
                            knownTags = state.knownTags,
                            selectedTags = currentEntry.tags,
                            onTagsChanged = component::changeTags,
                            isEnabled = !state.isSaving,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            enabled = !state.isSaving,
                            onClick = component::promptAttachmentSelection,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Add attachments")
                        }

                        if (state.notifications.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Notifications(state.notifications)
                        }
                    } else {
                        TagList(
                            tags = currentEntry.tags,
                            onTagsChanged = component::onTagsChanged,
                        )
                    }
                }

                items(currentEntry.attachments) { attachment ->
                    AttachmentView(
                        attachment,
                        editMode = isEditing,
                        onMoveUp = { component.moveAttachmentUpwards(attachment) },
                        onMoveDown = { component.moveAttachmentDownwards(attachment) },
                        onDelete = { component.deleteAttachment(attachment) },
                    )
                }

                item {
                    ReturnButton(component::close)
                }
            }
        }
    }
}
