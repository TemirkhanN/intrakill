package me.nasukhov.intrakill.ui.clipboard

import me.nasukhov.intrakill.domain.model.Attachment
import me.nasukhov.intrakill.storage.Filesystem
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

actual fun copyImageAttachmentToClipboard(attachment: Attachment) {
    val file = Filesystem.getTmpFile("clipboard-image").withExtension(attachment.fileExtension())
    val bytes =
        attachment.content.use { input ->
            input.readBytes()
        }
    file.outputStream().use { output ->
        output.write(bytes)
    }

    val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    runCatching {
        clipboard.setContents(
            ImageAttachmentTransferable(
                file = file,
                image = image,
            ),
            null,
        )
    }.getOrElse {
        clipboard.setContents(FileAttachmentTransferable(file), null)
    }
}

private class ImageAttachmentTransferable(
    private val file: File,
    private val image: Image?,
) : Transferable {
    private val uriListFlavor = DataFlavor("text/uri-list;class=java.lang.String")

    override fun getTransferDataFlavors(): Array<DataFlavor> =
        buildList {
            add(DataFlavor.javaFileListFlavor)
            if (image != null) add(DataFlavor.imageFlavor)
            add(uriListFlavor)
            add(DataFlavor.stringFlavor)
        }.toTypedArray()

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = getTransferDataFlavors().any { it == flavor }

    override fun getTransferData(flavor: DataFlavor): Any =
        when {
            flavor == DataFlavor.javaFileListFlavor -> listOf(file)
            image != null && flavor == DataFlavor.imageFlavor -> image
            flavor == uriListFlavor -> file.toURI().toString()
            flavor == DataFlavor.stringFlavor -> file.toURI().toString()
            else -> throw UnsupportedOperationException("Unsupported clipboard flavor: $flavor")
        }
}

private class FileAttachmentTransferable(
    private val file: File,
) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

    override fun getTransferData(flavor: DataFlavor): Any =
        if (flavor == DataFlavor.javaFileListFlavor) {
            listOf(file)
        } else {
            throw UnsupportedOperationException("Unsupported clipboard flavor: $flavor")
        }
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
