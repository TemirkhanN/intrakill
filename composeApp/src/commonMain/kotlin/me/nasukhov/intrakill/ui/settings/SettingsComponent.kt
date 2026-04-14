package me.nasukhov.intrakill.ui.settings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.domain.repository.MediaRepository
import me.nasukhov.intrakill.kmp.coroutineScope
import me.nasukhov.intrakill.ui.root.Request
import me.nasukhov.intrakill.ui.view.Notification
import me.nasukhov.intrakill.validatePassword

data class Setting<T>(
    val value: T,
    val isApplied: Boolean = false,
) {
    companion object {
        fun <T> applied(value: T) = Setting(value = value, isApplied = true)
    }
}

data class AppSettings(
    val newPassword: Setting<String> = Setting.applied(""),
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
    settings: AppSettings,
    private val navigate: (Request) -> Unit,
) : SettingsComponent,
    ComponentContext by context {
    private val scope = instanceKeeper.coroutineScope()
    private val mutableState = MutableValue(settings)
    override val state: Value<AppSettings> = mutableState

    override fun changePassword(password: String) {
        mutableState.update { it.copy(newPassword = Setting(password)) }
    }

    override fun save() {
        val settings = state.value
        if (settings.isSaving) {
            return
        }

        val newPassword = settings.newPassword.value
        // There is only one setting there. If it's not present, no need to do anything
        if (settings.newPassword.isApplied) {
            return
        }
        if (newPassword.isBlank()) {
            return
        }

        mutableState.update {
            it.copy(isSaving = true, notifications = Notification.warnings("Password is changing. It might take a while"))
        }

        val errors = Notification.errors(newPassword.validatePassword())
        if (!errors.isEmpty()) {
            mutableState.update { it.copy(notifications = errors, isSaving = false) }

            return
        }

        scope.launch {
            if (MediaRepository.changePassword(newPassword)) {
                mutableState.update { it.copy(isSaving = false, notifications = Notification.infos("New settings applied")) }
            } else {
                mutableState.update { it.copy(isSaving = false, notifications = Notification.errors("Could not save settings")) }
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
