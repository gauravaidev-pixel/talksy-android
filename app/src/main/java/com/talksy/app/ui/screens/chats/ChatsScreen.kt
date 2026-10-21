package com.talksy.app.ui.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PushPin
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
import com.talksy.app.data.remote.dto.UserDto
import kotlinx.coroutines.delay
import com.talksy.app.ui.components.Avatar
import com.talksy.app.ui.components.EmptyState
import com.talksy.app.ui.components.LoadingBox
import com.talksy.app.ui.components.UnreadBadge
import com.talksy.app.ui.nav.Routes
import com.talksy.app.util.Time

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatsScreen(nav: NavController, vm: ChatsViewModel) {
    val state by vm.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(0) } // 0 all, 1 unread, 2 groups
    var fabOpen by remember { mutableStateOf(false) }
    var menuChat by remember { mutableStateOf<ChatDto?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, ChatDto>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); vm.consumeError() } }

    // Refresh whenever we come back to this screen (the ON_RESUME observer below also fires on first composition).
    val openChat: (String) -> Unit = remember(nav) { { id -> nav.navigate(Routes.chat(id)) } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh(silent = true)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val showPeople = query.trim().length >= 2
    LaunchedEffect(query) {
        if (showPeople) { delay(300); vm.searchPeople(query) } else vm.clearPeople()
    }
    val existingPeerIds = state.chats.mapNotNull { it.peer?.id }.toSet()
    val people: List<UserDto> = if (showPeople) state.people.filter { it.id !in existingPeerIds } else emptyList()

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
                        placeholder = { Text("Search chats and people") },
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
                    state.loading -> com.talksy.app.ui.components.SkeletonList()
                    visible.isEmpty() && people.isEmpty() -> EmptyState(
                        title = if (showPeople) "No results" else if (state.chats.isEmpty()) "No chats yet" else "Nothing here",
                        subtitle = if (showPeople) "Nobody on Talksy matches \"${query.trim()}\"."
                        else if (state.chats.isEmpty()) "Tap the pencil button and search for someone to chat with."
                        else "No chats match your filter.",
                        icon = Icons.Filled.ChatBubble,
                    )
                    else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                        if (query.isBlank() && filter == 0 && state.chats.none { it.type == "saved" }) {
                            item(key = "saved-virtual") {
                                SavedRow { vm.openSaved { id -> nav.navigate(Routes.chat(id)) } }
                            }
                        }
                        items(visible, key = { it.id }) { chat ->
                            val peerId = chat.peer?.id
                            val online = peerId?.let { state.presence[it] ?: chat.peer?.isOnline } ?: false
                            Box(Modifier.animateItemPlacement()) {
                                ChatRow(
                                    chat, state.myId, online,
                                    onClick = { openChat(chat.id) },
                                    onLongClick = { menuChat = chat },
                                )
                                DropdownMenu(expanded = menuChat?.id == chat.id, onDismissRequest = { menuChat = null }) {
                                    DropdownMenuItem(
                                        text = { Text(if (chat.isPinned) "Unpin" else "Pin to top") },
                                        leadingIcon = { Icon(Icons.Filled.PushPin, null) },
                                        onClick = { vm.togglePin(chat); menuChat = null },
                                    )
                                    if (chat.isMuted) {
                                        DropdownMenuItem(
                                            text = { Text("Unmute") },
                                            leadingIcon = { Icon(Icons.Filled.NotificationsOff, null) },
                                            onClick = { vm.mute(chat, 0); menuChat = null },
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text("Mute for 8 hours") },
                                            leadingIcon = { Icon(Icons.Filled.NotificationsOff, null) },
                                            onClick = { vm.mute(chat, 8); menuChat = null },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Mute for 1 week") },
                                            leadingIcon = { Icon(Icons.Filled.NotificationsOff, null) },
                                            onClick = { vm.mute(chat, 168); menuChat = null },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Clear history") },
                                        leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) },
                                        onClick = { confirm = "clear" to chat; menuChat = null },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete chat", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = { confirm = "delete" to chat; menuChat = null },
                                    )
                                }
                            }
                        }
                        if (people.isNotEmpty()) {
                            item(key = "people-header") {
                                Text(
                                    "PEOPLE ON TALKSY",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 6.dp),
                                )
                            }
                            items(people, key = { "p-" + it.id }) { user ->
                                PersonRow(user) {
                                    query = ""; searching = false
                                    vm.openDirect(user.id) { id -> nav.navigate(Routes.chat(id)) }
                                }
                            }
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
                    onClick = { fabOpen = false; vm.openSaved { id -> nav.navigate(Routes.chat(id)) } },
                    icon = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
                    text = { Text("Saved messages") },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
                ExtendedFloatingActionButton(
                    onClick = { fabOpen = false; nav.navigate(Routes.NEW_GROUP) },
                    icon = { Icon(Icons.Filled.GroupAdd, contentDescription = null) },
                    text = { Text("New group") },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
                ExtendedFloatingActionButton(
                    onClick = { fabOpen = false; searching = true },
                    icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    text = { Text("Find people") },
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
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }

    confirm?.let { (kind, chat) ->
        val deleting = kind == "delete"
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (deleting) "Delete chat?" else "Clear history?") },
            text = {
                Text(
                    if (deleting) "The chat disappears from your list and your history is cleared. It comes back if someone sends a new message."
                    else "All current messages are hidden for you. Other people keep their copy.",
                )
            },
            confirmButton = {
                TextButton(onClick = { if (deleting) vm.hide(chat) else vm.clear(chat); confirm = null }) {
                    Text(if (deleting) "Delete" else "Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PersonRow(user: UserDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(user.name, user.picture, size = 52.dp, online = user.isOnline)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(user.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "@" + user.username + if (user.bio.isNotBlank()) " \u2022 " + user.bio else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SavedRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar("Saved", null, size = 56.dp, icon = Icons.Filled.Bookmark)
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Saved Messages", style = MaterialTheme.typography.titleMedium)
            Text(
                "Notes, links and photos just for you",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(chat: ChatDto, myId: String, online: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val isGroup = chat.type == "group"
    val isSaved = chat.type == "saved"
    val title = chat.title.ifBlank { chat.peer?.name ?: chat.name.ifBlank { "Chat" } }
    val picture = if (isGroup) chat.avatar else chat.peer?.picture
    val last = chat.lastMessage
    val unread = chat.unreadCount

    val preview = when {
        last == null -> if (isGroup) "Group created" else if (isSaved) "Notes, links and photos just for you" else "Say hi \uD83D\uDC4B"
        else -> {
            val body = when {
                last.kind == "voice" -> "\uD83C\uDFA4 Voice message"
                last.text.isNotBlank() -> last.text
                last.imageUrl.isNotBlank() -> "\uD83D\uDCF7 Photo"
                last.kind == "poll" -> "\uD83D\uDCCA Poll"
                else -> ""
            }
            val prefix = when {
                last.sender.id == myId -> if (isSaved) "" else "You: "
                isGroup -> last.sender.name.substringBefore(' ') + ": "
                else -> ""
            }
            prefix + body
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            title, picture, size = 56.dp, online = online && !isGroup && !isSaved,
            icon = when {
                isSaved -> Icons.Filled.Bookmark
                isGroup && picture.isNullOrBlank() -> Icons.Filled.Group
                else -> null
            },
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (chat.isMuted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.NotificationsOff, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    Time.listLabel(last?.createdAt ?: chat.lastMessageAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unread > 0 && !chat.isMuted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                if (chat.isPinned && unread == 0) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.PushPin, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    UnreadBadge(unread, muted = chat.isMuted)
                }
            }
        }
    }
}
