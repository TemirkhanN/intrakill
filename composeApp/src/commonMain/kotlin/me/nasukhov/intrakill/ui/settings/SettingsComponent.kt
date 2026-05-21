package me.nasukhov.intrakill.ui.settings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.domain.model.AppSetting
import me.nasukhov.intrakill.domain.model.Settings
import me.nasukhov.intrakill.domain.repository.MediaRepository
import me.nasukhov.intrakill.kmp.coroutineScope
import me.nasukhov.intrakill.ui.root.Request
import me.nasukhov.intrakill.ui.view.Notification
import me.nasukhov.intrakill.validatePassword

data class AppSettings(
    val entriesPerPage: AppSetting<Int>,
    val password: AppSetting<String>,
    val notifications: List<Notification> = emptyList(),
    val isSaving: Boolean = false,
)

interface SettingsComponent {
    val state: Value<AppSettings>

    fun changePassword(password: String)

    fun changeEntriesPerPage(value: Int)

    fun save()

    fun close()
}

class DefaultSettingsComponent(
    context: ComponentContext,
    private val navigate: (Request) -> Unit,
) : SettingsComponent,
    ComponentContext by context {
    private val scope = instanceKeeper.coroutineScope()
    private val mutableState =
        MutableValue(
            AppSettings(
                entriesPerPage = Settings.entriesPerPage,
                password = AppSetting.applied(""),
            ),
        )

    override val state: Value<AppSettings> = mutableState

    override fun changePassword(password: String) {
        mutableState.update { it.copy(password = AppSetting(password)) }
    }

    override fun changeEntriesPerPage(value: Int) {
        if (mutableState.value.entriesPerPage.value == value) {
            return
        }

        // Sanity check
        check(value in 1..100)

        mutableState.update { it.copy(entriesPerPage = AppSetting(value)) }
    }

    override fun save() {
        val settings = state.value
        if (settings.isSaving) {
            return
        }

        val notifications = mutableListOf<Notification>()

        Settings.setEntriesPerPage(settings.entriesPerPage.value)
        if (Settings.haveChanged()) {
            mutableState.update { it.copy(isSaving = true, notifications = notifications) }
            Settings.save()
            notifications.add(Notification.info("New settings applied"))

            // Mark settings as applied
            mutableState.update {
                it.copy(
                    isSaving = false,
                    entriesPerPage = it.entriesPerPage.copy(isApplied = true),
                    notifications = notifications,
                )
            }
        }

        val newPassword = settings.password.value
        if (settings.password.isApplied) {
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
                notifications.add(Notification.info("New password is set"))
                mutableState.update {
                    it.copy(
                        isSaving = false,
                        notifications = notifications,
                        password = it.password.copy(isApplied = true),
                    )
                }
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
