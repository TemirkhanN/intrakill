package me.nasukhov.intrakill.storage

import me.nasukhov.intrakill.scene.PickedMedia

expect object FilePicker {
    suspend fun pickMultiple(): List<PickedMedia>
}