package me.nasukhov.intrakill.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import me.nasukhov.intrakill.content.Tag
import kotlin.collections.plus

@Composable
fun TagsInput(
    knownTags: Set<Tag>,
    selectedTags: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    onTagsChanged: (Set<String>) -> Unit,
    isEnabled: Boolean = true,
    maxSuggestions: Int = 6,
    tagsDelimiter: String = ",",
    maxTagLength: Int = Tag.MAX_LENGTH,
) {
    var finalizedTags by remember { mutableStateOf(selectedTags) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    val currentText = textFieldValue.text
    val currentPrefix = currentText.substringAfterLast(tagsDelimiter).trim().lowercase()

    val suggestions = remember(currentPrefix, finalizedTags, knownTags) {
        knownTags.asSequence()
            .map { it.name.lowercase() }
            .filter { it !in finalizedTags && it.startsWith(currentPrefix) && it != currentPrefix }
            .sortedByDescending { tag -> knownTags.find { it.name.lowercase() == tag }?.frequency ?: 0 }
            .take(maxSuggestions)
            .toList()
    }

    if (isEnabled && suggestions.isNotEmpty()) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                suggestions.forEach { tag ->
                    key(tag) {
                        FilterChip(
                            modifier = Modifier.padding(4.dp),
                            selected = false,
                            label = { Text("+ $tag") },
                            onClick = {
                                val newSet = finalizedTags + tag
                                finalizedTags = newSet

                                onTagsChanged(newSet)

                                // TODO make configurable? Currently, it seems to be better if suggestions stay
                                //textFieldValue = TextFieldValue("", TextRange(0))
                            },
                        )
                    }
                }
            }
        }
    }

    Column(modifier) {
        OutlinedTextField(
            label = { Text("Tags (separated by $tagsDelimiter)") },
            value = textFieldValue,
            enabled = isEnabled,
            onValueChange = { newValue ->
                val newText = newValue.text
                // If value is bigger than allowed length including delimiter, no point in going further
                if (newText.length > (maxTagLength + tagsDelimiter.length)) {
                    return@OutlinedTextField
                }

                // If the user typed a delimiter, finalize the current word
                if (newText.endsWith(tagsDelimiter)) {
                    val word = newText.substringBeforeLast(tagsDelimiter).trim().lowercase()
                    if (word.isNotEmpty() && word.length <= maxTagLength) {
                        finalizedTags = finalizedTags + word
                        onTagsChanged(finalizedTags)
                        textFieldValue = TextFieldValue("", selection = TextRange(0))
                    }
                } else {
                    // Otherwise, just update the typing state
                    textFieldValue = newValue
                }
            },
            modifier = Modifier
                .fillMaxWidth()
        )

        // Display finalized tags as "chips" or a simple list so user sees what's locked in
        if (finalizedTags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                finalizedTags.forEach { tag ->
                    Text(
                        "#$tag",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            finalizedTags = finalizedTags - tag
                            onTagsChanged(finalizedTags)
                        }
                    )
                }
            }
        }
    }
}