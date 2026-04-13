package me.nasukhov.intrakill.ui.entries

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import me.nasukhov.intrakill.ui.view.NotFound
import me.nasukhov.intrakill.ui.view.Paginator
import me.nasukhov.intrakill.ui.view.asImageBitmap

private val styling =
    object {
        val gridCells = GridCells.Adaptive(minSize = 144.dp)
        val verticalArrangement = Arrangement.spacedBy(6.dp)
        val horizontalArrangement = Arrangement.spacedBy(6.dp)
        val padding = PaddingValues(8.dp)
        val headerVerticalArrangement = Arrangement.spacedBy(16.dp)

        val cell =
            object {
                val aspectRatio = 1f
                val padding = 6.dp
                val alignment = Alignment.Center
                val border =
                    object {
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
        if (isSearching) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return
        }

        LazyVerticalGrid(
            state = gridState,
            columns = styling.gridCells,
            modifier = Modifier.fillMaxSize(),
            contentPadding = styling.padding,
            verticalArrangement = styling.verticalArrangement,
            horizontalArrangement = styling.horizontalArrangement,
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = styling.headerVerticalArrangement) {
                    Row {
                        IconButton(onClick = component::onAddClicked) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add new")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = component::openSettings) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Open settings")
                        }
                    }

                    TagsInput(
                        knownTags = knownTags,
                        onTagsChanged = component::onTagsChanged,
                        selectedTags = state.filteredByTags,
                        maxSuggestions = 8,
                    )
                }
            }

            if (searchResult !== null) {
                items(searchResult.entries) { entry ->
                    Box(
                        modifier =
                            Modifier
                                .aspectRatio(styling.cell.aspectRatio)
                                .border(styling.cell.border.size, styling.cell.border.color, styling.cell.border.shape)
                                .clickable { component.onEntryClicked(entry.id) }
                                .padding(styling.cell.padding),
                        contentAlignment = styling.cell.alignment,
                    ) {
                        Image(
                            bitmap = entry.preview.asImageBitmap(),
                            contentDescription = entry.name,
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (searchResult.outOfTotal != 0) {
                        Paginator(
                            offset = state.offset,
                            maxEntriesPerPage = state.entriesPerPage,
                            total = searchResult.outOfTotal,
                            onOffsetChange = component::onOffsetChanged,
                        )
                    } else {
                        NotFound()
                    }
                }
            }
        }
    }
}
