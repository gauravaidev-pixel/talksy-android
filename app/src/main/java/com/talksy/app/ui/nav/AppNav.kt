package com.talksy.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.talksy.app.AppGraph
import com.talksy.app.ui.screens.chat.ChatScreen
import com.talksy.app.ui.screens.chats.ChatsScreen
import com.talksy.app.ui.screens.groups.NewGroupScreen
import com.talksy.app.ui.screens.login.LoginScreen
import com.talksy.app.ui.screens.polls.CreatePollScreen
import com.talksy.app.ui.screens.profile.ProfileScreen
import com.talksy.app.ui.screens.settings.SettingsScreen
import com.talksy.app.ui.screens.status.StatusScreen

object Routes {
    const val LOGIN = "login"
    const val CHATS = "chats"
    const val STATUS = "status"
    const val PROFILE = "profile"
    const val CHAT = "chat/{chatId}"
    const val NEW_CHAT = "newchat"
    const val NEW_GROUP = "newgroup"
    const val NEW_POLL = "newpoll/{chatId}"
    const val SETTINGS = "settings"

    fun chat(id: String) = "chat/$id"
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

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun MainShell() {
    val nav = rememberNavController()
    val tabs = listOf(
        Tab(Routes.CHATS, "Chats", Icons.Filled.Chat),
        Tab(Routes.STATUS, "Status", Icons.Filled.DonutLarge),
        Tab(Routes.PROFILE, "Profile", Icons.Filled.Person),
    )
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (current in tabs.map { it.route }) {
                NavigationBar(tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(Routes.CHATS) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.CHATS,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.CHATS) { ChatsScreen(nav) }
            composable(Routes.STATUS) { StatusScreen() }
            composable(Routes.PROFILE) { ProfileScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.NEW_CHAT) { com.talksy.app.ui.screens.chats.NewChatScreen(nav) }
            composable(Routes.NEW_GROUP) { NewGroupScreen(nav) }
            composable(
                Routes.CHAT,
                arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
            ) { entry ->
                ChatScreen(nav, entry.arguments?.getString("chatId").orEmpty())
            }
            composable(
                Routes.NEW_POLL,
                arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
            ) { entry ->
                CreatePollScreen(nav, entry.arguments?.getString("chatId").orEmpty())
            }
        }
    }
}
