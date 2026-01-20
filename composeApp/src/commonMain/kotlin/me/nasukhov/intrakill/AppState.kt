package me.nasukhov.intrakill

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed interface Scene {
    data object Login : Scene
    data object Content : Scene
    data object NewEntry : Scene
    data class ViewEntry(val entryId: String) : Scene
}

class AppState {
    var currentScene by mutableStateOf<Scene>(Scene.Login)
        private set

    fun login() {
        currentScene = Scene.Content
    }

    fun logout() {
        currentScene = Scene.Login
    }

    fun addNewEntry() {
        currentScene = Scene.NewEntry
    }

    fun listEntries() {
        currentScene = Scene.Content
    }

    fun viewEntry(id: String) {
        currentScene = Scene.ViewEntry(entryId = id)
    }
}
