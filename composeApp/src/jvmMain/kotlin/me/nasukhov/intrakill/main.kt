package me.nasukhov.intrakill

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import me.nasukhov.intrakill.storage.DbExporter


fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "intrakill",
    ) {
        DbExporter.start() // TODO runs even before logging in
        App()
    }
}