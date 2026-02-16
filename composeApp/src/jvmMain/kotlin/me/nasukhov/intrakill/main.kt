package me.nasukhov.intrakill

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import me.nasukhov.intrakill.navigation.DefaultRootComponent

fun main() = application {
    val lifecycle = LifecycleRegistry()
    val root = DefaultRootComponent(DefaultComponentContext(lifecycle))

    Window(
        onCloseRequest = ::exitApplication,
        title = "intrakill",
    ) {
        App(root)
    }
}