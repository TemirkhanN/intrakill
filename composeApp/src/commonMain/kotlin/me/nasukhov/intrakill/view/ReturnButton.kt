package me.nasukhov.intrakill.view

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ReturnButton(handler: () -> Unit) = TextButton(onClick = handler) {
    Text("← Back")
}