package me.nasukhov.intrakill.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.nasukhov.intrakill.scene.PickedMedia
import java.io.File
import java.nio.file.Files
import javax.swing.JFileChooser

actual object FilePicker {

    actual suspend fun pickMultiple(): List<PickedMedia> = withContext(Dispatchers.IO) {
        val chooser = JFileChooser().apply {
            isMultiSelectionEnabled = true
            dialogTitle = "Select photos or videos"
        }

        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFiles.mapNotNull { file: File ->
                try {
                    PickedMedia(
                        name = file.name,
                        bytes = file.readBytes(),
                        mimeType = Files.probeContentType(file.toPath()) ?: "application/octet-stream"
                    )
                } catch (e: Exception) {
                    println("Failed to read file ${file.absolutePath}: ${e.message}")
                    null
                }
            }
        } else {
            emptyList()
        }
    }
}
