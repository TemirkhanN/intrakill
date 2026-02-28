package me.nasukhov.intrakill.storage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Cross-platform FilePicker.
 * On Android, it delegates to ProvideFilePicker infrastructure.
 */
actual object FilePicker {
    internal var delegate: AndroidFilePickerDelegate? = null

    actual suspend fun pickMultiple(): List<Result<PickedMedia>> =
        delegate?.pickMultiple()
            ?: error("FilePicker not initialized. Wrap your UI in ProvideFilePicker().")
}

/**
 * Internal delegate interface for Android file picking.
 */
internal interface AndroidFilePickerDelegate {
    suspend fun pickMultiple(): List<Result<PickedMedia>>
}

/**
 * Android implementation for Compose Multiplatform.
 *
 * Wrap your Android UI with this to initialize FilePicker.
 */
@Composable
actual fun ProvideFilePicker(
    content: @Composable () -> Unit
) {
    // TODO this is an architectural flow consequence preventing too big files
    val maxAllowedFileSize: FileSize = 100 * 1024 * 1024
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var continuation by remember { mutableStateOf<((List<Result<PickedMedia>>) -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val cont = continuation
        continuation = null
        if (cont == null) return@rememberLauncherForActivityResult

        // Launch coroutine in remembered scope
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    context.readPickedMedia(uri).mapCatching { media ->
                        check(media.size <= maxAllowedFileSize) {
                            "File is too big (${media.size.MB()} out of ${maxAllowedFileSize.MB()})"
                        }
                        media
                    }
                }
            }
            cont(result)
        }
    }

    val delegate = remember {
        object : AndroidFilePickerDelegate {
            override suspend fun pickMultiple(): List<Result<PickedMedia>> =
                suspendCancellableCoroutine { cont ->
                    continuation = cont::resume
                    launcher.launch(arrayOf("*/*"))
                }
        }
    }

    DisposableEffect(Unit) {
        FilePicker.delegate = delegate
        onDispose { FilePicker.delegate = null }
    }

    content()
}
