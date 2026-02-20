package me.nasukhov.intrakill.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import me.nasukhov.intrakill.content.Attachment
import chaintech.videoplayer.host.MediaPlayerHost
import chaintech.videoplayer.ui.video.VideoPlayerComposable
import chaintech.videoplayer.model.VideoPlayerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.nasukhov.intrakill.scene.asImageBitmap
import java.io.File

@Composable
fun VideoPlayer(attachment: Attachment) {
    var isLoaded by remember { mutableStateOf(false) }

    if (isLoaded) {
        RealPlayer(attachment)
    } else {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                bitmap = attachment.preview.asImageBitmap(),
                contentDescription = "Video Preview",
                modifier = Modifier
                    .clickable { isLoaded = true },
                contentScale = ContentScale.Crop,
            )
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
                val file = File.createTempFile("intrakill_vid_", ".mp4").apply {
                    writeBytes(attachment.content)
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f),
        contentAlignment = Alignment.Center
    ) {
        when {
            isWritingFile -> {
                CircularProgressIndicator()
            }

            error.isNotEmpty() || tempFile == null -> {
                PageError(error)
            }

            else -> {
                val file = tempFile!!
                val playerHost = remember(file.absolutePath) {
                    MediaPlayerHost(
                        mediaUrl = file.absolutePath,
                        isMuted = false,
                        autoPlay = true,
                        isLooping = false,
                    )
                }
                VideoPlayerComposable(
                    modifier = Modifier.fillMaxSize(),
                    playerHost = playerHost,
                    playerConfig = VideoPlayerConfig(
                        isPauseResumeEnabled = true,
                        isSeekBarVisible = true,
                        isDurationVisible = true,
                        isAutoHideControlEnabled = true,
                        isGestureVolumeControlEnabled = false,
                        controlHideIntervalSeconds = 5,
                    )
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
}