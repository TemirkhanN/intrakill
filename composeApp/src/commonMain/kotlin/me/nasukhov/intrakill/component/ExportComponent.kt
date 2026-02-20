package me.nasukhov.intrakill.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import me.nasukhov.intrakill.getLocalIpAddress
import me.nasukhov.intrakill.navigation.Request
import me.nasukhov.intrakill.storage.DbExporter
import me.nasukhov.intrakill.storage.ExportProcess
import me.nasukhov.intrakill.validatePassword

data class ExportState(
    val password: String = "",
    val port: Int = 8080,
    val ip: String = "",
    val isEnabled: Boolean = false,
    val isInProgress: Boolean = false,
    val errors: List<String> = emptyList(),
)

interface ExportComponent {
    val state: Value<ExportState>

    fun setPassword(password: String)

    fun setPort(port: Int)

    fun enable()

    fun disable()

    fun close()
}

class DefaultExportComponent(
    context: ComponentContext,
    private val navigate: (Request) -> Unit,
): ExportComponent, ComponentContext by context {
    private data class ExportParams(
        val password: String = "",
        val port: Int = 8080,
    )

    private val params = MutableValue(ExportParams())

    private val mutableState = MutableValue(ExportState(ip = getLocalIpAddress()))
    override val state: Value<ExportState> = mutableState

    init {
        params.subscribe { newParams ->
            mutableState.update { it.copy(
                password = newParams.password,
                port = newParams.port,
            ) }
        }
    }

    override fun setPassword(password: String) {
        params.update { it.copy(password = password) }
    }

    override fun setPort(port: Int) {
        params.update { it.copy(port = port) }
    }

    override fun enable() {
        val current = mutableState.value
        check(!current.isEnabled)
        val violations = current.password.validatePassword()
        if (!violations.isEmpty()) {
            mutableState.update { it.copy(errors = violations) }
            return
        }

        // Switch state to enabled until exporter confirms it
        mutableState.update { it.copy(errors = emptyList(), isEnabled = true) }

        val enabled = DbExporter.start(current.password, current.port) { exportProcess ->
            // Basically, hook that switches begin/end state of the export process
            // Note: this is from exporters perspective. Importer might need some additional time to handle
            // received instructions. i.e. download file (provided by exporter), then handle it (performed by importer)
            mutableState.update { it.copy(isInProgress = exportProcess == ExportProcess.BEGUN) }
        }

        if (!enabled) {
            mutableState.update { it.copy(
                errors = listOf("Couldn't enable export. Likely, wrong password."),
                isEnabled = false
            ) }
        }
    }

    override fun disable() {
        mutableState.update { it.copy(isEnabled = false, isInProgress = false) }
        DbExporter.stop()
    }

    override fun close() {
        disable()
        navigate(Request.Back)
    }
}