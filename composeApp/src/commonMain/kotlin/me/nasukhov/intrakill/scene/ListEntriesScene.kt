package me.nasukhov.intrakill.scene

import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.nasukhov.intrakill.AppEvent
import me.nasukhov.intrakill.LocalEventEmitter
import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.MediaRepository
import me.nasukhov.intrakill.storage.EntriesFilter
import me.nasukhov.intrakill.storage.SecureDatabase

@Composable
fun EntryCell(entry: Entry) {
    val eventEmitter = LocalEventEmitter.current

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .border(1.dp, Color.Gray)
            .clickable { eventEmitter.emit(AppEvent.ViewEntry(entry.id)) }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = entry.preview.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun ListEntriesScene(
    initialTags: Set<String> = emptySet(),
    initialOffset: Int = 0,
) {
    val maxEntriesPerPage = 12
    val eventEmitter = LocalEventEmitter.current

    var selectedTags by remember { mutableStateOf(initialTags) }
    var offset by remember { mutableStateOf(initialOffset) }

    val searchResult = remember(selectedTags, offset) {
        MediaRepository.findEntries(
            EntriesFilter(
                limit = maxEntriesPerPage,
                offset = offset,
                tags = selectedTags
            )
        )
    }

    BoxWithConstraints {
        val columns = if (maxWidth < 600.dp) 1 else 4

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {

            // --- Header ---
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = {
                        eventEmitter.emit(AppEvent.AddNewEntry)
                    }) {
                        Text("+")
                    }

                    TagList(
                        tags = SecureDatabase.listTags()
                            .sortedByDescending { it.frequency }
                            .map { it.name }
                            .toSet(),
                        selectedTags = selectedTags,
                        onTagsChanged = {
                            selectedTags = it
                            offset = 0
                        }
                    )
                }
            }

            items(searchResult.entries) { entry ->
                EntryCell(entry)
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(
                        enabled = offset > 0,
                        onClick = {
                            offset = (offset - maxEntriesPerPage).coerceAtLeast(0)
                        }
                    ) {
                        Text("<")
                    }

                    TextButton(
                        enabled = offset + maxEntriesPerPage < searchResult.outOfTotal,
                        onClick = {
                            offset += maxEntriesPerPage
                        }
                    ) {
                        Text(">")
                    }
                }
            }
        }
    }
}
