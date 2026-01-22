package me.nasukhov.intrakill

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed interface Scene {
    data object Login : Scene
    data class Content(val filteredByTags: List<String> = emptyList()) : Scene
    data object NewEntry : Scene
    data class ViewEntry(val entryId: String) : Scene
}

class AppState {
    var currentScene by mutableStateOf<Scene>(Scene.Login)
        private set

    fun handle(event: AppEvent) {
        when (event) {
            is AppEvent.Logout -> {
                currentScene = Scene.Login
            }

            is AppEvent.LoginSucceeded -> {
                currentScene = Scene.Content()
            }

            is AppEvent.AddNewEntry -> {
                currentScene = Scene.NewEntry
            }

            is AppEvent.ViewEntry -> {
                currentScene = Scene.ViewEntry(event.entryId)
            }

            is AppEvent.TagsSelected -> {
                currentScene = Scene.Content(event.tags)
            }

            is AppEvent.Back -> {}
        }
    }
}
