package me.nasukhov.intrakill.ui.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.FileProvider
import me.nasukhov.intrakill.domain.model.Attachment
import me.nasukhov.intrakill.storage.Filesystem
import java.io.File

object AndroidClipboard {
    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    internal fun requireContext(): Context {
        check(::context.isInitialized) { "AndroidClipboard is not initialized" }
        return context
    }
}

actual fun copyImageAttachmentToClipboard(attachment: Attachment) {
    val context = AndroidClipboard.requireContext()
    val file = attachment.writeClipboardCacheFile()
    val uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.clipboardprovider",
            file,
        )
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData =
        ClipData(
            ClipDescription("Image attachment", arrayOf(attachment.mimeType)),
            ClipData.Item(uri),
        )
    clipboardManager.setPrimaryClip(clipData)
}

private fun Attachment.writeClipboardCacheFile(): File {
    val file = Filesystem.getTmpFile("clipboard-image").withExtension(fileExtension())
    content.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    return file
}

private fun Attachment.fileExtension(): String =
    when (mimeType.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        else -> "img"
    }

private fun File.withExtension(extension: String): File {
    val target = File(parentFile, "$nameWithoutExtension.$extension")
    if (this != target) renameTo(target)
    target.deleteOnExit()
    return target
}
