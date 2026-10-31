package me.nasukhov.intrakill.ui.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.router.stack.replaceCurrent
import com.arkivanov.decompose.value.Value
import me.nasukhov.intrakill.ui.auth.LoginComponent
import me.nasukhov.intrakill.ui.datasync.ExportComponent
import me.nasukhov.intrakill.ui.datasync.ImportComponent
import me.nasukhov.intrakill.ui.entries.AddEntryComponent
import me.nasukhov.intrakill.ui.entries.EntryComponent
import me.nasukhov.intrakill.ui.entries.ListEntriesComponent
import me.nasukhov.intrakill.ui.settings.SettingsComponent
import me.nasukhov.intrakill.ui.tag.TagsComponent
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf

typealias ComponentNavigation = (Request) -> Unit

interface RootComponent {
    val stack: Value<ChildStack<*, Child>>

    sealed class Child {
        class List(
            val component: ListEntriesComponent,
        ) : Child()

        class Login(
            val component: LoginComponent,
        ) : Child()

        class View(
            val component: EntryComponent,
        ) : Child()

        class AddEntry(
            val component: AddEntryComponent,
        ) : Child()

        class Import(
            val component: ImportComponent,
        ) : Child()

        class Export(
            val component: ExportComponent,
        ) : Child()

        class Settings(
            val component: SettingsComponent,
        ) : Child()

        class Tags(
            val component: TagsComponent,
        ) : Child()
    }
}

class DefaultRootComponent(
    componentContext: ComponentContext,
) : RootComponent,
    KoinComponent,
    ComponentContext by componentContext {
    private val navigation = StackNavigation<Route>()

    override val stack: Value<ChildStack<Route, RootComponent.Child>> =
        childStack(
            source = navigation,
            serializer = Route.serializer(),
            initialConfiguration = Route.Login,
            handleBackButton = true,
            childFactory = ::createChild,
        )

    private fun createChild(
        route: Route,
        context: ComponentContext,
    ): RootComponent.Child =
        when (route) {
            is Route.Login ->
                RootComponent.Child.Login(get { parametersOf(context, ::handleLoginRequests) })

            is Route.Import ->
                RootComponent.Child.Import(get { parametersOf(context, ::handleImportRequests) })

            is Route.Export ->
                RootComponent.Child.Export(get { parametersOf(context, ::handleExportRequests) })

            is Route.List ->
                RootComponent.Child.List(get { parametersOf(context, ::handleContentListRequests, route) })

            is Route.View -> {
                RootComponent.Child.View(
                    get { parametersOf(context, ::handleEntryViewRequests, route.entryId) },
                )
            }

            is Route.AddEntry ->
                RootComponent.Child.AddEntry(get { parametersOf(context, ::handleAddEntryRequests) })

            is Route.OpenSettings -> {
                RootComponent.Child.Settings(get { parametersOf(context, ::handleSettingsRequests) })
            }

            is Route.OpenTags -> {
                RootComponent.Child.Tags(get { parametersOf(context, ::handleTagsRequests) })
            }
        }

    @OptIn(DelicateDecomposeApi::class)
    private fun handleTagsRequests(request: Request) {
        when (request) {
            is Request.Back -> navigation.pop()
            is Request.ListEntries -> navigation.replaceCurrent(Route.List(filterByTags = request.filterByTags))
            else -> error("The request $request is not supported in this component")
        }
    }

    private fun handleSettingsRequests(request: Request) =
        when (request) {
            is Request.Back -> navigation.pop()
            else -> error("The request $request is not supported in this component")
        }

    @OptIn(DelicateDecomposeApi::class)
    private fun handleLoginRequests(request: Request) =
        when (request) {
            is Request.ListEntries -> navigation.replaceCurrent(Route.List())
            is Request.ImportRequested -> navigation.push(Route.Import)
            is Request.ExportRequested -> navigation.push(Route.Export)
            else -> error("The request $request is not supported in this component")
        }

    private fun handleExportRequests(request: Request) =
        when (request) {
            is Request.ListEntries -> navigation.replaceCurrent(Route.List())
            is Request.Back -> navigation.pop()
            else -> error("The request $request is not supported in this component")
        }

    private fun handleImportRequests(request: Request) =
        when (request) {
            is Request.Back -> navigation.pop()
            is Request.ListEntries -> navigation.replaceCurrent(Route.List())
            else -> error("The request $request is not supported in this component")
        }

    @OptIn(DelicateDecomposeApi::class)
    private fun handleContentListRequests(request: Request) =
        when (request) {
            is Request.ViewEntry -> navigation.push(Route.View(request.id))
            is Request.AddEntry -> navigation.push(Route.AddEntry)
            is Request.OpenSettings -> navigation.push(Route.OpenSettings)
            is Request.OpenTags -> navigation.push(Route.OpenTags)
            else -> error("The request $request is not supported in this component")
        }

    private fun handleEntryViewRequests(request: Request) =
        when (request) {
            is Request.ListEntries -> {
                navigation.replaceAll(Route.List(filterByTags = request.filterByTags))
            }
            is Request.Back -> navigation.pop()
            else -> error("The request $request is not supported in this component")
        }

    private fun handleAddEntryRequests(request: Request) =
        when (request) {
            is Request.Back -> navigation.pop()
            is Request.ViewEntry -> navigation.replaceCurrent(Route.View(request.id))
            else -> error("The request $request is not supported in this component")
        }
}
