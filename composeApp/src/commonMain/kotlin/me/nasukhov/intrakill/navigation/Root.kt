package me.nasukhov.intrakill.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.router.stack.replaceCurrent
import com.arkivanov.decompose.value.Value
import me.nasukhov.intrakill.component.AddEntryComponent
import me.nasukhov.intrakill.component.DefaultAddEntryComponent
import me.nasukhov.intrakill.component.DefaultImportComponent
import me.nasukhov.intrakill.component.DefaultListEntriesComponent
import me.nasukhov.intrakill.component.DefaultLoginComponent
import me.nasukhov.intrakill.component.DefaultEntryComponent
import me.nasukhov.intrakill.component.DefaultExportComponent
import me.nasukhov.intrakill.component.ImportComponent
import me.nasukhov.intrakill.component.ListEntriesComponent
import me.nasukhov.intrakill.component.LoginComponent
import me.nasukhov.intrakill.component.EntryComponent
import me.nasukhov.intrakill.component.ExportComponent

interface RootComponent {
    val stack: Value<ChildStack<*, Child>>

    sealed class Child {
        class List(val component: ListEntriesComponent) : Child()
        class Login(val component: LoginComponent) : Child()
        class View(val component: EntryComponent) : Child()
        class AddEntry(val component: AddEntryComponent) : Child()
        class Import(val component: ImportComponent): Child()
        class Export(val component: ExportComponent): Child()
    }
}

class DefaultRootComponent(
    componentContext: ComponentContext
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Route>()

    override val stack: Value<ChildStack<Route, RootComponent.Child>> =
        childStack(
            source = navigation,
            serializer = Route.serializer(),
            initialConfiguration = Route.Login,
            handleBackButton = true,
            childFactory = ::createChild
        )

    private fun createChild(route: Route, context: ComponentContext): RootComponent.Child = when (route) {
        is Route.Login -> RootComponent.Child.Login(
            DefaultLoginComponent(
                context = context,
                navigate = ::handleLoginRequests
            )
        )
        is Route.Import -> RootComponent.Child.Import(
            DefaultImportComponent(
                context = context,
                navigate = ::handleImportRequests
            )
        )
        is Route.Export -> RootComponent.Child.Export(
            DefaultExportComponent(
                context = context,
                navigate = ::handleExportRequests
            )
        )
        is Route.List -> RootComponent.Child.List(
            DefaultListEntriesComponent(
                context = context,
                navigate = ::handleContentListRequests,
                filterByTags = route.filterByTags
            )
        )
        is Route.View -> {
            RootComponent.Child.View(
                DefaultEntryComponent(
                    context = context,
                    entryId = route.entryId,
                    navigate = ::handleEntryViewRequests
                ),
            )
        }
        is Route.AddEntry -> RootComponent.Child.AddEntry(
            DefaultAddEntryComponent(
                context = context,
                navigate = ::handleAddEntryRequests
            )
        )
    }

    private fun handleLoginRequests(request: Request) = when (request) {
        is Request.ListEntries -> navigation.replaceCurrent(Route.List())
        is Request.ImportRequested -> navigation.push(Route.Import)
        is Request.ExportRequested -> navigation.push(Route.Export)
        else -> error("The request $request is not supported in this component")
    }

    private fun handleExportRequests(request: Request) = when (request) {
        is Request.ListEntries -> navigation.replaceCurrent(Route.List())
        is Request.Back -> navigation.pop()
        else -> error("The request $request is not supported in this component")
    }

    private fun handleImportRequests(request: Request) = when (request) {
        is Request.Back -> navigation.pop()
        is Request.ListEntries -> navigation.replaceCurrent(Route.List())
        else -> error("The request $request is not supported in this component")
    }

    private fun handleContentListRequests(request: Request) = when (request) {
        is Request.ViewEntry -> navigation.push(Route.View(request.id))
        is Request.AddEntry -> navigation.push(Route.AddEntry)
        else -> error("The request $request is not supported in this component")
    }

    private fun handleEntryViewRequests(request: Request) = when (request) {
        is Request.ListEntries -> {
            navigation.replaceAll(Route.List(filterByTags = request.filterByTags))
        }
        is Request.Back -> navigation.pop()
        else -> error("The request $request is not supported in this component")
    }

    private fun handleAddEntryRequests(request: Request) = when (request) {
        is Request.Back -> navigation.pop()
        is Request.ViewEntry -> navigation.replaceCurrent(Route.View(request.id))
        else -> error("The request $request is not supported in this component")
    }
}
