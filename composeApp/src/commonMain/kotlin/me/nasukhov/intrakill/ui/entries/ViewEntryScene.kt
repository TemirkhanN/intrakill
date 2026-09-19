package me.nasukhov.intrakill.ui.entries

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.domain.model.Attachment
import me.nasukhov.intrakill.storage.MediaKind
import me.nasukhov.intrakill.ui.clipboard.copyImageAttachmentToClipboard
import me.nasukhov.intrakill.ui.view.ConfirmationDialog
import me.nasukhov.intrakill.ui.view.Notification
import me.nasukhov.intrakill.ui.view.Notifications
import me.nasukhov.intrakill.ui.view.ReturnButton
import me.nasukhov.intrakill.ui.view.ScrollUpButton
import me.nasukhov.intrakill.ui.view.asImageBitmap

@Composable
fun ViewEntryScene(component: EntryComponent) {
    val state by component.state.subscribeAsState()

    var activeVideo by remember { mutableStateOf<Attachment?>(null) }
    var copyNotification by remember { mutableStateOf<Notification?>(null) }

    LaunchedEffect(copyNotification) {
        if (copyNotification != null) {
            delay(2_000)
            copyNotification = null
        }
    }

    if (state.isWaitingForActionConfirmation) {
        ConfirmationDialog(
            text = "Delete entirely?",
            onConfirm = component::confirmDelete,
            onCancel = component::cancelDelete,
        )
    }

    if (activeVideo != null) {
        activeVideo?.let { AttachmentView(it) }
        ReturnButton { activeVideo = null }

        return
    }

    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = state.isLoading) { isLoading ->
            val currentEntry = state.entry
            val isEditing = state.isEditing
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (currentEntry == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column {
                        Text("Entry does not exist. It was probably deleted")
                        ReturnButton(component::close)
                    }
                }
            } else {
                val listState = rememberLazyListState()
                val coroutineScope = rememberCoroutineScope()

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                ) {
                    item {
                        ReturnButton(component::close)

                        Text(currentEntry.name, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.padding(8.dp))

                        Row {
                            IconButton(onClick = component::share) {
                                Icon(Icons.Filled.Share, contentDescription = "Share entry via QR code")
                            }
                            IconButton(
                                onClick = component::toggleEditMode,
                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            ) {
                                if (isEditing) {
                                    Icon(Icons.Filled.Done, contentDescription = "Switch to view mode")
                                } else {
                                    Icon(Icons.Default.Edit, contentDescription = "Switch to edit mode")
                                }
                            }
                            if (isEditing) {
                                IconButton(
                                    onClick = component::deleteEntry,
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete entirely")
                                }
                            }
                        }
                    }

                    item {
                        if (isEditing) {
                            TagsInput(
                                knownTags = state.knownTags,
                                selectedTags = currentEntry.tags,
                                onTagsChanged = component::changeTags,
                                isEnabled = !state.isSaving,
                                maxSuggestions = 15,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                enabled = !state.isSaving,
                                onClick = component::promptAttachmentSelection,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Add attachments")
                            }

                            if (state.notifications.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Notifications(state.notifications)
                            }
                        } else {
                            TagList(
                                tags = currentEntry.tags,
                                onTagsChanged = component::onTagsChanged,
                                initiallyVisible = 5,
                            )
                        }
                    }

                    items(currentEntry.attachments) { attachment ->
                        AttachmentView(
                            attachment,
                            editMode = isEditing,
                            onMoveUp = { component.moveAttachmentUpwards(attachment) },
                            onMoveDown = { component.moveAttachmentDownwards(attachment) },
                            onDelete = { component.deleteAttachment(attachment) },
                            onCopy = {
                                runCatching { copyImageAttachmentToClipboard(attachment) }
                                    .onSuccess {
                                        copyNotification = Notification.info("Image copied to clipboard")
                                    }.onFailure {
                                        copyNotification = Notification.error("Failed to copy image to clipboard")
                                    }
                            },
                            onClick = {
                                if (attachment.mediaKind == MediaKind.VIDEO) {
                                    activeVideo = attachment
                                }
                            },
                        )
                    }

                    item {
                        Row {
                            ReturnButton(component::close)
                            Spacer(Modifier.weight(1f))
                            ScrollUpButton {
                                val topOfThePagePosition = 0
                                coroutineScope.launch {
                                    listState.animateScrollToItem(topOfThePagePosition)
                                }
                            }
                        }
                    }
                }
            }
        }

        copyNotification?.let { notification ->
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Notifications(listOf(notification))
            }
        }

        state.sharingQRCode?.let {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxSize()
                        .background(Color.White),
            ) {
                Image(
                    bitmap = it.render().getBytes().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                )
                IconButton(
                    onClick = component::stopSharing,
                    modifier = Modifier.align(Alignment.TopEnd),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop sharing")
                }
            }
        }
    }
}
