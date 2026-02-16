package me.nasukhov.intrakill.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.content.EntriesSearchResult
import me.nasukhov.intrakill.content.MediaRepository
import me.nasukhov.intrakill.content.Tag
import me.nasukhov.intrakill.navigation.Request
import me.nasukhov.intrakill.scene.asFlow
import me.nasukhov.intrakill.scene.coroutineScope
import me.nasukhov.intrakill.storage.EntriesFilter

data class ListState(
    val filteredByTags: Set<String> = emptySet(),
    val offset: Int = 0,
    val searchResult: EntriesSearchResult? = null,
    val isSearching: Boolean = false,
)

interface ListEntriesComponent {
    val state: Value<ListState>
    val knownTags: Value<Set<Tag>>

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
    private data class FilterParams(
        val tags: Set<String> = emptySet(),
        val offset: Int = 0,
        val limit: Int = 12
    )

    override val knownTags = MutableValue<Set<Tag>>(emptySet())

    private val mutableState = MutableValue(ListState(filteredByTags = filterByTags))
    override val state: Value<ListState> = mutableState

    private val filters = MutableValue(FilterParams(tags = filterByTags))

    private val scope = instanceKeeper.coroutineScope()

    init {
        scope.launch {
            merge(
                filters.asFlow().map { it to false },
                MediaRepository.updates.map { filters.value to true }
            )
            .collectLatest { (filter, isStorageUpdated) ->
                mutableState.update { it.copy(
                    isSearching = true,
                    filteredByTags = filter.tags,
                    offset = filter.offset,
                ) }

                val result = MediaRepository.findEntries(
                    EntriesFilter(limit = filter.limit, offset = filter.offset, tags = filter.tags)
                )

                mutableState.update { it.copy(
                    searchResult = result,
                    isSearching = false
                ) }
                if (isStorageUpdated) {
                    refreshKnownTags()
                }
            }
        }

        refreshKnownTags()
    }

    override fun onTagsChanged(tags: Set<String>) {
        filters.update { it.copy(tags = tags, offset = 0) }
    }

    override fun onOffsetChanged(offset: Int) {
        filters.update { it.copy(offset = offset) }
    }

    override fun onEntryClicked(id: String) = navigate(Request.ViewEntry(id))
    override fun onAddClicked() = navigate(Request.AddEntry)

    private fun refreshKnownTags() {
        scope.launch {
            val allTags = MediaRepository.listTags()
            knownTags.update { allTags }
        }
    }
}