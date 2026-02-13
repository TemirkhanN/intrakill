package me.nasukhov.intrakill

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    object ImportRequested : AppEvent
}

@Composable
fun IntrakillTheme(content: @Composable () -> Unit) {
    val nightMode = darkColorScheme(
        primary = Color(0xFF00FFC2), // Electric Emerald - feels like "Access Granted"
        onPrimary = Color(0xFF00382B),
        secondary = Color(0xFF00D1FF), // Neon Blue - for secondary actions
        background = Color(0xFF0A0C10), // Not pure black, but a "Rich Navy Black"
        surface = Color(0xFF161B22),    // Slightly lighter for inputs/cards
        onBackground = Color(0xFFF0F6FC), // Off-white to reduce eye strain
        onSurface = Color(0xFFF0F6FC),
        error = Color(0xFFFF453A)       // Classic warning red
    )

    MaterialTheme(
        colorScheme = nightMode
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                content()
            }
        }
    }
}

@Composable
fun App() {
    val appState = remember { AppState() }

    IntrakillTheme {
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
