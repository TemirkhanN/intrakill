package me.nasukhov.intrakill.domain.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import me.nasukhov.intrakill.storage.SecureDatabase

data class AppSetting<T>(
    val value: T,
    val isApplied: Boolean = false,
) {
    companion object {
        fun <T> applied(value: T) = AppSetting(value = value, isApplied = true)
    }
}

class Settings(
    private val db: SecureDatabase,
) {
    companion object {
        private const val NAME_ENTRIES_PER_PAGE = "entriesPerPage"
    }

    private var persisted = lazy { db.getSettings() }

    var entriesPerPage: AppSetting<Int> = AppSetting.applied(getSetting(NAME_ENTRIES_PER_PAGE, "12").toInt())
        private set

    val current
        get() =
            mapOf(
                NAME_ENTRIES_PER_PAGE to entriesPerPage.value.toString(),
            )

    private val _updates = MutableSharedFlow<Unit>(replay = 1)

    val updates: Flow<Unit> = _updates

    private fun getSetting(
        name: String,
        default: String,
    ): String = persisted.value.getOrElse(name) { default }

    fun haveChanged(): Boolean = !entriesPerPage.isApplied

    fun setEntriesPerPage(value: Int) {
        if (value == entriesPerPage.value) {
            return
        }

        entriesPerPage = AppSetting(value)
    }

    fun save() {
        if (!haveChanged()) {
            return
        }

        val newSettings = current
        db.updateSettings(newSettings)
        persisted = lazy { db.getSettings() }

        _updates.tryEmit(Unit)
    }
}
