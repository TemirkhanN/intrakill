package me.nasukhov.intrakill

import androidx.compose.runtime.*
import me.nasukhov.intrakill.scene.AddContentScene
import me.nasukhov.intrakill.scene.ContentScene
import me.nasukhov.intrakill.scene.LoginScene

@Composable
fun App() {
    val appState = remember { AppState() }

    when (appState.currentScene) {
        Scene.Login -> LoginScene(
            onLoginSuccess = appState::onLoginSuccess
            //onLoginSuccess = appState::addNewEntry
        )
        Scene.Content -> ContentScene()
        Scene.NewEntry -> AddContentScene(
            onSuccess = appState::toContentScene
        )
    }
}
