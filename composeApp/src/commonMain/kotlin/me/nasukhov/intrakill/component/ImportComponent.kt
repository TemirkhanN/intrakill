package me.nasukhov.intrakill.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.backhandler.BackCallback
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.content.MediaRepository
import me.nasukhov.intrakill.navigation.Request
import me.nasukhov.intrakill.scene.coroutineScope
import me.nasukhov.intrakill.storage.DbImporter
import me.nasukhov.intrakill.storage.Progress
import me.nasukhov.intrakill.storage.StorageSource
import me.nasukhov.intrakill.validatePassword
import me.nasukhov.intrakill.view.Notification

data class ImportState(
    val ip: String = "192.168.0.1",
    val password: String = "",
    val notifications: List<Notification> = emptyList(),
    val isInProgress: Boolean = false,
    val progress: Progress = Progress.EMPTY,
    val isPartialImport: Boolean = false,
)

interface ImportComponent {
    val state: Value<ImportState>

    fun changeIp(ip: String)

    fun changePassword(password: String)

    fun close()

    fun import()

    fun sync()
}

class DefaultImportComponent(
    context: ComponentContext,
    private val navigate: (Request) -> Unit,
) : ImportComponent,
    ComponentContext by context {
    private val scope = instanceKeeper.coroutineScope()

    private val mutableState = MutableValue(ImportState())
    override val state: Value<ImportState> = mutableState

    private val localIpPattern = """(127|192)\.\d{1,3}\.\d{1,3}\.\d{1,3}""".toRegex()

    init {
        context.backHandler.register(BackCallback(onBack = ::close))
    }

    override fun changeIp(ip: String) {
        mutableState.update { it.copy(ip = ip, notifications = emptyList()) }
    }

    override fun changePassword(password: String) {
        mutableState.update { it.copy(password = password.trim(), notifications = emptyList()) }
    }

    override fun import() {
        val current = state.value
        if (current.isInProgress) return

        val violations = validate(current)
        if (!violations.isEmpty()) {
            mutableState.update { it.copy(notifications = violations) }
            return
        }

        scope.launch {
            mutableState.update { it.copy(isInProgress = true, isPartialImport = false, notifications = violations) }
            val errors = mutableListOf<Notification>()
            val success =
                try {
                    DbImporter.importDatabase(
                        source = StorageSource(current.ip, 8080),
                        password = current.password,
                    ) { progress ->
                        if (progress != state.value.progress) {
                            mutableState.update { it.copy(progress = progress) }
                        }
                    }
                } catch (e: Exception) {
                    errors += Notification.error((e.message ?: "Fatal error on import attempt."))
                    false
                }

            if (success && MediaRepository.unlock(current.password)) {
                navigate(Request.ListEntries())
            } else {
                mutableState.update {
                    it.copy(
                        isInProgress = false,
                        notifications = errors + Notification.error("Failed to perform import."),
                    )
                }
            }
        }
    }

    override fun sync() {
        val current = state.value
        if (current.isInProgress) return

        val violations = validate(current)
        if (!violations.isEmpty()) {
            mutableState.update { it.copy(notifications = violations) }
            return
        }

        scope.launch {
            if (!MediaRepository.unlock(current.password)) {
                mutableState.update {
                    it.copy(
                        isInProgress = false,
                        notifications = listOf(Notification.error("Incorrect password")),
                    )
                }
                return@launch
            }

            mutableState.update { it.copy(isInProgress = true, isPartialImport = true, notifications = violations) }

            try {
                DbImporter.syncEntries(
                    StorageSource(current.ip, 8080),
                    password = current.password,
                    onProgress = { newProgress -> mutableState.update { it.copy(progress = newProgress) } },
                )

                mutableState.update { it.copy(isInProgress = false) }
            } catch (e: Exception) {
                mutableState.update {
                    it.copy(
                        isInProgress = false,
                        notifications = listOf(Notification.error(e.message ?: "Could not perform sync.")),
                    )
                }
            }
        }
    }

    override fun close() {
        if (state.value.isInProgress) return
        navigate(Request.Back)
    }

    private fun validate(state: ImportState): List<Notification> {
        val violations = mutableListOf<String>()

        if (!state.ip.matches(localIpPattern)) {
            violations.add("Invalid IP address. Only local IP starting with 127 or 192 is allowed.")
        }

        violations.addAll(state.password.validatePassword())

        return violations.map { Notification.error(it) }
    }
}
