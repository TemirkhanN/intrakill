package me.nasukhov.intrakill.ui.entries

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.backhandler.BackCallback
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.domain.model.Tag
import me.nasukhov.intrakill.domain.repository.EntriesSearchResult
import me.nasukhov.intrakill.domain.repository.MediaRepository
import me.nasukhov.intrakill.kmp.coroutineScope
import me.nasukhov.intrakill.storage.EntriesFilter
import me.nasukhov.intrakill.ui.root.Request
import kotlin.math.max

data class ListState(
    val filteredByTags: Set<String> = emptySet(),
    val offset: Int = 0,
    val entriesPerPage: Int = 12,
    val searchResult: EntriesSearchResult? = null,
    val isSearching: Boolean = false,
) {
    init {
        check(entriesPerPage > 0)
        check(offset >= 0)
    }
}

interface ListEntriesComponent {
    val state: Value<ListState>
    val knownTags: Value<Set<Tag>>

    fun onTagsChanged(tags: Set<String>)

    fun onOffsetChanged(offset: Int)

    fun onEntryClicked(id: String)

    fun onAddClicked()

    fun openSettings()
}

class DefaultListEntriesComponent(
    context: ComponentContext,
    filterByTags: Set<String> = emptySet(),
    private val navigate: (Request) -> Unit,
) : ListEntriesComponent,
    ComponentContext by context {
    private data class FilterParams(
        val tags: Set<String> = emptySet(),
        val offset: Int = 0,
        val limit: Int = 12,
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
                MediaRepository.updates.map { filters.value to true },
            ).collectLatest { (filter, isStorageUpdated) ->
                mutableState.update {
                    it.copy(
                        isSearching = true,
                        filteredByTags = filter.tags,
                        offset = filter.offset,
                    )
                }

                val result =
                    MediaRepository.findEntries(
                        EntriesFilter(limit = filter.limit, offset = filter.offset, tags = filter.tags),
                    )

                mutableState.update {
                    it.copy(
                        searchResult = result,
                        isSearching = false,
                    )
                }
                if (isStorageUpdated) {
                    refreshKnownTags()
                }
            }
        }

        refreshKnownTags()

        context.backHandler.register(
            BackCallback(onBack = {
                state.value.let {
                    // Unless we're already on the very first page, move backwards.
                    if (it.offset != 0) {
                        val previousPageOffset = max(it.offset - it.entriesPerPage, 0)
                        onOffsetChanged(previousPageOffset)
                    }
                }
            }),
        )
    }

    override fun onTagsChanged(tags: Set<String>) {
        filters.update { it.copy(tags = tags, offset = 0) }
    }

    override fun onOffsetChanged(offset: Int) {
        filters.update { it.copy(offset = offset) }
    }

    override fun onEntryClicked(id: String) = navigate(Request.ViewEntry(id))

    override fun onAddClicked() = navigate(Request.AddEntry)

    override fun openSettings() = navigate(Request.OpenSettings)

    private fun refreshKnownTags() {
        scope.launch {
            val allTags = MediaRepository.listTags()
            knownTags.update { allTags }
        }
    }
}

private fun <T : Any> Value<T>.asFlow(): Flow<T> =
    callbackFlow {
        // Subscribe for changes in Value. Subscriber sends that value into the flow.
        // It's, technically, intended to be used as a pipeline.
        val cancellation =
            subscribe { value ->
                trySend(value)
            }

        // 2. When the Flow is closed/cancelled, we call cancel() on the token
        awaitClose {
            cancellation.cancel()
        }
    }
