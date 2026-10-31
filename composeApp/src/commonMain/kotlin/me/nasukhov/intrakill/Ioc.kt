package me.nasukhov.intrakill

import com.arkivanov.decompose.ComponentContext
import me.nasukhov.intrakill.domain.model.Settings
import me.nasukhov.intrakill.domain.repository.MediaRepository
import me.nasukhov.intrakill.storage.DbExporter
import me.nasukhov.intrakill.storage.DbImporter
import me.nasukhov.intrakill.storage.ExternalStorage
import me.nasukhov.intrakill.ui.auth.DefaultLoginComponent
import me.nasukhov.intrakill.ui.auth.LoginComponent
import me.nasukhov.intrakill.ui.datasync.DefaultExportComponent
import me.nasukhov.intrakill.ui.datasync.DefaultImportComponent
import me.nasukhov.intrakill.ui.datasync.ExportComponent
import me.nasukhov.intrakill.ui.datasync.ImportComponent
import me.nasukhov.intrakill.ui.entries.AddEntryComponent
import me.nasukhov.intrakill.ui.entries.DefaultAddEntryComponent
import me.nasukhov.intrakill.ui.entries.DefaultEntryComponent
import me.nasukhov.intrakill.ui.entries.DefaultListEntriesComponent
import me.nasukhov.intrakill.ui.entries.EntryComponent
import me.nasukhov.intrakill.ui.entries.ListEntriesComponent
import me.nasukhov.intrakill.ui.root.ComponentNavigation
import me.nasukhov.intrakill.ui.root.Route
import me.nasukhov.intrakill.ui.settings.DefaultSettingsComponent
import me.nasukhov.intrakill.ui.settings.SettingsComponent
import me.nasukhov.intrakill.ui.tag.TagsComponent
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

// TODO see https://insert-koin.io/docs/reference/koin-core/kmp-shared-modules#organizing-shared-modules
class Ioc(
    platformSpecificDefinition: Module,
    config: KoinAppDeclaration? = null,
) {
    companion object {
        inline fun <reified T : Any> locate(): T = KoinPlatform.getKoin().get<T>()
    }

    init {
        val appModule =
            module {
                singleOf(::MediaRepository)
                singleOf(::DbExporter)
                singleOf(::DbImporter)
                singleOf(::Settings)
                singleOf(::ExternalStorage)
            }

        val decomposeComponents =
            module {
                factory<LoginComponent> { (context: ComponentContext, navigate: ComponentNavigation) ->
                    DefaultLoginComponent(context, navigate, mediaRepository = get())
                }

                factory<ImportComponent> { (context: ComponentContext, navigate: ComponentNavigation) ->
                    DefaultImportComponent(context, navigate, mediaRepository = get(), dbImporter = get())
                }

                factory<ExportComponent> { (context: ComponentContext, navigate: ComponentNavigation) ->
                    DefaultExportComponent(context, navigate, exporter = get())
                }

                factory<ListEntriesComponent> { (context: ComponentContext, navigate: ComponentNavigation, config: Route.List) ->
                    DefaultListEntriesComponent(context, navigate, config, mediaRepository = get(), settings = get())
                }

                factory<EntryComponent> { (context: ComponentContext, navigate: ComponentNavigation, entryId: String) ->
                    DefaultEntryComponent(context, navigate, entryId, filePicker = get(), mediaRepository = get())
                }

                factory<AddEntryComponent> { (context: ComponentContext, navigate: ComponentNavigation) ->
                    DefaultAddEntryComponent(context, navigate, filePicker = get(), mediaRepository = get(), externalStorage = get())
                }

                factory<SettingsComponent> { (context: ComponentContext, navigate: ComponentNavigation) ->
                    DefaultSettingsComponent(context, navigate, mediaRepository = get(), settings = get())
                }

                factory { (context: ComponentContext, navigate: ComponentNavigation) ->
                    TagsComponent(context, navigate, mediaRepository = get())
                }
            }

        startKoin {
            config?.invoke(this)
            modules(appModule, decomposeComponents, platformSpecificDefinition)
        }
    }
}
