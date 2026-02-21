package me.nasukhov.intrakill.scene

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import me.nasukhov.intrakill.component.ImportComponent
import me.nasukhov.intrakill.view.ReturnButton

@Composable
fun ImportScene(component: ImportComponent) {
    val state by component.state.subscribeAsState()

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        OutlinedTextField(
            value = state.ip,
            onValueChange = component::changeIp,
            label = { Text("Source IP") },
            enabled = !state.isInProgress,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.password,
            onValueChange = component::changePassword,
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = !state.isInProgress,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = component::import,
            enabled = !state.isInProgress,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isInProgress) {
                LinearProgressIndicator()
            } else {
                Text("Import")
            }
        }

        ReturnButton(component::close)

        if (!state.violations.isEmpty()) {
            Text(
                text = state.violations.joinToString("\n"),
                color = Color.Red,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

