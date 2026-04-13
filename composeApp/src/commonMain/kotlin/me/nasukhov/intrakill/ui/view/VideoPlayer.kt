package me.nasukhov.intrakill.ui.view

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import chaintech.videoplayer.host.MediaPlayerHost
import chaintech.videoplayer.model.VideoPlayerConfig
import chaintech.videoplayer.ui.video.VideoPlayerComposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.nasukhov.intrakill.domain.model.Attachment
import java.io.File

@Composable
fun VideoPlayer(attachment: Attachment) {
    var isLoaded by remember { mutableStateOf(false) }

    var isVerticalVideo by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .aspectRatio(if (!isVerticalVideo) 16f / 9 else 9f / 16),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = isLoaded,
        ) { isReady ->
            if (isReady) {
                RealPlayer(attachment)
            } else {
                Image(
                    bitmap = attachment.preview.asImageBitmap(),
                    contentDescription = "Video Preview",
                    modifier =
                        Modifier
                            .clickable { isLoaded = true }
                            .border(1.dp, Color.Cyan)
                            .fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                Button(onClick = { isVerticalVideo = !isVerticalVideo }) {
                    Text(if (isVerticalVideo) "horizontal" else "vertical")
                }
            }
        }
    }
}

@Composable
private fun RealPlayer(attachment: Attachment) {
    var error by remember { mutableStateOf("") }
    var tempFile by remember { mutableStateOf<File?>(null) }
    var isWritingFile by remember { mutableStateOf(true) }

    LaunchedEffect(attachment.content) {
        withContext(Dispatchers.IO) {
            try {
                val file =
                    File.createTempFile("intrakill_vid_", ".mp4").apply {
                        outputStream().use { output ->
                            attachment.content.use { it.copyTo(output) }
                        }
                        deleteOnExit()
                    }
                tempFile = file
            } catch (e: Exception) {
                error = e.message ?: "Failed to write video to disk"
            } finally {
                isWritingFile = false
            }
        }
    }
    when {
        isWritingFile -> {
            CircularProgressIndicator()
        }

        error.isNotEmpty() || tempFile == null -> {
            PageError(error)
        }

        else -> {
            val file = tempFile!!
            val playerHost =
                remember(file.absolutePath) {
                    MediaPlayerHost(
                        mediaUrl = file.absolutePath,
                        isMuted = false,
                        autoPlay = true,
                        isLooping = true,
                    )
                }
            VideoPlayerComposable(
                modifier = Modifier.fillMaxSize(),
                playerHost = playerHost,
                playerConfig =
                    VideoPlayerConfig(
                        isPauseResumeEnabled = true,
                        isSeekBarVisible = true,
                        isDurationVisible = true,
                        isAutoHideControlEnabled = true,
                        isGestureVolumeControlEnabled = false,
                        controlHideIntervalSeconds = 5,
                    ),
            )
            DisposableEffect(playerHost) {
                onDispose {
                    playerHost.pause()
                    file.delete()
                }
            }
        }
    }
}
