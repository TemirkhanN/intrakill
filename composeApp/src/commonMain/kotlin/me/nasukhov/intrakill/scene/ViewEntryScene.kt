package me.nasukhov.intrakill.scene

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.nasukhov.intrakill.AppEvent
import me.nasukhov.intrakill.LocalEventEmitter
import me.nasukhov.intrakill.content.MediaRepository
import me.nasukhov.intrakill.storage.MediaKind

@Composable
fun ViewEntryScene(entryId: String) {
    val entry = remember(entryId) {
        MediaRepository.getById(entryId)
    }

    val eventEmitter = LocalEventEmitter.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = { eventEmitter.emit(AppEvent.Back) }) {
                Text("← Back")
            }
        }

        item {
            TagList(
                tags = entry.tags,
                onTagsChanged = {
                    eventEmitter.emit(AppEvent.TagsSelected(it))
                }
            )
        }
        items(entry.attachments) { attachment ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                when (attachment.mediaKind) {
                    MediaKind.VIDEO -> {
                        // TODO
                        Image(
                            bitmap = attachment.preview.asImageBitmap(),
                            contentDescription = "TODO",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    MediaKind.IMAGE -> {
                        Image(
                            bitmap = attachment.content.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        Text("No preview", color = Color.Gray)
                    }
                }
            }
        }
    }
}