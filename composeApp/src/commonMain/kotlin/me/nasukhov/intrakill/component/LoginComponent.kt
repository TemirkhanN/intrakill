package me.nasukhov.intrakill.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import kotlinx.coroutines.launch
import me.nasukhov.intrakill.content.MediaRepository
import me.nasukhov.intrakill.navigation.Request
import me.nasukhov.intrakill.scene.coroutineScope

data class LoginState(
    val password: String = "",
    val isLoggingIn: Boolean = false,
    val violations: List<String> = emptyList(),
)

interface LoginComponent {
    val state: Value<LoginState>

    fun onPasswordChanged(newValue: String)
    fun onUnlockClicked()
    fun onImportClicked()
}

class DefaultLoginComponent(
    context: ComponentContext,
    private val navigate: (Request) -> Unit
) : LoginComponent, ComponentContext by context {

    private val scope = instanceKeeper.coroutineScope()
    private val mutableState = MutableValue(LoginState())
    override val state: Value<LoginState> = mutableState

    override fun onPasswordChanged(newValue: String) {
        mutableState.update { it.copy(password = newValue, violations = emptyList()) }
    }

    override fun onUnlockClicked() {
        val current = state.value
        if (current.isLoggingIn) {
            return
        }

        val violations = validate(current)
        if (!violations.isEmpty()) {
            mutableState.update { it.copy(violations = violations, isLoggingIn = false) }
            return
        }

        scope.launch {
            mutableState.update { it.copy(isLoggingIn = true, violations = violations) }
            if (MediaRepository.unlock(current.password)) {
                navigate(Request.ListEntries())
            } else {
                mutableState.update { it.copy(violations = violations + "Incorrect password.", isLoggingIn = false) }
            }
        }
    }

    override fun onImportClicked() = navigate(Request.ImportRequested)

    private fun validate(state: LoginState): List<String> {
        val violations = mutableListOf<String>()
        if (state.password.length < 6) {
            violations.add("Password must be at least 6 characters.")
        }

        return violations
    }
}