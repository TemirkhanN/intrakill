package me.nasukhov.intrakill.scene

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TagList(
    tags: Set<String>,
    selectedTags: Set<String> = emptySet(),
    onTagsChanged: (Set<String>) -> Unit,
    initiallyVisible: Int = 15,
    expandedInitially: Boolean = false,
) {
    var selectedTags by remember { mutableStateOf(selectedTags) }
    var expanded by remember { mutableStateOf(expandedInitially) }

    val visibleTags = if (expanded) tags else tags.take(initiallyVisible)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                visibleTags.forEach { tag ->
                    val selected = tag in selectedTags

                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedTags = if (selected) {
                                selectedTags - tag
                            } else {
                                selectedTags + tag
                            }
                        },
                        label = { Text(tag) }
                    )
                }
            }

            if (tags.size > initiallyVisible) {
                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = { expanded = !expanded }
                ) {
                    Text(if (expanded) "less tags" else "more tags")
                }
            }

            TextButton(onClick = {onTagsChanged(selectedTags)}) {
                Text("Find")
            }
        }
    }
}
