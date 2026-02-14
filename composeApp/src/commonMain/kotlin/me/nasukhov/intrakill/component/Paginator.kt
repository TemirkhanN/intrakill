package me.nasukhov.intrakill.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        horizontalArrangement = Arrangement.Center
    ) {
        TextButton(
            enabled = offset > 0,
            onClick = { onOffsetChange((offset - maxEntriesPerPage).coerceAtLeast(0)) }
        ) {
            Text("<")
        }

        TextButton(
            enabled = offset + maxEntriesPerPage < total,
            onClick = { onOffsetChange((offset + maxEntriesPerPage).coerceAtMost(total)) }
        ) {
            Text(">")
        }
    }
}