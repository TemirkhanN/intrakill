package me.nasukhov.intrakill.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.content.MediaRepository
import me.nasukhov.intrakill.navigation.Request
import me.nasukhov.intrakill.scene.coroutineScope
import me.nasukhov.intrakill.validatePassword
import me.nasukhov.intrakill.view.Notification

data class AppSettings(
    val newPassword: String = "",
    val notifications: List<Notification> = emptyList(),
    val isSaving: Boolean = false,
)

interface SettingsComponent {
    val state: Value<AppSettings>

    fun changePassword(password: String)

    fun save()

    fun close()
}

class DefaultSettingsComponent(
    context: ComponentContext,
    private val navigate: (Request) -> Unit,
) : SettingsComponent,
    ComponentContext by context {
    private val scope = instanceKeeper.coroutineScope()
    private val mutableState = MutableValue(AppSettings())
    override val state: Value<AppSettings> = mutableState

    override fun changePassword(password: String) {
        mutableState.update { it.copy(newPassword = password) }
    }

    override fun save() {
        val settings = state.value
        if (settings.isSaving) {
            return
        }

        // There is only one setting there. If it's not present, no need to do anything
        if (settings.newPassword.isBlank()) {
            return
        }

        mutableState.update { it.copy(isSaving = true) }

        val violations = mutableListOf<String>()

        violations.addAll(settings.newPassword.validatePassword())

        if (!violations.isEmpty()) {
            mutableState.update { it.copy(notifications = violations.map(Notification::error), isSaving = false) }

            return
        }

        scope.launch {
            if (MediaRepository.changePassword(settings.newPassword)) {
                mutableState.update { it.copy(isSaving = false, notifications = listOf(Notification.info("New settings applied"))) }
            } else {
                mutableState.update { it.copy(isSaving = false, notifications = listOf(Notification.error("Could not save settings"))) }
            }
        }
    }

    override fun close() {
        if (state.value.isSaving) {
            return
        }
        navigate(Request.Back)
    }
}
