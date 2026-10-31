package me.nasukhov.intrakill.storage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlin.coroutines.resume

/**
 * Cross-platform FilePicker.
 * On Android, it delegates to ProvideFilePicker infrastructure.
 */
internal class FilePickerAndroid : FilePicker {
    @Volatile
    internal var delegate: AndroidFilePickerDelegate? = null

    override suspend fun pickMultiple(): List<Result<PickedMedia>> =
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
 */
@Composable
actual fun ProvideFilePicker(content: @Composable () -> Unit) {
    val filePicker: FilePickerAndroid = koinInject()

    val maxAllowedFileSize: FileSize = 100 * 1024 * 1024

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var continuation: ((List<Result<PickedMedia>>) -> Unit)? = null

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris: List<Uri> ->
            val cont = continuation
            continuation = null
            if (cont == null) return@rememberLauncherForActivityResult

            scope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        uris.map { uri ->
                            context.readPickedMedia(uri).mapCatching { media ->
                                check(media.size <= maxAllowedFileSize) {
                                    "File is too big (${media.size.mb} out of ${maxAllowedFileSize.mb})"
                                }
                                media
                            }
                        }
                    }

                cont(result)
            }
        }

    val delegate =
        remember(launcher) {
            object : AndroidFilePickerDelegate {
                override suspend fun pickMultiple(): List<Result<PickedMedia>> =
                    suspendCancellableCoroutine { cont ->
                        continuation = cont::resume

                        cont.invokeOnCancellation {
                            continuation = null
                        }

                        launcher.launch(arrayOf("*/*"))
                    }
            }
        }

    DisposableEffect(Unit) {
        filePicker.delegate = delegate
        onDispose { filePicker.delegate = null }
    }

    content()
}
