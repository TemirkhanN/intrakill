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
import me.nasukhov.intrakill.component.Paginator
import me.nasukhov.intrakill.content.EntriesSearchResult
import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.MediaRepository
import me.nasukhov.intrakill.storage.EntriesFilter
import me.nasukhov.intrakill.storage.SecureDatabase

@Composable
private fun EntryCell(entry: Entry) {
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
    var offset by remember { mutableIntStateOf(initialOffset) }

    // Use produceState to manage the query. Remember introduces lag, and combined with BoxWithConstraint recomputation
    // duplicates the request.
    val searchResultState = produceState<EntriesSearchResult?>(
        initialValue = null,
        key1 = selectedTags,
        key2 = offset
    ) {
        value = MediaRepository.findEntries(
            EntriesFilter(limit = maxEntriesPerPage, offset = offset, tags = selectedTags)
        )
    }

    val searchResult = searchResultState.value

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints {
            val columns = if (maxWidth < 600.dp) 1 else 4

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = { eventEmitter.emit(AppEvent.AddNewEntry) }) {
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

                if (searchResult != null) {
                    items(searchResult.entries) { entry ->
                        EntryCell(entry)
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Paginator(
                            offset,
                            maxEntriesPerPage,
                            searchResult.outOfTotal,
                            onOffsetChange = { offset = it }
                        )
                    }
                }
            }
        }

        if (searchResult == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = false) {} // Block interactions while loading
                    .padding(top = 100.dp), // Offset it so it doesn't block the header(TODO)
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
