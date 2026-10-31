package me.nasukhov.intrakill

import android.app.Application
import me.nasukhov.intrakill.storage.FilePicker
import me.nasukhov.intrakill.storage.FilePickerAndroid
import me.nasukhov.intrakill.storage.Filesystem
import me.nasukhov.intrakill.storage.FilesystemAndroid
import me.nasukhov.intrakill.storage.SecureDatabase
import me.nasukhov.intrakill.storage.SecureDatabaseAndroid
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

class IntrakillApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val androidSpecificDefinitions =
            module {
                single<Filesystem> { FilesystemAndroid(context = get()) }
                single<FilePicker> { FilePickerAndroid() }
                single<SecureDatabase> { SecureDatabaseAndroid(context = get(), fs = get()) }
            }

        Ioc(androidSpecificDefinitions) {
            androidContext(this@IntrakillApp)
        }
    }
}
