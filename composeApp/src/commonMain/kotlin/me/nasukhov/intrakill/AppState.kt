package me.nasukhov.intrakill

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed interface Scene {
    data object Login : Scene
    data object ImportDb : Scene
    data class Content(val filteredByTags: Set<String> = emptySet()) : Scene
    data object NewEntry : Scene
    data class ViewEntry(val entryId: String) : Scene
}

class AppState {
    private val backStack = ArrayDeque<Scene>()

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
                push(Scene.NewEntry)
            }

            is AppEvent.ViewEntry -> {
                push(Scene.ViewEntry(event.entryId))
            }

            is AppEvent.TagsSelected -> {
                currentScene = Scene.Content(event.tags)
            }
            is AppEvent.ImportRequested -> {
                currentScene = Scene.ImportDb
            }

            is AppEvent.Back -> {
                pop()
            }
        }
    }

    private fun push(scene: Scene) {
        backStack.addLast(currentScene)
        currentScene = scene
    }

    private fun pop() {
        if (backStack.isNotEmpty()) {
            currentScene = backStack.removeLast()
        }
    }
}
