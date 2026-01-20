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
import me.nasukhov.intrakill.content.Attachment
import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.MediaRepository
import me.nasukhov.intrakill.storage.FilePicker
import me.nasukhov.intrakill.storage.PickedMedia

// commonMain

@Composable
fun AddContentScene(onSuccess: () -> Unit) {
    var selected by remember { mutableStateOf<List<PickedMedia>>(emptyList()) }
    var tagsInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

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

                if (selected.isEmpty() || tags.isEmpty()) {
                    error = "Select files and enter tags"
                    return@Button
                }

                MediaRepository.save(
                    Entry(
                        preview = selected.first().generateImagePreview(),
                        attachments = selected.map {
                            Attachment(
                                mimeType = it.mimeType,
                                content = it.bytes,
                                preview = it.generateImagePreview()
                            )
                        },
                        tags = tags,
                    )
                )

                selected = emptyList()
                tagsInput = ""
                error = null
                onSuccess()
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
