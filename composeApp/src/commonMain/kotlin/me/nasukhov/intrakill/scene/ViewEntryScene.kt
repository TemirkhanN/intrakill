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

@Composable
fun ViewEntryScene(component: EntryComponent) {
    val state by component.state.subscribeAsState()

    Crossfade(targetState = state.isLoading) { isLoading ->
        val currentEntry = state.entry
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
                    TextButton(onClick = component::onReturnClicked) {
                        Text("← Back")
                    }
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
                    TextButton(onClick = component::onReturnClicked) {
                        Text("← Back")
                    }
                    TextButton(onClick = component::toggleEditMode) {
                        Text(if (state.isEditing) "View mode" else "Edit mode")
                    }
                    if (state.isEditing) {
                        TextButton(onClick = component::onDeletePressed) {
                            Text("Delete entirely")
                        }
                    }
                }

                item {
                    TagList(
                        tags = currentEntry.tags,
                        onTagsChanged = component::onTagsChanged
                    )
                }

                items(currentEntry.attachments) { attachment ->
                    AttachmentView(
                        attachment,
                        editMode = state.isEditing,
                        onMoveUp = {},
                        onMoveDown = {},
                        onDelete = {}
                    )
                }

                item {
                    TextButton(onClick = component::onReturnClicked) {
                        Text("← Back")
                    }
                }
            }
        }
    }
}
