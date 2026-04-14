package me.nasukhov.intrakill.ui.root

import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable
    data object Login : Route

    @Serializable
    data class List(
        val filterByTags: Set<String> = emptySet(),
        val limit: Int = 12,
        val offset: Int = 0,
    ) : Route

    @Serializable
    data class View(
        val entryId: String,
    ) : Route

    @Serializable
    data object AddEntry : Route

    @Serializable
    data object OpenSettings : Route

    @Serializable
    data object Import : Route

    data object Export : Route
}
