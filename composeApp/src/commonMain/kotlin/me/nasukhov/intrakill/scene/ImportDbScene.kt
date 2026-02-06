package me.nasukhov.intrakill.scene

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.AppEvent
import me.nasukhov.intrakill.LocalEventEmitter
import me.nasukhov.intrakill.storage.DbImporter

@Composable
fun ImportDbScene() {
    var ip by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val eventEmitter = LocalEventEmitter.current

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text("Desktop IP") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("password") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = {
            error = null
            CoroutineScope(Dispatchers.Main).launch {
                val success = DbImporter.importDatabase(ip=ip, password=password)
                if (!success) {
                    error = "Failed to download database"
                    return@launch
                }

                eventEmitter.emit(AppEvent.Logout)

            }
        }) {
            Text("Import Database")
        }

        Button(onClick = {
            eventEmitter.emit(AppEvent.Logout)
        }) {
            Text("Back")
        }

        error?.let { Text(it, color = Color.Red) }
    }
}
