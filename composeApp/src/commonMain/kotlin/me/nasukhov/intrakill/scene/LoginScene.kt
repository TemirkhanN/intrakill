package me.nasukhov.intrakill.scene

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import me.nasukhov.intrakill.component.LoginComponent

@Composable
fun LoginScene(component: LoginComponent) {
    val state by component.state.subscribeAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 400.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Internal Storage Overkill",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = component::onPasswordChanged,
                label = { Text("Master Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = !state.violations.isEmpty()
            )

            if (!state.violations.isEmpty()) {
                Text(
                    text = state.violations.joinToString("\n"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Button(
                onClick = component::onUnlockClicked,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                enabled = !state.isLoggingIn
            ) {
                if (state.isLoggingIn) {
                    CircularProgressIndicator()
                } else {
                    Text("Unlock Storage")
                }
            }

            Button(
                onClick = component::onImportClicked,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                enabled = !state.isLoggingIn
            ) {
                Text("Import Storage")
            }
            Button(
                onClick = component::onExportClicked,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                enabled = !state.isLoggingIn
            ) {
                Text("Export Storage")
            }
        }
    }
}