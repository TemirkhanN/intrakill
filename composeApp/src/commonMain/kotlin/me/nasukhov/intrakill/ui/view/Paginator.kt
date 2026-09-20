package me.nasukhov.intrakill.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
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
    val hasPreviousPage = offset > 0
    val hasNextPage = offset + maxEntriesPerPage < total

    var lastPageOffset = (total / maxEntriesPerPage) * maxEntriesPerPage
    if (lastPageOffset == total) {
        lastPageOffset -= maxEntriesPerPage
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        if (hasPreviousPage) {
            IconButton(
                onClick = { onOffsetChange(0) },
            ) {
                Icon(Icons.Rounded.KeyboardDoubleArrowLeft, contentDescription = "To the beginning")
            }

            IconButton(
                onClick = { onOffsetChange((offset - maxEntriesPerPage).coerceAtLeast(0)) },
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = "Previous results")
            }
        }

        if (hasNextPage) {
            IconButton(
                onClick = { onOffsetChange((offset + maxEntriesPerPage).coerceAtMost(total)) },
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, contentDescription = "Next results")
            }

            IconButton(
                onClick = { onOffsetChange(lastPageOffset) },
            ) {
                Icon(Icons.Rounded.KeyboardDoubleArrowRight, contentDescription = "To the end")
            }
        }
    }
}
