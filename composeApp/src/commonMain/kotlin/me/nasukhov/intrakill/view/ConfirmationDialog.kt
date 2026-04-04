package me.nasukhov.intrakill.view

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ConfirmationDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) = AlertDialog(
    onDismissRequest = onCancel,
    title = { Text("Confirm Action") },
    text = { Text("Are you sure you want to proceed?") },
    confirmButton = {
        TextButton(onClick = onConfirm) {
            Text("Yes")
        }
    },
    dismissButton = {
        TextButton(onClick = onCancel) {
            Text("No")
        }
    },
)
