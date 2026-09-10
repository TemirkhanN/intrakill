package me.nasukhov.intrakill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.retainedComponent
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import me.nasukhov.intrakill.storage.Filesystem
import me.nasukhov.intrakill.storage.SecureDatabase
import me.nasukhov.intrakill.ui.clipboard.AndroidClipboard
import me.nasukhov.intrakill.ui.root.DefaultRootComponent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Hide the system bars
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        // Configure behavior when user swipes to reveal the bars
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // TODO is there a better way to inject these deps?
        SecureDatabase.init(this)
        Filesystem.init(this)
        AndroidClipboard.init(this)

        val root =
            retainedComponent { context ->
                DefaultRootComponent(componentContext = context)
            }

        setContent {
            App(root)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    val lifecycle = LifecycleRegistry()
    val root = DefaultRootComponent(DefaultComponentContext(lifecycle))
    App(root)
}
