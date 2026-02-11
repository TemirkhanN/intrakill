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
import me.nasukhov.intrakill.AppEvent
import me.nasukhov.intrakill.LocalEventEmitter
import me.nasukhov.intrakill.component.AttachmentView
import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.MediaRepository

@Composable
fun ViewEntryScene(entryId: String) {
    val eventEmitter = LocalEventEmitter.current

    val entryState = produceState<Entry?>(initialValue = null, key1 = entryId) {
        value = MediaRepository.getById(entryId)
    }

    val entry = entryState.value

    Crossfade(targetState = entry) { currentEntry ->
        if (currentEntry == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
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
                        tags = currentEntry.tags,
                        onTagsChanged = {
                            eventEmitter.emit(AppEvent.TagsSelected(it))
                        }
                    )
                }

                items(currentEntry.attachments) { attachment ->
                    AttachmentView(attachment)
                }
            }
        }
    }
}
