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

import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.MediaRepository

@Composable
fun EntryCell(
    entry: Entry,
    onClick: (entryId: String) -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .border(1.dp, Color.Gray)
            .clickable { onClick(entry.id) }
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
    entries: List<Entry> = MediaRepository.listEntries(),
    onEntryClick: (entryId: String) -> Unit = {},
    onNewEntryButtonClick: () -> Unit = {},
) {
    val visible = entries.take(20)          // max 4 × 5
    val rows = visible.chunked(4)

    Box {
        Button(
            onClick = onNewEntryButtonClick,
            content = { Text(text = "+") }
        )
    }
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