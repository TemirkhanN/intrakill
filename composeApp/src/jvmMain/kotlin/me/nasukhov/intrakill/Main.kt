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
import me.nasukhov.intrakill.storage.FilePicker
import me.nasukhov.intrakill.storage.FilePickerJvm
import me.nasukhov.intrakill.storage.Filesystem
import me.nasukhov.intrakill.storage.FilesystemJvm
import me.nasukhov.intrakill.storage.SecureDatabase
import me.nasukhov.intrakill.storage.SecureDatabaseJvm
import me.nasukhov.intrakill.ui.root.DefaultRootComponent
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

@Suppress("ktlint:standard:function-naming")
fun main() =
    application {
        val jvmSpecificDefinitions =
            module {
                singleOf<Filesystem>(::FilesystemJvm)
                singleOf<FilePicker>(::FilePickerJvm)
                single<SecureDatabase> { SecureDatabaseJvm(filesystem = get()) }
            }
        Ioc(jvmSpecificDefinitions)

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
