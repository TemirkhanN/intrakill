package me.nasukhov.intrakill.scene

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
import me.nasukhov.intrakill.AppState
import me.nasukhov.intrakill.storage.EntryPreview
import me.nasukhov.intrakill.storage.FilePicker
import me.nasukhov.intrakill.storage.SecureDatabase
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

// commonMain

data class PickedMedia(
    val name: String,
    val bytes: ByteArray,
    val mimeType: String,
) {
    val mediaType = when {
        mimeType.startsWith("image/") && mimeType != "image/gif" -> "image"
        mimeType == "image/gif" -> "gif"
        mimeType.startsWith("video/") -> "video"
        else -> error("Unsupported media type: $mimeType")
    }

    fun generateImagePreview(maxSize: Int = 256): ByteArray {
        val img = ImageIO.read(ByteArrayInputStream(bytes))
        val scale = minOf(
            maxSize.toDouble() / img.width,
            maxSize.toDouble() / img.height,
            1.0
        )

        val w = (img.width * scale).toInt()
        val h = (img.height * scale).toInt()

        val resized = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = resized.createGraphics()
        g.drawImage(img, 0, 0, w, h, null)
        g.dispose()

        val out = ByteArrayOutputStream()
        ImageIO.write(resized, "jpg", out)
        return out.toByteArray()
    }
}

data class MediaEntry(
    val media: List<PickedMedia>,
    val tags: List<String>
)

// commonMain

object MediaRepository {

    fun save(entry: MediaEntry) {
        SecureDatabase.saveEntry(entry)
    }

    fun listEntries(): List<EntryPreview>  = SecureDatabase.listEntries()
}

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
                    MediaEntry(
                        media = selected,
                        tags = tags
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
