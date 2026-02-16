package me.nasukhov.intrakill.scene

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import me.nasukhov.intrakill.navigation.ListEntriesComponent
import me.nasukhov.intrakill.component.Paginator

@Composable
fun ListEntriesScene(component: ListEntriesComponent) {
    val filteredByTags by component.filteredByTags.subscribeAsState()
    val offset by component.offset.subscribeAsState()
    val searchResultState by component.searchResult.subscribeAsState()
    val knownTags by component.knownTags.subscribeAsState()
    val searchResult = searchResultState.data

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = component.gridState, // Crucial: This preserves scroll!
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = component::onAddClicked) { Text("+ Add New") }

                    // TODO expanded resets
                    TagList(
                        tags = knownTags.map { it.name }.toSet(),
                        selectedTags = filteredByTags,
                        onTagsChanged = component::onTagsChanged
                    )
                }
            }

            if (searchResult != null) {
                items(searchResult.entries) { entry ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .border(1.dp, Color.Gray)
                            .clickable { component.onEntryClicked(entry.id) }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = entry.preview.asImageBitmap(),
                            contentDescription = null
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Paginator(
                        offset = offset,
                        maxEntriesPerPage = 12,
                        total = searchResult.outOfTotal,
                        onOffsetChange = component::onOffsetChanged
                    )
                }
            }
        }

        if (searchResult == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}