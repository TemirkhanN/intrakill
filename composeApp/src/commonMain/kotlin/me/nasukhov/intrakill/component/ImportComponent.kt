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

data class ImportState(
    val ip: String = "192.168.0.1",
    val password: String = "",
    val violations: List<String> = emptyList(),
    val isInProgress: Boolean = false,
)

interface ImportComponent {
    val state: Value<ImportState>

    fun changeIp(ip: String)
    fun changePassword(password: String)

    fun close()
    fun import()
}

class DefaultImportComponent(
    context: ComponentContext,
    private val navigate: (Request) -> Unit
) : ImportComponent, ComponentContext by context {
    private val scope = instanceKeeper.coroutineScope()

    private val mutableState = MutableValue(ImportState())
    override val state: Value<ImportState> = mutableState

    private val localIpPattern = """(127|192)\.\d{1,3}\.\d{1,3}\.\d{1,3}""".toRegex()

    private val minPasswordLength = 6

    init {
        context.backHandler.register(BackCallback(onBack = ::close))
    }

    override fun changeIp(ip: String) {
        mutableState.update { it.copy(ip = ip, violations = emptyList()) }
    }

    override fun changePassword(password: String) {
        mutableState.update { it.copy(password = password.trim(), violations = emptyList()) }
    }

    override fun import() {
        val current = state.value
        if (current.isInProgress) return

        val violations = validate(current)
        if (!violations.isEmpty()) {
            mutableState.update { it.copy(violations = violations) }
            return
        }

        scope.launch {
            mutableState.update { it.copy(isInProgress = true, violations = violations) }
            val errors = mutableListOf<String>()
            val success = try {
                DbImporter.importDatabase(
                    ip = current.ip,
                    password = current.password
                )
            } catch (e: Exception) {
                errors += (e.message ?: "Fatal error on import attempt.")
                false
            }

            if (success && MediaRepository.unlock(current.password)) {
                navigate(Request.ListEntries())
            } else {
                mutableState.update { it.copy(
                    isInProgress = false,
                    violations = errors + "Failed to perform import."
                ) }
            }
        }
    }

    override fun close() {
        if (state.value.isInProgress) return
        navigate(Request.Back)
    }

    private fun validate(state: ImportState): List<String> {
        val violations = mutableListOf<String>()

        if (!state.ip.matches(localIpPattern)) {
            violations.add("Invalid IP address. Only local IP starting with 127 or 192 is allowed.")
        }

        if (state.password.length < minPasswordLength) {
            violations.add("Password must be at least $minPasswordLength characters.")
        }

        return violations
    }
}