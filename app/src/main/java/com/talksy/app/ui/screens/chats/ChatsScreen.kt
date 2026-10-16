package com.talksy.app.ui.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talksy.app.data.remote.dto.ChatDto
import com.talksy.app.ui.components.Avatar
import com.talksy.app.ui.components.EmptyState
import com.talksy.app.ui.components.FilterChip
import com.talksy.app.ui.components.LoadingBox
import com.talksy.app.ui.components.SearchPill
import com.talksy.app.ui.nav.Routes
import com.talksy.app.ui.theme.LocalChatColors
import com.talksy.app.util.Time

@Composable
fun ChatsScreen(nav: NavController, vm: ChatsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val chat = LocalChatColors.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var menu by remember { mutableStateOf(false) }

    val visible = state.chats.filter { c ->
        val title = c.title.ifBlank { c.name.ifBlank { c.peer?.name ?: "" } }
        val matchesQuery = query.isBlank() || title.contains(query, true) ||
            (c.lastMessage?.text?.contains(query, true) ?: false)
        val matchesFilter = when (filter) {
            "Groups" -> c.type == "group"
            "Direct" -> c.type != "group"
            else -> true
        }
        matchesQuery && matchesFilter
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            // header
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Talksy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = chat.t2)
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "New", tint = chat.t2)
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
                }
            }

            SearchPill(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search chats and people",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("All", "Groups", "Direct").forEach { f ->
                    FilterChip(label = f, selected = filter == f) { filter = f }
                }
            }

            Box(Modifier.weight(1f)) {
                when {
                    state.loading -> LoadingBox()
                    visible.isEmpty() -> EmptyState(
                        "No chats yet",
                        "Start a conversation or create a group to get going.",
                        Icons.Filled.Chat,
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(visible, key = { it.id }) { c ->
                            ChatRow(c, chat.t2, chat.t3) { nav.navigate(Routes.chat(c.id)) }
                        }
                        item { Spacer(Modifier.height(90.dp)) }
                    }
                }
            }
        }

        // FAB
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp)
                .size(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(chat.accent)
                .clickable { nav.navigate(Routes.NEW_CHAT) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Edit, contentDescription = "New chat", tint = chat.onAccent, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun ChatRow(chatDto: ChatDto, t2: androidx.compose.ui.graphics.Color, t3: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val title = chatDto.title.ifBlank { chatDto.name.ifBlank { chatDto.peer?.name ?: "Chat" } }
    val picture = chatDto.peer?.picture ?: chatDto.avatar
    val last = chatDto.lastMessage
    val preview = when {
        last == null -> "Tap to open"
        last.text.isNotBlank() -> last.text
        last.imageUrl.isNotBlank() -> "\uD83D\uDCF7 Photo"
        else -> "Tap to open"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(title, picture, size = 54.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    Time.short(last?.createdAt ?: chatDto.lastMessageAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = t3,
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chatDto.type == "group") {
                    Text("\uD83D\uDC65 ", style = MaterialTheme.typography.bodyMedium, color = t2)
                }
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = t2,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
