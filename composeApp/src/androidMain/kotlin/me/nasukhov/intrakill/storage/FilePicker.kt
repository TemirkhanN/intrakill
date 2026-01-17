package me.nasukhov.intrakill.storage

import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.suspendCancellableCoroutine
import me.nasukhov.intrakill.MainActivity
import me.nasukhov.intrakill.scene.PickedMedia

/*
actual object FilePicker {

    actual suspend fun pickMultiple(): List<PickedMedia> =
        suspendCancellableCoroutine { cont ->
            val launcher = MainActivity.activity
                .activityResultRegistry
                .register(
                    "pick_files",
                    ActivityResultContracts.OpenMultipleDocuments()
                ) { uris ->

                    val result = uris.mapNotNull { uri ->
                        val resolver = MainActivity.activity.contentResolver
                        resolver.openInputStream(uri)?.use {
                            PickedMedia(
                                name = uri.lastPathSegment ?: "file",
                                bytes = it.readBytes(),
                                mimeType = resolver.getType(uri) ?: "application/octet-stream"
                            )
                        }
                    }
                    cont.resume(result)
                }

            launcher.launch(arrayOf("image/*", "video/*"))
        }
}
 */