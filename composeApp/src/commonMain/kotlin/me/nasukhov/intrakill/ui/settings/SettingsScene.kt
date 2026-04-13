package me.nasukhov.intrakill.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import me.nasukhov.intrakill.ui.view.Notifications
import me.nasukhov.intrakill.ui.view.ReturnButton

@Composable
fun SettingsScene(component: SettingsComponent) {
    val state by component.state.subscribeAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(24.dp)
                    .widthIn(max = 400.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                label = { Text("New password") },
                value = state.newPassword,
                onValueChange = component::changePassword,
                visualTransformation = PasswordVisualTransformation(),
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )

            Notifications(state.notifications)

            Button(
                onClick = component::save,
                enabled = !state.isSaving,
            ) {
                if (!state.isSaving) Text("Save") else CircularProgressIndicator()
            }

            ReturnButton(component::close)
        }
    }
}
