package me.nasukhov.intrakill.storage

enum class ExportProcess {
    BEGUN,
    END
}

expect object DbExporter {
    fun start(plainPassword: String, port: Int = 8080, onExportStateChange: (ExportProcess) -> Unit = {}): Boolean

    fun stop()
}