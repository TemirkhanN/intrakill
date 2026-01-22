package me.nasukhov.intrakill.scene

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
    val entries = MediaRepository.findEntries(EntriesFilter(maxEntriesPerPage, offset, filteredByTags))
    val rows = entries.chunked(4)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = { eventEmitter.emit(AppEvent.AddNewEntry) },
            content = { Text(text = "+") }
        )
        TagList(
            tags = SecureDatabase.listTags() // TODO
                .sortedByDescending { it.frequency }
                .map { it.name }
        )
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { entry ->
                    Box(modifier = Modifier.weight(1f)) {
                        EntryCell(entry)
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