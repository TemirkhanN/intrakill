package me.nasukhov.intrakill.scene

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.nasukhov.intrakill.content.MediaRepository

@Composable
fun ViewEntryScene(entryId: String) {
    val entry = MediaRepository.getById(entryId)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(entry.attachments) { attachment ->
            val bitmap = attachment.content.asImageBitmap()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f), // square, full width
                contentAlignment = Alignment.Center
            ) {
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
                if (entry.id == "video") {
                    Text(
                        text = "▶",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .border(1.dp, Color.Black)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}