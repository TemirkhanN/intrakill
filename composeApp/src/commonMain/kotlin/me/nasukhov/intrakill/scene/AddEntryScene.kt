package me.nasukhov.intrakill.scene

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import me.nasukhov.intrakill.component.AttachmentView
import me.nasukhov.intrakill.component.TagsInput
import me.nasukhov.intrakill.navigation.AddEntryComponent

@Composable
fun AddEntryScene(component: AddEntryComponent) {
    val state by component.state.subscribeAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TextButton(onClick = component::cancel) {
            Text("← Cancel")
        }

        Button(
            enabled = !state.isSaving,
            onClick = component::promptAttachmentSelection,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select photos / videos")
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(0.5f)
        ) {
            itemsIndexed(state.attachments) { index, media ->
                AttachmentView(
                    attachment = media,
                    editMode = true,
                    onDelete = { component.removeAttachment(index) },
                    onMoveUp = { component.moveAttachmentUpwards(index) },
                    onMoveDown = { component.moveAttachmentDownwards(index) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.name,
            onValueChange = component::changeName,
            label = { Text("Name or description") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSaving
        )

        TagsInput(
            allTags = state.knownTags,
            onTagsChanged = component::changeTags,
            isEnabled = !state.isSaving
        )

        Spacer(Modifier.height(12.dp))

        Button(
            enabled = !state.isSaving,
            onClick = component::save,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isSaving) {
                CircularProgressIndicator()
            } else {
                Text("Save")
            }
        }

        if (state.violations.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(state.violations.joinToString("\n"), color = Color.Red)
        }
    }
}
