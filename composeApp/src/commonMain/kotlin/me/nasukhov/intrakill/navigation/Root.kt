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
import kotlinx.serialization.Serializable

sealed interface Request {
    data class ViewEntry(val id: String) : Request
    data object AddEntry : Request
    data object ImportRequested: Request
    data class ListEntries(val filterByTags: Set<String> = emptySet()): Request
    data object Back: Request
}

@Serializable
sealed interface Route {
    @Serializable
    data object Login : Route
    @Serializable
    data class List(val filterByTags: Set<String> = emptySet()) : Route
    @Serializable
    data class View(val entryId: String) : Route
    @Serializable
    data object AddEntry : Route
    @Serializable
    data object ImportRequested : Route
}

interface RootComponent {
    val stack: Value<ChildStack<*, Child>>

    sealed class Child {
        class List(val component: ListEntriesComponent) : Child()
        class Login(val component: LoginComponent) : Child()
        class View(val component: ViewEntryComponent) : Child()
        class AddEntry(val component: AddEntryComponent) : Child()
        data class Import(val component: ImportComponent): Child()
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
        is Route.ImportRequested -> RootComponent.Child.Import(
            DefaultImportComponent(
                context = context,
                navigate = ::handleImportRequests
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
                DefaultViewEntryComponent(
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
        else -> error("Not implemented yet $route")
    }

    // Router for all requests on login page
    private fun handleLoginRequests(request: Request) = when (request) {
        is Request.ListEntries -> navigation.replaceCurrent(Route.List())
        is Request.ImportRequested -> navigation.push(Route.ImportRequested)
        else -> error("Not implemented yet $request")
    }

    private fun handleImportRequests(request: Request) = when (request) {
        is Request.Back -> navigation.pop()
        else -> error("Not implemented yet $request")
    }

    // Router for all requests on content list page
    private fun handleContentListRequests(request: Request) = when (request) {
        is Request.ViewEntry -> navigation.push(Route.View(request.id))
        is Request.AddEntry -> navigation.push(Route.AddEntry)
        else -> error("Not implemented yet $request")
    }

    // Router for all requests on entry view page
    private fun handleEntryViewRequests(request: Request) = when (request) {
        is Request.ListEntries -> {
            navigation.replaceAll(Route.List(filterByTags = request.filterByTags))
        }
        is Request.Back -> navigation.pop()
        else -> error("Not implemented yet $request")
    }

    private fun handleAddEntryRequests(request: Request) = when (request) {
        is Request.Back -> navigation.pop()
        is Request.ViewEntry -> navigation.replaceCurrent(Route.View(request.id))
        else -> error("Not implemented yet $request")
    }
}
