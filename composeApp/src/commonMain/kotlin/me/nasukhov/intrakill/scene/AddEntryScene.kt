package me.nasukhov.intrakill.scene

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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

        OutlinedTextField(
            value = tagsInput,
            onValueChange = { tagsInput = it },
            label = { Text("Tags (comma separated)") },
            modifier = Modifier.fillMaxWidth()
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
