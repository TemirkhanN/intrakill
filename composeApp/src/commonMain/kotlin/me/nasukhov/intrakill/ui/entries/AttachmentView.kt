package me.nasukhov.intrakill.ui.entries

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import me.nasukhov.intrakill.domain.model.Attachment
import me.nasukhov.intrakill.storage.MediaKind
import me.nasukhov.intrakill.ui.view.VideoPlayer

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttachmentView(
    attachment: Attachment,
    editMode: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onDelete: () -> Unit = {},
    onCopy: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    when (attachment.mediaKind) {
        MediaKind.IMAGE,
        MediaKind.GIF,
        ->
            ImageView(
                attachment = attachment,
                onLongClick = onCopy,
            ) {
                if (editMode) {
                    AttachmentEditor(onMoveUp, onMoveDown, onDelete)
                }
            }

        MediaKind.VIDEO -> VideoPlayer(attachment, onClick)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageView(
    attachment: Attachment,
    onLongClick: (() -> Unit)?,
    controlBar: @Composable BoxScope.() -> Unit,
) {
    check(attachment.mediaKind == MediaKind.IMAGE || attachment.mediaKind == MediaKind.GIF)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = onLongClick != null,
                    onClick = {},
                    onLongClick = onLongClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (attachment.mediaKind == MediaKind.IMAGE) {
            Image(
                bitmap = attachment.imageBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
        } else {
            AsyncImage(
                model = attachment.content.readBytes(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
        }

        controlBar()
    }
}

@Composable
private fun BoxScope.AttachmentEditor(
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ControlToolButton(text = "↑", onClick = onMoveUp)
        ControlToolButton(text = "↓", onClick = onMoveDown)
        ControlToolButton(text = "✕", color = Color.Red, onClick = onDelete)
    }
}

@Composable
private fun ControlToolButton(
    text: String,
    color: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(24.dp)
                .border(1.dp, Color.Gray.copy(alpha = 0.5f))
                .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
