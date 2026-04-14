package me.nasukhov.intrakill.ui.view

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReturnButton(handler: () -> Unit) =
    TextButton(onClick = handler) {
        Text("← Back")
    }

@Composable
fun ScrollUpButton(handler: () -> Unit) =
    IconButton(
        onClick = handler,
        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Icon(
            Icons.Rounded.KeyboardArrowUp,
            contentDescription = "Scroll up",
            modifier = Modifier.size(32.dp),
        )
    }
