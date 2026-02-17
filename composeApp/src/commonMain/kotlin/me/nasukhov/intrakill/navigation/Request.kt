package me.nasukhov.intrakill.navigation

sealed interface Request {
    data class ViewEntry(val id: String) : Request
    data object AddEntry : Request
    data object ImportRequested: Request
    data object ExportRequested: Request
    data class ListEntries(val filterByTags: Set<String> = emptySet()): Request
    data object Back: Request
}