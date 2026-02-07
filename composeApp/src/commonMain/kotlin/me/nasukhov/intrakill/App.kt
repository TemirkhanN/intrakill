package me.nasukhov.intrakill

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import me.nasukhov.intrakill.scene.AddContentScene
import me.nasukhov.intrakill.scene.ImportDbScene
import me.nasukhov.intrakill.scene.ListEntriesScene
import me.nasukhov.intrakill.scene.LoginScene
import me.nasukhov.intrakill.scene.ViewEntryScene
import me.nasukhov.intrakill.storage.ProvideFilePicker

sealed interface AppEvent {
    object LoginSucceeded : AppEvent
    object Logout : AppEvent
    data class ViewEntry(val entryId: String) : AppEvent
    object AddNewEntry : AppEvent
    object Back : AppEvent
    data class TagsSelected(val tags: Set<String>) : AppEvent
    object ImportRequested: AppEvent
}

@Composable
fun App() {
    val appState = remember { AppState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        ProvideFilePicker {
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
                    Scene.ImportDb -> ImportDbScene()
                }
            }
        }
    }
}
