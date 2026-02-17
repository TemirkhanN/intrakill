package me.nasukhov.intrakill.storage

actual object DbExporter {
    actual fun stop() {
        // TODO
    }

    actual fun start(plainPassword: String, port: Int, onExportStateChange: (ExportProcess) -> Unit): Boolean {
        return false
    }
}