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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.domain.model.Settings
import me.nasukhov.intrakill.domain.model.Tag
import me.nasukhov.intrakill.domain.repository.EntriesSearchResult
import me.nasukhov.intrakill.domain.repository.MediaRepository
import me.nasukhov.intrakill.kmp.coroutineScope
import me.nasukhov.intrakill.storage.EntriesFilter
import me.nasukhov.intrakill.ui.root.Request
import me.nasukhov.intrakill.ui.root.Route
import kotlin.math.max

data class ListState(
    val filter: EntriesFilter = EntriesFilter(limit = 12, offset = 0),
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

    fun openSettings()

    fun openTagsScene()
}

class DefaultListEntriesComponent(
    context: ComponentContext,
    config: Route.List, // TODO feels odd
    private val navigate: (Request) -> Unit,
) : ListEntriesComponent,
    ComponentContext by context {
    override val knownTags = MutableValue<Set<Tag>>(emptySet())

    private val mutableState = MutableValue(ListState())
    override val state: Value<ListState> = mutableState

    private val scope = instanceKeeper.coroutineScope()

    init {
        scope.launch {
            refreshKnownTags()
            val filter =
                EntriesFilter(
                    offset = config.offset,
                    tags = config.filterByTags,
                    limit = Settings.entriesPerPage.value,
                )
            applyFilter(filter)

            // Everytime something changes in
            // the storage (add/remove entries)
            // OR the config (change amount of per page items or other UI features)
            // we have to refresh the search result
            merge(
                MediaRepository.updates,
                Settings.updates,
            ).collectLatest {
                refreshKnownTags()
                val newFilter = state.value.filter.copy(limit = Settings.entriesPerPage.value)
                applyFilter(newFilter)
            }
        }

        context.backHandler.register(
            BackCallback(onBack = {
                state.value.filter.let {
                    // Unless we're already on the very first page, move backwards.
                    if (it.offset != 0) {
                        val previousPageOffset = max(it.offset - it.limit, 0)
                        onOffsetChanged(previousPageOffset)
                    }
                }
            }),
        )
    }

    override fun onTagsChanged(tags: Set<String>) {
        val newFilter = state.value.filter.copy(tags = tags, offset = 0)

        scope.launch { applyFilter(newFilter) }
    }

    override fun onOffsetChanged(offset: Int) {
        val newFilter = state.value.filter.copy(offset = offset)

        scope.launch { applyFilter(newFilter) }
    }

    override fun onEntryClicked(id: String) = navigate(Request.ViewEntry(id))

    override fun onAddClicked() = navigate(Request.AddEntry)

    override fun openSettings() = navigate(Request.OpenSettings)

    override fun openTagsScene() = navigate(Request.OpenTags)

    private suspend fun refreshKnownTags() {
        val allTags = MediaRepository.listTags()
        knownTags.update { allTags }
    }

    private suspend fun applyFilter(newFilter: EntriesFilter) {
        val availableTags = knownTags.value.map { it.name }
        val selectedAvailableTags = newFilter.tags.filter { availableTags.contains(it) }.toSet()
        val newFilter = newFilter.copy(tags = selectedAvailableTags)

        mutableState.update {
            it.copy(
                filter = newFilter,
                isSearching = true,
            )
        }

        val result = MediaRepository.findEntries(newFilter)

        mutableState.update {
            it.copy(
                searchResult = result,
                isSearching = false,
            )
        }
    }
}

// Keep for reference. I find this to be useful under particular circumstances between Flow and Decompose
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
