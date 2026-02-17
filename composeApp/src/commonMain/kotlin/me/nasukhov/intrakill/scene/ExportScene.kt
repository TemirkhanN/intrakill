package me.nasukhov.intrakill.scene

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import me.nasukhov.intrakill.component.ExportComponent
import me.nasukhov.intrakill.view.ReturnButton

@Composable
fun ExportScene(component: ExportComponent) {
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
            OutlinedTextField(
                value = state.password,
                onValueChange = component::setPassword,
                label = { Text("Your storage password") },
                visualTransformation = PasswordVisualTransformation(),
                enabled = !state.isEnabled,
                modifier = Modifier.fillMaxWidth()
            )

            if (!state.errors.isEmpty()) {
                Text(
                    text = state.errors.joinToString("\n"),
                    color = Color.Red,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Prevent stopping an ongoing export process
            Button(
                onClick = if (state.isEnabled) component::disable else component::enable,
                enabled = !state.isInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isInProgress) {
                    LinearProgressIndicator()
                } else {
                    Text(if (state.isEnabled) "Turn off" else "Turn on")
                }
            }

            if (!state.isInProgress) {
                ReturnButton(component::close)
            }
        }
    }
}