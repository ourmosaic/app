package space.ourmosaic.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

class NavState(startDestination: Route = Route.Login) {
    private val backStack = mutableStateListOf(startDestination)

    val currentRoute: Route
        get() = backStack.last()

    val canGoBack: Boolean
        get() = backStack.size > 1

    fun navigateTo(route: Route) {
        backStack.add(route)
    }

    fun replaceLast(route: Route) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = route
        }
    }

    fun back(): Boolean {
        if (!canGoBack) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}

@Composable
fun rememberNavState(startDestination: Route = Route.Login): NavState {
    return remember(startDestination) { NavState(startDestination) }
}


