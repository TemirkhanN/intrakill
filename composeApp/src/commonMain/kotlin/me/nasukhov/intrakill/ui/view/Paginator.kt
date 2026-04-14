package me.nasukhov.intrakill.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Paginator(
    offset: Int,
    maxEntriesPerPage: Int,
    total: Int,
    onOffsetChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        IconButton(
            enabled = offset > 0,
            onClick = { onOffsetChange((offset - maxEntriesPerPage).coerceAtLeast(0)) },
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous results")
        }

        IconButton(
            enabled = offset + maxEntriesPerPage < total,
            onClick = { onOffsetChange((offset + maxEntriesPerPage).coerceAtMost(total)) },
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next results")
        }
    }
}
