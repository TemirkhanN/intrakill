package me.nasukhov.intrakill.scene

import androidx.compose.runtime.Composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
    filteredByTags: List<String> = emptyList(),
    offset: Int = 0,
) {
    val maxEntriesPerPage = 20
    val eventEmitter = LocalEventEmitter.current
    val entries = MediaRepository.findEntries(
        EntriesFilter(maxEntriesPerPage, offset, filteredByTags)
    )

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
                    )
                }
            }

            // --- Grid items ---
            items(entries) { entry ->
                EntryCell(entry)
            }
        }
    }
}

