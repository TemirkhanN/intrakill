package me.nasukhov.intrakill.view

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
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
    tagsDelimiter: String = ","
) {
    var finalizedTags by remember { mutableStateOf(selectedTags) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

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

    Column(modifier) {
        OutlinedTextField(
            label = { Text("Tags (separated by $tagsDelimiter)") },
            value = textFieldValue,
            enabled = isEnabled,
            onValueChange = { newValue ->
                val newText = newValue.text

                // If the user typed a delimiter, finalize the current word
                if (newText.endsWith(tagsDelimiter)) {
                    val word = newText.substringBeforeLast(tagsDelimiter).trim().lowercase()
                    if (word.isNotEmpty()) {
                        finalizedTags = finalizedTags + word
                        onTagsChanged(finalizedTags)
                        // Reset text field but keep the "session"
                        textFieldValue = TextFieldValue("", selection = TextRange(0))
                    }
                } else {
                    // Otherwise, just update the typing state
                    textFieldValue = newValue

                    // Sync parent with finalized + current part (optional, depending on UX)
                    val currentParts = newText.split(tagsDelimiter)
                        .map { it.trim().lowercase() }
                        .filter { it.isNotEmpty() }
                    onTagsChanged(finalizedTags + currentParts)
                }
            },
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )

        // Display finalized tags as "chips" or a simple list so user sees what's locked in
        if (finalizedTags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
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

        if (isEnabled && isFocused && suggestions.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            ) {
                suggestions.forEach { tag ->
                    key(tag) {
                        Text(
                            text = tag,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(tag) {
                                    awaitEachGesture {
                                        awaitFirstDown()

                                        // 1. Update local finalized set
                                        val newSet = finalizedTags + tag
                                        finalizedTags = newSet

                                        // 2. Clear the text field for the next tag
                                        textFieldValue = TextFieldValue("", TextRange(0))

                                        // 3. Notify parent
                                        onTagsChanged(newSet)

                                        focusRequester.requestFocus()
                                    }
                                }
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}