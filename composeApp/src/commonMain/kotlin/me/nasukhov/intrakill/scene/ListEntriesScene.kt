package me.nasukhov.intrakill.scene

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val styling = object {
    val gridCells = GridCells.Adaptive(minSize = 144.dp)
    val verticalArrangement = Arrangement.spacedBy(6.dp)
    val horizontalArrangement = Arrangement.spacedBy(6.dp)
    val padding = PaddingValues(8.dp)
    val headerVerticalArrangement = Arrangement.spacedBy(16.dp)

    val cell = object {
        val aspectRatio = 1f
        val padding = 6.dp
        val alignment = Alignment.Center
        val border = object {
            val size = 1.dp
            val color = Color.DarkGray
            val shape = RoundedCornerShape(4.dp)
        }
    }
}

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
            columns = styling.gridCells,
            modifier = Modifier.fillMaxSize(),
            contentPadding = styling.padding,
            verticalArrangement = styling.verticalArrangement,
            horizontalArrangement = styling.horizontalArrangement
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = styling.headerVerticalArrangement) {
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
                            .aspectRatio(styling.cell.aspectRatio)
                            .border(styling.cell.border.size, styling.cell.border.color, styling.cell.border.shape)
                            .clickable { component.onEntryClicked(entry.id) }
                            .padding(styling.cell.padding),
                        contentAlignment = styling.cell.alignment
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