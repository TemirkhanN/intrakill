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
import me.nasukhov.intrakill.component.ListEntriesComponent
import me.nasukhov.intrakill.view.Paginator
import me.nasukhov.intrakill.view.TagsInput

@Composable
fun ListEntriesScene(component: ListEntriesComponent) {
    val state by component.state.subscribeAsState()
    val knownTags by component.knownTags.subscribeAsState()

    val gridState = rememberLazyGridState()

    val isSearching = state.isSearching
    val searchResult = state.searchResult

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = component::onAddClicked) { Text("+ Add New") }

                    TagsInput(
                        knownTags = knownTags,
                        onTagsChanged = component::onTagsChanged,
                        selectedTags = state.filteredByTags,
                        maxSuggestions = 8,
                    )
                }
            }

            if (!isSearching && searchResult !== null) {
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
                            contentDescription = entry.name
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Paginator(
                        offset = state.offset,
                        maxEntriesPerPage = state.entriesPerPage,
                        total = searchResult.outOfTotal,
                        onOffsetChange = component::onOffsetChanged
                    )
                }
            }
        }

        if (state.isSearching) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}