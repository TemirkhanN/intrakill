package me.nasukhov.intrakill

import androidx.compose.runtime.*
import me.nasukhov.intrakill.scene.AddContentScene
import me.nasukhov.intrakill.scene.ListEntriesScene
import me.nasukhov.intrakill.scene.LoginScene
import me.nasukhov.intrakill.scene.ViewEntryScene

@Composable
fun App() {
    val appState = remember { AppState() }

    when (val currentScene = appState.currentScene) {
        Scene.Login -> LoginScene(
            onLoginSuccess = appState::login
        )
        Scene.Content -> ListEntriesScene(
            onEntryClick = { entryId ->
                appState.viewEntry(entryId)
            },
            onNewEntryButtonClick = {
                appState.addNewEntry()
            }
        )
        Scene.NewEntry -> AddContentScene(
            onSuccess = appState::listEntries
        )
        is Scene.ViewEntry -> ViewEntryScene(currentScene.entryId)
    }
}
