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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.AppEvent
import me.nasukhov.intrakill.LocalEventEmitter
import me.nasukhov.intrakill.storage.DbImporter

@Composable
fun ImportDbScene() {
    var ip by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isInProgress by remember { mutableStateOf(false) }

    val eventEmitter = LocalEventEmitter.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text("Desktop IP") },
            enabled = !isInProgress,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = !isInProgress,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (isInProgress) return@Button

                isInProgress = true
                error = null

                scope.launch {
                    val success = try {
                        DbImporter.importDatabase(
                            ip = ip.trim(),
                            password = password
                        )
                    } catch (e: Exception) {
                        error = e.message ?: "Error on during importing"
                        false
                    }

                    isInProgress = false

                    if (!success) {
                        error = "Failed to import database"
                    } else {
                        eventEmitter.emit(AppEvent.Logout)
                    }
                }
            },
            enabled = !isInProgress,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isInProgress) "Importing…" else "Import Database")
        }

        Button(
            onClick = { eventEmitter.emit(AppEvent.Logout) },
            enabled = !isInProgress,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }

        error?.let {
            Text(
                text = it,
                color = Color.Red,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

