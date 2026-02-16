package me.nasukhov.intrakill.component

import androidx.compose.foundation.lazy.grid.LazyGridState
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.decompose.value.update
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.content.EntriesSearchResult
import me.nasukhov.intrakill.content.MediaRepository
import me.nasukhov.intrakill.content.Tag
import me.nasukhov.intrakill.navigation.Request
import me.nasukhov.intrakill.scene.coroutineScope
import me.nasukhov.intrakill.storage.EntriesFilter

data class SearchResultState(val data: EntriesSearchResult? = null)

interface ListEntriesComponent {
    val filteredByTags: Value<Set<String>>
    val knownTags: Value<Set<Tag>>
    val offset: Value<Int>
    val searchResult: Value<SearchResultState>
    val gridState: LazyGridState

    fun onTagsChanged(tags: Set<String>)
    fun onOffsetChanged(offset: Int)
    fun onEntryClicked(id: String)
    fun onAddClicked()
}

class DefaultListEntriesComponent(
    context: ComponentContext,
    filterByTags: Set<String> = emptySet(),
    private val navigate: (Request) -> Unit
) : ListEntriesComponent, ComponentContext by context {

    override val gridState = LazyGridState()

    override val knownTags = MutableValue<Set<Tag>>(emptySet())

    private val _state = MutableValue(ListState(tags = filterByTags))
    override val filteredByTags: Value<Set<String>> = _state.map { it.tags }
    override val offset: Value<Int> = _state.map { it.offset }

    override val searchResult = MutableValue(SearchResultState())

    private val scope = instanceKeeper.coroutineScope()

    init {
        // Observe state changes and refresh data
        _state.subscribe { state ->
            scope.launch {
                val result = MediaRepository.findEntries(
                    EntriesFilter(limit = 12, offset = state.offset, tags = state.tags)
                )
                val allTags = MediaRepository.listTags()

                searchResult.update { it.copy(data = result) }
                knownTags.update { allTags }
            }
        }
    }

    override fun onTagsChanged(tags: Set<String>) {
        _state.update { it.copy(tags = tags, offset = 0) }
    }

    override fun onOffsetChanged(offset: Int) {
        _state.update { it.copy(offset = offset) }
    }

    override fun onEntryClicked(id: String) = navigate(Request.ViewEntry(id))
    override fun onAddClicked() = navigate(Request.AddEntry)

    private data class ListState(val tags: Set<String> = emptySet(), val offset: Int = 0)
}