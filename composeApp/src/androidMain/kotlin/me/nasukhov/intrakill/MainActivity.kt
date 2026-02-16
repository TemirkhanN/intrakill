package me.nasukhov.intrakill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.retainedComponent
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import me.nasukhov.intrakill.navigation.DefaultRootComponent
import me.nasukhov.intrakill.storage.DbFileResolver
import me.nasukhov.intrakill.storage.SecureDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // TODO is there a better way to inject these deps?
        SecureDatabase.init(this)
        DbFileResolver.init(this)

        val root = retainedComponent{ context ->
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