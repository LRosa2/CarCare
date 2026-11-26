package io.github.lrosa2.carcare.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lrosa2.carcare.data.CarRepository
import io.github.lrosa2.carcare.reminders.Notifications

sealed interface Screen {
    data object Items : Screen
    data object History : Screen
    data object CarInfo : Screen
    data object Settings : Screen
    data class EnterKm(val readingId: Long? = null, val afterImport: Boolean = false) : Screen
    data class Item(val id: String) : Screen
    data class Visit(val id: Long? = null) : Screen
    data class Card(val id: Long? = null) : Screen
}

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Screen.Items, "Items", Icons.Default.Build),
    Tab(Screen.History, "History", Icons.Default.DateRange),
    Tab(Screen.CarInfo, "Car info", Icons.Default.Info),
    Tab(Screen.Settings, "Settings", Icons.Default.Settings),
)

/** A small hand-made back stack: tabs at the root, detail screens pushed on top. */
class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Items)
    val current: Screen get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun root(s: Screen) { stack.clear(); stack.add(s) }
}

@Composable
fun CarCareRoot(repo: CarRepository, openRequest: String?, onOpenHandled: () -> Unit) {
    val home by repo.home.collectAsStateWithLifecycle(initialValue = null)
    val nav = remember { Nav() }
    val state = home
    if (state == null) { // first frame: data not loaded yet
        Box(Modifier.fillMaxSize())
        return
    }

    // Decided once from the first loaded state, so onboarding stays up while it asks for the
    // notification permission even though the import has already marked the app onboarded.
    val startedEmpty = remember { !state.settings.onboarded && !state.hasItems }
    var onboardingDone by rememberSaveable { mutableStateOf(false) }
    if (startedEmpty && !onboardingDone) {
        OnboardingScreen(repo, onDone = { askKm ->
            onboardingDone = true
            nav.root(Screen.Items)
            if (askKm) nav.push(Screen.EnterKm(afterImport = true))
        })
        return
    }

    LaunchedEffect(openRequest) {
        when (openRequest) {
            Notifications.OPEN_ENTER_KM -> { nav.root(Screen.Items); nav.push(Screen.EnterKm()); onOpenHandled() }
            Notifications.OPEN_HOME -> { nav.root(Screen.Items); onOpenHandled() }
        }
    }

    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    val bottomBar: @Composable () -> Unit = {
        NavigationBar {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = nav.stack.first() == tab.screen,
                    onClick = { nav.root(tab.screen) },
                    icon = { Icon(tab.icon, null) },
                    label = { Text(tab.label) },
                )
            }
        }
    }

    when (val s = nav.current) {
        Screen.Items -> HomeScreen(state, bottomBar, onEnterKm = { nav.push(Screen.EnterKm()) }, onItem = { nav.push(Screen.Item(it)) }, onLogVisit = { nav.push(Screen.Visit()) }, onAddItem = { nav.push(Screen.Item(it)) }, repo = repo, onSettings = { nav.root(Screen.Settings) })
        Screen.History -> HistoryScreen(repo, state, bottomBar, onVisit = { nav.push(Screen.Visit(it)) }, onReading = { nav.push(Screen.EnterKm(readingId = it)) })
        Screen.CarInfo -> CarInfoScreen(repo, bottomBar, onCard = { nav.push(Screen.Card(it)) })
        Screen.Settings -> SettingsScreen(repo, state, bottomBar)
        is Screen.EnterKm -> EnterKmScreen(repo, state, s.readingId, s.afterImport, onDone = { nav.pop() })
        is Screen.Item -> ItemScreen(repo, state, s.id, onDone = { nav.pop() }, onVisit = { nav.push(Screen.Visit(it)) })
        is Screen.Visit -> VisitScreen(repo, state, s.id, onDone = { nav.pop() })
        is Screen.Card -> CardScreen(repo, s.id, onDone = { nav.pop() })
    }
}
