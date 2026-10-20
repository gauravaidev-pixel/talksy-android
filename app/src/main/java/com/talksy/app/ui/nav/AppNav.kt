package com.talksy.app.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.talksy.app.AppGraph
import com.talksy.app.ui.screens.chat.ChatInfoScreen
import com.talksy.app.ui.screens.chat.ChatScreen
import com.talksy.app.ui.screens.chats.ChatsScreen
import com.talksy.app.ui.screens.call.CallOverlay
import com.talksy.app.ui.screens.calls.CallsScreen
import com.talksy.app.ui.screens.chats.ChatsViewModel
import com.talksy.app.ui.screens.groups.NewGroupScreen
import com.talksy.app.ui.screens.login.LoginScreen
import com.talksy.app.ui.screens.polls.CreatePollScreen
import com.talksy.app.ui.screens.profile.BlockedScreen
import com.talksy.app.ui.screens.profile.EditProfileScreen
import com.talksy.app.ui.screens.profile.ProfileScreen
import com.talksy.app.ui.screens.status.StatusScreen
import com.talksy.app.ui.screens.status.StatusViewModel

object Routes {
    const val LOGIN = "login"
    const val CHATS = "chats"
    const val STATUS = "status"
    const val CALLS = "calls"
    const val PROFILE = "profile"
    const val EDIT_PROFILE = "editprofile"
    const val BLOCKED = "blocked"
    const val CHAT = "chat/{chatId}"
    const val INFO = "info/{chatId}"
    const val NEW_CHAT = "newchat"
    const val NEW_GROUP = "newgroup"
    const val NEW_POLL = "newpoll/{chatId}"

    fun chat(id: String) = "chat/$id"
    fun info(id: String) = "info/$id"
    fun newPoll(id: String) = "newpoll/$id"
}

@Composable
fun RootNav() {
    val loggedIn by AppGraph.session.isLoggedInFlow.collectAsState(initial = null)
    when (loggedIn) {
        null -> com.talksy.app.ui.components.LoadingBox()
        false -> LoginScreen()
        true -> MainShell()
    }
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

@Composable
private fun MainShell() {
    val nav = rememberNavController()
    val chatsVm: ChatsViewModel = viewModel()
    val statusVm: StatusViewModel = viewModel() // created here so statuses load in the background at app start
    val chatsState by chatsVm.state.collectAsState()

    // Connect the realtime socket on every cold start (it used to connect only right after login).
    LaunchedEffect(Unit) {
        AppGraph.session.load()
        AppGraph.session.accessToken?.let { AppGraph.socket.connect(it) }
    }

    val tabs = listOf(
        Tab(Routes.CHATS, "Chats", Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
        Tab(Routes.STATUS, "Status", Icons.Outlined.DonutLarge, Icons.Filled.DonutLarge),
        Tab(Routes.CALLS, "Calls", Icons.Outlined.Call, Icons.Filled.Call),
        Tab(Routes.PROFILE, "You", Icons.Outlined.Person, Icons.Filled.Person),
    )
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val unread = chatsState.chats.sumOf { it.unreadCount }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (current in tabs.map { it.route }) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        val selected = current == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(Routes.CHATS) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (tab.route == Routes.CHATS && unread > 0) {
                                    BadgedBox(badge = { Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                                        Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label)
                                    }
                                } else {
                                    Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.CHATS,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { slideInHorizontally(initialOffsetX = { it / 8 }) + fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it / 8 }) + fadeOut() },
        ) {
            composable(Routes.CHATS) { ChatsScreen(nav, chatsVm) }
            composable(Routes.STATUS) { StatusScreen(statusVm) }
            composable(Routes.CALLS) { CallsScreen() }
            composable(Routes.PROFILE) { ProfileScreen(nav) }
            composable(Routes.EDIT_PROFILE) { EditProfileScreen(nav) }
            composable(Routes.BLOCKED) { BlockedScreen(nav) }
            composable(Routes.NEW_CHAT) { com.talksy.app.ui.screens.chats.NewChatScreen(nav) }
            composable(Routes.NEW_GROUP) { NewGroupScreen(nav) }
            composable(
                Routes.CHAT,
                arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
            ) { entry ->
                ChatScreen(nav, entry.arguments?.getString("chatId").orEmpty())
            }
            composable(
                Routes.INFO,
                arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
            ) { entry ->
                ChatInfoScreen(nav, entry.arguments?.getString("chatId").orEmpty())
            }
            composable(
                Routes.NEW_POLL,
                arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
            ) { entry ->
                CreatePollScreen(nav, entry.arguments?.getString("chatId").orEmpty())
            }
        }
    }
    CallOverlay()
    }
}
