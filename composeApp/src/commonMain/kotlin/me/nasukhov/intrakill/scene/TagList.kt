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
) {
    var expanded by remember { mutableStateOf(false) }

    val visibleTags = if (expanded) tags else tags.take(initiallyVisible)

    Column(modifier = Modifier.fillMaxWidth()) {

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            visibleTags.forEach { tag ->
                val selected = tag in selectedTags

                FilterChip(
                    selected = selected,
                    onClick = {
                        val updated =
                            if (selected) selectedTags - tag
                            else selectedTags + tag

                        onTagsChanged(updated)
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
    }
}
