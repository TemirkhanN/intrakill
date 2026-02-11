package me.nasukhov.intrakill.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import me.nasukhov.intrakill.content.Attachment
import me.nasukhov.intrakill.scene.asImageBitmap
import me.nasukhov.intrakill.storage.MediaKind

@Composable
fun AttachmentView(attachment: Attachment) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        when (attachment.mediaKind) {
            MediaKind.IMAGE -> {
                Image(
                    bitmap = attachment.content.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            }
            MediaKind.VIDEO -> {
                Box(modifier = Modifier.aspectRatio(1f)) {
                    Image(
                        bitmap = attachment.preview.asImageBitmap(),
                        contentDescription = "Video Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            else -> {
                Box(
                    modifier = Modifier.aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No preview", color = Color.Gray)
                }
            }
        }
    }
}