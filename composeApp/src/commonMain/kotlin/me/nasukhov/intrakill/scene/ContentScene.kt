package me.nasukhov.intrakill.scene

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import me.nasukhov.intrakill.storage.EntryPreview
import org.jetbrains.skia.Image

fun previewBitmap(bytes: ByteArray): ImageBitmap? {
    return try {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }
}

@Composable
fun EntryCell(
    entry: EntryPreview,
    onClick: (EntryPreview) -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .border(1.dp, Color.Gray)
            .clickable { onClick(entry) }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = previewBitmap(entry.previewBytes)

        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("No preview", color = Color.Gray)
        }

        // Optional video overlay
        if (entry.mediaType == "video") {
            Text(
                text = "▶",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .border(1.dp, Color.Black)
            )
        }
    }
}

@Composable
fun ContentScene(
    entries: List<EntryPreview> = MediaRepository.listEntries(),
    onEntryClick: (EntryPreview) -> Unit = {}
) {
    val visible = entries.take(20)          // max 4 × 5
    val rows = visible.chunked(4)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { entry ->
                    Box(modifier = Modifier.weight(1f)) {
                        EntryCell(entry, onEntryClick)
                    }
                }

                // fill empty columns if last row < 4
                repeat(4 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}