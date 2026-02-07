package me.nasukhov.intrakill.scene

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import me.nasukhov.intrakill.content.Attachment
import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.MediaRepository
import me.nasukhov.intrakill.storage.FilePicker
import me.nasukhov.intrakill.storage.PickedMedia
import me.nasukhov.intrakill.storage.SecureDatabase

@Composable
fun AddContentScene() {
    var selected by remember { mutableStateOf<List<PickedMedia>>(emptyList()) }
    var tagsInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val eventEmitter = LocalEventEmitter.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Button(
            onClick = {
                scope.launch {
                    selected = FilePicker.pickMultiple()
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
                .fillMaxWidth()
        ) {
            items(selected) { media ->
                    Image(
                        bitmap = media.preview,
                        contentDescription = null,
                    )
                    Text(
                        text = media.name,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it.trim() },
            label = { Text("Name or description") },
            modifier = Modifier.fillMaxWidth()
        )

        TagsInput(
            allTags = SecureDatabase.listTags().map { it.name }.toSet(),
            onTagsChanged = { tagsInput = it.joinToString{","} },
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                val tags = tagsInput
                    .split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .toSet()

                if (selected.isEmpty() || tags.isEmpty() || nameInput.isEmpty()) {
                    error = "Select files and enter tags"
                    return@Button
                }

                val entry = MediaRepository.save(
                    Entry(
                        name = nameInput,
                        preview = selected.first().rawPreview,
                        attachments = selected.map {
                            Attachment(
                                mimeType = it.mimeType,
                                content = it.bytes,
                                preview = it.rawPreview
                            )
                        },
                        tags = tags,
                    )
                )

                selected = emptyList()
                tagsInput = ""
                error = null
                eventEmitter.emit(AppEvent.ViewEntry(entry.id))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color.Red)
        }
    }
}

@Composable
fun TagsInput(
    allTags: Set<String>,
    modifier: Modifier = Modifier,
    onTagsChanged: (Set<String>) -> Unit
) {
    var text by remember { mutableStateOf("") }

    val parts = text.split(",").map { it.trim() }
    val currentPrefix = parts.lastOrNull().orEmpty()

    val suggestions = remember(currentPrefix, allTags) {
        if (currentPrefix.isBlank()) emptyList()
        else allTags.filter {
            it.startsWith(currentPrefix, ignoreCase = true) && !parts.contains(it)
        }
    }

    Column(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onTagsChanged(
                    it.split(",")
                        .map { s -> s.trim() }
                        .filter { s -> s.isNotEmpty() }
                        .toSet()
                )
            },
            label = { Text("Tags (comma separated)") },
            modifier = Modifier.fillMaxWidth()
        )

        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                suggestions.take(6).forEach { tag ->
                    Text(
                        text = tag,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newText =
                                    parts.dropLast(1)
                                        .plus(tag)
                                        .joinToString(", ") + ", "

                                text = newText
                                onTagsChanged(
                                    newText.split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .toSet()
                                )
                            }
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

