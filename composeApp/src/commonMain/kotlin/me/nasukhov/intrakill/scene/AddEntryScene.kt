package me.nasukhov.intrakill.scene

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.AppEvent
import me.nasukhov.intrakill.LocalEventEmitter
import me.nasukhov.intrakill.component.AttachmentView
import me.nasukhov.intrakill.component.TagsInput
import me.nasukhov.intrakill.content.Attachment
import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.MediaRepository
import me.nasukhov.intrakill.storage.FilePicker
import me.nasukhov.intrakill.storage.SecureDatabase

@Composable
fun AddContentScene() {
    val selected = remember { mutableStateListOf<Attachment>() }
    var tags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var nameInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val knownTags = SecureDatabase.listTags()

    val scope = rememberCoroutineScope()
    val eventEmitter = LocalEventEmitter.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TextButton(onClick = { eventEmitter.emit(AppEvent.Back) }) {
            Text("← Cancel")
        }

        Button(
            enabled = !isSaving,
            onClick = {
                scope.launch {
                    val picked = FilePicker.pickMultiple()
                    selected.addAll(picked.map {
                        Attachment(
                            mimeType = it.mimeType,
                            content = it.bytes,
                            preview = it.rawPreview
                        )
                    })
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select photos / videos")
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(0.5f)
        ) {
            // 2. Use itemsIndexed to get the current position for reordering logic
            itemsIndexed(selected) { index, media ->
                AttachmentView(
                    attachment = media,
                    editMode = true,
                    onDelete = { selected.removeAt(index) },
                    onMoveUp = {
                        if (index > 0) {
                            val item = selected.removeAt(index)
                            selected.add(index - 1, item)
                        }
                    },
                    onMoveDown = {
                        if (index < selected.lastIndex) {
                            val item = selected.removeAt(index)
                            selected.add(index + 1, item)
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text("Name or description") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving
        )

        TagsInput(
            allTags = knownTags,
            onTagsChanged = { tags = it },
            isEnabled = !isSaving
        )

        Spacer(Modifier.height(12.dp))

        Button(
            enabled = !isSaving,
            onClick = {
                if (selected.isEmpty() || tags.isEmpty() || nameInput.isEmpty()) {
                    error = "Select files and enter tags"
                    return@Button
                }

                scope.launch {
                    isSaving = true
                    error = null
                    try {
                        val entry = MediaRepository.save(
                            Entry(
                                name = nameInput,
                                preview = selected.first().preview,
                                attachments = selected.toList(), // Snapshot of current list. Can it change and retrigger?
                                tags = tags,
                            )
                        )
                        eventEmitter.emit(AppEvent.ViewEntry(entry.id))
                    } catch (e: Exception) {
                        error = "Failed to save: ${e.message}"
                    } finally {
                        isSaving = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSaving) {
                CircularProgressIndicator()
            } else {
                Text("Save")
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color.Red)
        }
    }
}
