package me.nasukhov.intrakill.scene

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import me.nasukhov.intrakill.AppEvent
import me.nasukhov.intrakill.LocalEventEmitter

@Composable
fun TagList(
    tags: List<String>
) {
    val eventEmitter = LocalEventEmitter.current
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        tags.forEach { tag ->
            val selected = tag in selectedTags

            FilterChip(
                selected = selected,
                onClick = {
                    selectedTags =
                        if (selected) selectedTags - tag
                        else selectedTags + tag

                    eventEmitter.emit(AppEvent.TagsSelected(selectedTags.toList()))
                },
                label = {
                    Text(tag)
                }
            )
        }
    }
}
