package me.nasukhov.intrakill.ui.datasync

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import me.nasukhov.intrakill.ui.view.Notifications
import me.nasukhov.intrakill.ui.view.ReturnButton

@Composable
fun ImportScene(component: ImportComponent) {
    val state by component.state.subscribeAsState()

    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = state.ip,
            onValueChange = component::changeIp,
            label = { Text("Source IP") },
            enabled = !state.isInProgress,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.password,
            onValueChange = component::changePassword,
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = !state.isInProgress,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = component::import,
            enabled = !state.isInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isInProgress && !state.isPartialImport) {
                if (state.progress.isEmpty()) {
                    LinearProgressIndicator()
                } else {
                    LinearProgressIndicator(progress = { state.progress.percent / 100 })
                }
            } else {
                Text("Import")
            }
        }

        Button(
            onClick = component::sync,
            enabled = !state.isInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isInProgress && state.isPartialImport) {
                if (state.progress.isEmpty()) {
                    LinearProgressIndicator()
                } else {
                    Text("${state.progress.current}/${state.progress.outOf}")
                }
            } else {
                Text("Sync")
            }
        }

        Notifications(state.notifications)

        ReturnButton(component::close)
    }
}
