package me.nasukhov.intrakill.scene

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.nasukhov.intrakill.AppEvent
import me.nasukhov.intrakill.LocalEventEmitter
import androidx.compose.foundation.layout.FlowRow

@Composable
fun TagList(
    tags: List<String>,
    initiallyVisible: Int = 15,
) {
    val eventEmitter = LocalEventEmitter.current
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expanded by remember { mutableStateOf(false) }

    val visibleTags = if (expanded) tags else tags.take(initiallyVisible)

    Column(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            visibleTags.forEach { tag ->
                val selected = tag in selectedTags

                FilterChip(
                    selected = selected,
                    onClick = {
                        selectedTags =
                            if (selected) selectedTags - tag
                            else selectedTags + tag

                        eventEmitter.emit(AppEvent.TagsSelected(selectedTags.toList()))
                    },
                    label = { Text(tag) }
                )
            }
        }

        if (tags.size > initiallyVisible) {
            ElevatedFilterChip(
                onClick = { expanded = !expanded },
                selected = false,
                elevation = FilterChipDefaults.elevatedFilterChipElevation(elevation = 8.dp),
                label = { Text(if (expanded) "less tags" else "more tags") },
            )
        }
    }
}
