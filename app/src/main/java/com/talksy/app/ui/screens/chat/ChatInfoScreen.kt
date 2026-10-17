package com.talksy.app.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talksy.app.AppGraph
import com.talksy.app.data.remote.dto.ChatDto
import com.talksy.app.ui.components.Avatar
import com.talksy.app.ui.components.LoadingBox
import com.talksy.app.ui.components.SectionCard
import com.talksy.app.ui.components.SectionLabel
import com.talksy.app.ui.nav.Routes
import com.talksy.app.util.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatInfoState(
    val loading: Boolean = true,
    val chat: ChatDto? = null,
    val bio: String = "",
    val myId: String = "",
    val message: String? = null,
    val left: Boolean = false,
)

class ChatInfoViewModel : ViewModel() {
    private val _state = MutableStateFlow(ChatInfoState())
    val state: StateFlow<ChatInfoState> = _state

    fun load(chatId: String) {
        viewModelScope.launch {
            val myId = AppGraph.session.userIdFlow.first() ?: ""
            val chat = AppGraph.chats.get(chatId).getOrNull()?.chat
            val bio = chat?.peer?.id?.let { AppGraph.users.get(it).getOrNull()?.user?.bio }.orEmpty()
            _state.update { it.copy(loading = false, chat = chat, bio = bio, myId = myId) }
        }
    }

    fun leave(chatId: String) {
        val myId = _state.value.myId
        viewModelScope.launch {
            AppGraph.chats.leave(chatId, myId)
                .onSuccess { _state.update { it.copy(left = true) } }
                .onFailure { e -> _state.update { it.copy(message = e.message) } }
        }
    }

    fun block(userId: String) {
        viewModelScope.launch {
            AppGraph.users.block(userId)
                .onSuccess { _state.update { it.copy(message = "Block list updated") } }
                .onFailure { e -> _state.update { it.copy(message = e.message) } }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(nav: NavController, chatId: String, vm: ChatInfoViewModel = viewModel()) {
    LaunchedEffect(chatId) { vm.load(chatId) }
    val state by vm.state.collectAsState()
    val chat = state.chat
    val snackbar = remember { SnackbarHostState() }
    var confirmLeave by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(state.left) {
        if (state.left) nav.popBackStack(Routes.CHATS, inclusive = false)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (chat?.type == "group") "Group info" else "Contact info") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (state.loading || chat == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val isGroup = chat.type == "group"
        val title = chat.title.ifBlank { chat.peer?.name ?: "Chat" }
        val picture = if (isGroup) chat.avatar else chat.peer?.picture

        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(
                        title, picture, size = 112.dp,
                        online = !isGroup && (chat.peer?.isOnline == true),
                        icon = if (isGroup && picture.isNullOrBlank()) Icons.Filled.Group else null,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isGroup) "${chat.memberCount} members"
                        else Time.lastSeen(chat.peer?.isOnline == true, chat.peer?.lastSeen),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (!isGroup && chat.peer?.isOnline == true) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isGroup) {
                if (chat.description.isNotBlank()) {
                    item {
                        SectionLabel("About")
                        SectionCard { Text(chat.description, style = MaterialTheme.typography.bodyLarge) }
                    }
                }
                item { SectionLabel("${chat.members.size} members") }
                items(chat.members, key = { it.id }) { member ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(member.name, member.picture, size = 46.dp, online = member.isOnline)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (member.id == state.myId) "${member.name} (you)" else member.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "@${member.username}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (member.role == "owner" || member.role == "admin") {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    if (member.role == "owner") "Owner" else "Admin",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = { confirmLeave = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Leave group", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            } else {
                item {
                    SectionLabel("About")
                    SectionCard {
                        Text(
                            state.bio.ifBlank { "No bio yet" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (state.bio.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("@${chat.peer?.username.orEmpty()}", color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = { chat.peer?.id?.let(vm::block) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    ) {
                        Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Block / unblock user", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave group?") },
            text = { Text("You will stop receiving messages from this group.") },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; vm.leave(chatId) }) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel") } },
        )
    }
}
