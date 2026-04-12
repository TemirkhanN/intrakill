package me.nasukhov.intrakill.view
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ConfirmationDialog(
    text: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) = AlertDialog(
    onDismissRequest = onCancel,
    title = {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.error,
        )
    },
    confirmButton = {
        Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Yes", color = MaterialTheme.colorScheme.primary)
        }
    },
    dismissButton = {
        TextButton(onClick = onCancel) {
            Text("No")
        }
    },
)
