package com.talksy.app.ui.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.talksy.app.data.remote.dto.ChatDto
import com.talksy.app.ui.components.Avatar
import com.talksy.app.ui.components.EmptyState
import com.talksy.app.ui.components.LoadingBox
import com.talksy.app.ui.components.UnreadBadge
import com.talksy.app.ui.nav.Routes
import com.talksy.app.util.Time

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(nav: NavController, vm: ChatsViewModel) {
    val state by vm.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(0) } // 0 all, 1 unread, 2 groups
    var fabOpen by remember { mutableStateOf(false) }

    // Refresh whenever we come back to this screen.
    LaunchedEffect(Unit) { vm.refresh(silent = true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh(silent = true)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val unreadChats = state.chats.count { it.unreadCount > 0 }
    val visible = state.chats.filter { chat ->
        val title = chat.title.ifBlank { chat.peer?.name ?: chat.name }
        val matches = query.isBlank() || title.contains(query.trim(), ignoreCase = true)
        val passes = when (filter) {
            1 -> chat.unreadCount > 0
            2 -> chat.type == "group"
            else -> true
        }
        matches && passes
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // ---- top bar
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (searching) {
                    IconButton(onClick = { searching = false; query = "" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                    }
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search chats") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                            }
                        },
                    )
                } else {
                    Text(
                        "Talksy",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    IconButton(onClick = { searching = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                }
            }

            // ---- filter chips
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterPill("All", filter == 0) { filter = 0 }
                FilterPill(if (unreadChats > 0) "Unread $unreadChats" else "Unread", filter == 1) { filter = 1 }
                FilterPill("Groups", filter == 2) { filter = 2 }
            }
            Spacer(Modifier.height(4.dp))

            // ---- list
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> LoadingBox()
                    visible.isEmpty() -> EmptyState(
                        title = if (state.chats.isEmpty()) "No chats yet" else "Nothing here",
                        subtitle = if (state.chats.isEmpty()) "Tap the pencil button to start a conversation."
                        else "No chats match your search or filter.",
                        icon = Icons.Filled.ChatBubble,
                    )
                    else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(visible, key = { it.id }) { chat ->
                            val peerId = chat.peer?.id
                            val online = peerId?.let { state.presence[it] ?: chat.peer?.isOnline } ?: false
                            ChatRow(chat, state.myId, online) { nav.navigate(Routes.chat(chat.id)) }
                        }
                    }
                }
            }
        }

        // ---- speed-dial FAB
        if (fabOpen) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { fabOpen = false },
            )
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (fabOpen) {
                ExtendedFloatingActionButton(
                    onClick = { fabOpen = false; nav.navigate(Routes.NEW_GROUP) },
                    icon = { Icon(Icons.Filled.GroupAdd, contentDescription = null) },
                    text = { Text("New group") },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
                ExtendedFloatingActionButton(
                    onClick = { fabOpen = false; nav.navigate(Routes.NEW_CHAT) },
                    icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    text = { Text("New chat") },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            }
            FloatingActionButton(
                onClick = { fabOpen = !fabOpen },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(if (fabOpen) Icons.Filled.Close else Icons.Filled.Edit, contentDescription = "New")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ChatRow(chat: ChatDto, myId: String, online: Boolean, onClick: () -> Unit) {
    val isGroup = chat.type == "group"
    val title = chat.title.ifBlank { chat.peer?.name ?: chat.name.ifBlank { "Chat" } }
    val picture = if (isGroup) chat.avatar else chat.peer?.picture
    val last = chat.lastMessage
    val unread = chat.unreadCount

    val preview = when {
        last == null -> if (isGroup) "Group created" else "Say hi \uD83D\uDC4B"
        else -> {
            val body = when {
                last.text.isNotBlank() -> last.text
                last.imageUrl.isNotBlank() -> "\uD83D\uDCF7 Photo"
                last.kind == "poll" -> "\uD83D\uDCCA Poll"
                else -> ""
            }
            val prefix = when {
                last.sender.id == myId -> "You: "
                isGroup -> last.sender.name.substringBefore(' ') + ": "
                else -> ""
            }
            prefix + body
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(title, picture, size = 56.dp, online = online && !isGroup, icon = if (isGroup && picture.isNullOrBlank()) Icons.Filled.Group else null)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    Time.listLabel(last?.createdAt ?: chat.lastMessageAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unread > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (unread > 0) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    UnreadBadge(unread)
                }
            }
        }
    }
}
