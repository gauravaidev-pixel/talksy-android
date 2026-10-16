package com.talksy.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import com.talksy.app.ui.theme.LocalChatColors

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

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun MainShell() {
    val nav = rememberNavController()
    val tabs = listOf(
        Tab(Routes.CHATS, "Chats", Icons.Filled.Chat),
        Tab(Routes.STATUS, "Status", Icons.Filled.DonutLarge),
        Tab(Routes.PROFILE, "Settings", Icons.Filled.Person),
    )
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val chat = LocalChatColors.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (current in tabs.map { it.route }) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tabs.forEach { tab ->
                            val on = current == tab.route
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable {
                                        nav.navigate(tab.route) {
                                            popUpTo(Routes.CHATS) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .width(56.dp)
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(if (on) chat.accent.copy(alpha = 0.20f) else androidx.compose.ui.graphics.Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        tab.icon, contentDescription = tab.label,
                                        tint = if (on) chat.accent else chat.t2,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    tab.label,
                                    color = if (on) chat.accent else chat.t2,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
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
