package me.nasukhov.intrakill

import androidx.compose.runtime.*
import me.nasukhov.intrakill.scene.AddContentScene
import me.nasukhov.intrakill.scene.ListEntriesScene
import me.nasukhov.intrakill.scene.LoginScene
import me.nasukhov.intrakill.scene.ViewEntryScene


sealed interface AppEvent {
    object LoginSucceeded : AppEvent
    object Logout : AppEvent
    data class ViewEntry(val entryId: String) : AppEvent
    object AddNewEntry : AppEvent
    object Back : AppEvent
    data class TagsSelected(val tags: List<String>) : AppEvent
}

@Composable
fun App() {
    val appState = remember { AppState() }

    CompositionLocalProvider(
        LocalEventEmitter provides EventEmitter { event ->
            appState.handle(event)
        }
    ) {
        when (val currentScene = appState.currentScene) {
            Scene.Login -> LoginScene()
            is Scene.Content -> ListEntriesScene(currentScene.filteredByTags)
            Scene.NewEntry -> AddContentScene()
            is Scene.ViewEntry -> ViewEntryScene(currentScene.entryId)
        }
    }
}
