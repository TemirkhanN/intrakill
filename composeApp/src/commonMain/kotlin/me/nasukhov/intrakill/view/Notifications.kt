package me.nasukhov.intrakill.view

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sun.org.apache.xml.internal.serializer.utils.Utils.messages

enum class NotificationType(
    internal val priority: Int,
) {
    INFO(100),
    WARNING(50),
    ERROR(10), ;

    internal fun color(): Color =
        when (this) {
            INFO -> Color.Green
            WARNING -> Color.Yellow
            ERROR -> Color.Red
        }
}

data class Notification(
    val message: String,
    val type: NotificationType = NotificationType.INFO,
) {
    companion object {
        fun error(message: String) = Notification(message, NotificationType.ERROR)

        fun errors(vararg messages: String) = messages.map(::error)

        fun errors(messages: Collection<String>) = messages.map(::error)

        fun warning(message: String) = Notification(message, NotificationType.WARNING)

        fun warnings(vararg messages: String) = messages.map(::warning)

        fun info(message: String) = Notification(message, NotificationType.INFO)

        fun infos(vararg messages: String) = messages.map(::info)
    }
}

@Composable
fun Notifications(notifications: List<Notification>) {
    if (notifications.isEmpty()) {
        return
    }

    notifications
        .sortedBy { it.type.priority }
        .forEach {
            Text(
                text = it.message,
                color = it.type.color(),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
}
