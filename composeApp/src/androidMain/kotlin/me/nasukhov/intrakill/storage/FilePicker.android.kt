package me.nasukhov.intrakill.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.math.min
import androidx.core.graphics.createBitmap

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
    // TODO this is an architectural flow consequence preventing too big files. Those should go in stream mode
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
                uris.mapNotNull { uri ->
                    val fileSize = context.contentResolver.getFileSize(uri)
                    if (fileSize != null && fileSize > maxAllowedFileSize) {
                        Result.failure(Exception("Files is too big (${fileSize.MB()} out of ${maxAllowedFileSize.MB()}"))
                    } else {
                        context.contentResolver.readPickedMedia(uri)
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


actual fun generatePreviewBytes(
    bytes: ByteArray,
    mimeType: String,
    previewSize: Int
): ByteArray =
    when (mimeType.mediaKind()) {
        MediaKind.IMAGE, MediaKind.GIF ->
            generateImagePreviewAndroid(bytes, previewSize)

        MediaKind.VIDEO ->
            generateVideoPlaceholder(previewSize)
    }

private fun generateImagePreviewAndroid(
    bytes: ByteArray,
    maxSize: Int
): ByteArray {
    val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: error("Bitmap decode failed")

    val scale = min(
        maxSize.toFloat() / src.width,
        maxSize.toFloat() / src.height
    ).coerceAtMost(1f)

    val w = (src.width * scale).toInt()
    val h = (src.height * scale).toInt()

    val resized = createBitmap(w, h)
    val canvas = Canvas(resized)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    canvas.drawBitmap(
        src,
        null,
        android.graphics.Rect(0, 0, w, h),
        paint
    )

    return ByteArrayOutputStream().also {
        resized.compress(Bitmap.CompressFormat.JPEG, 85, it)
    }.toByteArray()
}

private fun generateVideoPlaceholder(size: Int): ByteArray {
    val bmp = createBitmap(size, size)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    canvas.drawRGB(30, 30, 30)

    paint.color = android.graphics.Color.WHITE
    canvas.drawPath(
        android.graphics.Path().apply {
            moveTo(size / 3f, size / 4f)
            lineTo(size / 3f, size * 3 / 4f)
            lineTo(size * 2 / 3f, size / 2f)
            close()
        },
        paint
    )

    return ByteArrayOutputStream().also {
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, it)
    }.toByteArray()
}
