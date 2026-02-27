package me.nasukhov.intrakill

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import chaintech.videoplayer.util.LocalWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import me.nasukhov.intrakill.navigation.DefaultRootComponent

fun main() = application {
    val lifecycle = LifecycleRegistry()
    val root = DefaultRootComponent(DefaultComponentContext(lifecycle))

    val windowState = rememberWindowState(width = 1280.dp, height = 720.dp, position = WindowPosition.Aligned(Alignment.Center))
    CompositionLocalProvider(LocalWindowState provides windowState) {
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "intrakill",
        ) {
            App(root)
        }
    }
}