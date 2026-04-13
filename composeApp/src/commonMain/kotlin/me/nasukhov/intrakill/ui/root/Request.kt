package me.nasukhov.intrakill.ui.root

sealed interface Request {
    data class ViewEntry(
        val id: String,
    ) : Request

    data object AddEntry : Request

    data object OpenSettings : Request

    data object ImportRequested : Request

    data object ExportRequested : Request

    data class ListEntries(
        val filterByTags: Set<String> = emptySet(),
    ) : Request

    data object Back : Request
}
