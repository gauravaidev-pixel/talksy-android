package com.talksy.app.ui.screens.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talksy.app.data.remote.dto.ChatDto
import com.talksy.app.ui.components.Avatar
import com.talksy.app.ui.components.EmptyState
import com.talksy.app.ui.components.LoadingBox
import com.talksy.app.ui.nav.Routes
import com.talksy.app.util.Time

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(nav: NavController, vm: ChatsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Talksy", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = "New chat")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("New chat") },
                            leadingIcon = { Icon(Icons.Filled.PersonAdd, null) },
                            onClick = { menu = false; nav.navigate(Routes.NEW_CHAT) },
                        )
                        DropdownMenuItem(
                            text = { Text("New group") },
                            leadingIcon = { Icon(Icons.Filled.GroupAdd, null) },
                            onClick = { menu = false; nav.navigate(Routes.NEW_GROUP) },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingBox()
                state.chats.isEmpty() -> EmptyState(
                    "No chats yet",
                    "Start a conversation or create a group to get going.",
                    Icons.Filled.Chat,
                )
                else -> LazyColumn {
                    items(state.chats, key = { it.id }) { chat ->
                        ChatRow(chat) { nav.navigate(Routes.chat(chat.id)) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatDto, onClick: () -> Unit) {
    val title = chat.title.ifBlank { chat.peer?.name ?: "Chat" }
    val picture = chat.peer?.picture ?: chat.avatar
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(title, picture, size = 52.dp, online = chat.peer?.let { false } ?: false)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Time.short(chat.lastMessage?.createdAt ?: chat.lastMessageAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                chat.lastMessage?.text?.ifBlank { if (chat.lastMessage?.imageUrl?.isNotBlank() == true) "Photo" else "" } ?: "Tap to open",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
