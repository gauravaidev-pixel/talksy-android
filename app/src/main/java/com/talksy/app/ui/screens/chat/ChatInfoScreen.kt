package com.talksy.app.ui.screens.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talksy.app.AppGraph
import com.talksy.app.data.remote.dto.ChatDto
import com.talksy.app.data.remote.dto.UserBrief
import com.talksy.app.data.remote.dto.UserDto
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
    val uploading: Boolean = false,
    val newAvatar: String? = null,
    val inviteCode: String? = null,
    val searchResults: List<UserDto> = emptyList(),
)

class ChatInfoViewModel : ViewModel() {
    private val _state = MutableStateFlow(ChatInfoState())
    val state: StateFlow<ChatInfoState> = _state
    private var chatId = ""

    fun load(id: String) {
        chatId = id
        viewModelScope.launch {
            val myId = AppGraph.session.userIdFlow.first() ?: ""
            val chat = AppGraph.chats.get(id).getOrNull()?.chat
            val bio = chat?.peer?.id?.let { AppGraph.users.get(it).getOrNull()?.user?.bio }.orEmpty()
            _state.update { it.copy(loading = false, chat = chat, bio = bio, myId = myId) }
        }
    }

    private fun reload() {
        viewModelScope.launch {
            AppGraph.chats.get(chatId).onSuccess { res ->
                AppGraph.chatCache[chatId] = res.chat
                _state.update { it.copy(chat = res.chat) }
            }
        }
    }

    private fun fail(e: Throwable) = _state.update { it.copy(message = e.message) }

    fun leave() {
        viewModelScope.launch {
            AppGraph.chats.leave(chatId, _state.value.myId)
                .onSuccess { _state.update { it.copy(left = true) } }
                .onFailure { e -> fail(e) }
        }
    }

    fun deleteGroup() {
        viewModelScope.launch {
            AppGraph.chats.delete(chatId)
                .onSuccess { _state.update { it.copy(left = true) } }
                .onFailure { e -> fail(e) }
        }
    }

    fun block(userId: String) {
        viewModelScope.launch {
            AppGraph.users.block(userId)
                .onSuccess { _state.update { it.copy(message = "Block list updated") } }
                .onFailure { e -> fail(e) }
        }
    }

    fun uploadAvatar(uri: Uri) {
        _state.update { it.copy(uploading = true) }
        viewModelScope.launch {
            AppGraph.uploads.image(uri)
                .onSuccess { url -> _state.update { it.copy(uploading = false, newAvatar = url) } }
                .onFailure { e -> _state.update { it.copy(uploading = false, message = e.message) } }
        }
    }

    fun saveGroup(name: String, description: String) {
        val avatar = _state.value.newAvatar
        viewModelScope.launch {
            AppGraph.chats.edit(chatId, name.trim().ifBlank { null }, description.trim(), avatar)
                .onSuccess { res ->
                    AppGraph.chatCache[chatId] = res.chat
                    _state.update { it.copy(chat = res.chat, newAvatar = null, message = "Group updated") }
                }
                .onFailure { e -> fail(e) }
        }
    }

    fun clearNewAvatar() = _state.update { it.copy(newAvatar = null) }

    fun searchPeople(q: String) {
        if (q.trim().length < 2) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            AppGraph.users.search(q.trim()).onSuccess { res ->
                val existing = _state.value.chat?.members?.map { it.id }.orEmpty().toSet()
                _state.update { it.copy(searchResults = res.users.filter { u -> u.id !in existing }) }
            }
        }
    }

    fun addMember(userId: String) {
        viewModelScope.launch {
            AppGraph.chats.addMembers(chatId, listOf(userId))
                .onSuccess {
                    _state.update { it.copy(message = "Member added", searchResults = emptyList()) }
                    reload()
                }
                .onFailure { e -> fail(e) }
        }
    }

    fun setRole(userId: String, role: String) {
        viewModelScope.launch {
            AppGraph.chats.setRole(chatId, userId, role)
                .onSuccess { reload() }
                .onFailure { e -> fail(e) }
        }
    }

    fun removeMember(userId: String) {
        viewModelScope.launch {
            AppGraph.chats.leave(chatId, userId)
                .onSuccess { _state.update { it.copy(message = "Member removed") }; reload() }
                .onFailure { e -> fail(e) }
        }
    }

    fun createInvite() {
        viewModelScope.launch {
            AppGraph.chats.invite(chatId)
                .onSuccess { res -> _state.update { it.copy(inviteCode = res.code) } }
                .onFailure { e -> fail(e) }
        }
    }

    fun dismissInvite() = _state.update { it.copy(inviteCode = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(nav: NavController, chatId: String, vm: ChatInfoViewModel = viewModel()) {
    LaunchedEffect(chatId) { vm.load(chatId) }
    val state by vm.state.collectAsState()
    val chat = state.chat
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var memberMenu by remember { mutableStateOf<UserBrief?>(null) }
    var confirmRemove by remember { mutableStateOf<UserBrief?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) vm.uploadAvatar(uri) }

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
                title = { Text(when (chat?.type) { "group" -> "Group info"; "saved" -> "Saved Messages"; else -> "Contact info" }) },
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
        val isSaved = chat.type == "saved"
        val isAdmin = chat.myRole == "owner" || chat.myRole == "admin"
        val isOwner = chat.myRole == "owner"
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
                        online = !isGroup && !isSaved && (chat.peer?.isOnline == true),
                        icon = when {
                            isSaved -> Icons.Filled.Bookmark
                            isGroup && picture.isNullOrBlank() -> Icons.Filled.Group
                            else -> null
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            isSaved -> "Notes, links and photos just for you"
                            isGroup -> "${chat.memberCount} members"
                            else -> Time.lastSeen(chat.peer?.isOnline == true, chat.peer?.lastSeen)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (!isGroup && !isSaved && chat.peer?.isOnline == true) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isGroup && isAdmin) {
                        Spacer(Modifier.height(14.dp))
                        FilledTonalButton(onClick = { showEdit = true }) {
                            Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Edit group")
                        }
                    }
                }
            }

            if (isGroup) {
                if (chat.description.isNotBlank()) {
                    item {
                        SectionLabel("About")
                        SectionCard { Text(chat.description, style = MaterialTheme.typography.bodyLarge) }
                    }
                }
                if (isAdmin) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.PersonAdd, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Add member")
                            }
                            OutlinedButton(onClick = { vm.createInvite() }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Link, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Invite code")
                            }
                        }
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
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(
                                    if (member.role == "owner") "Owner" else "Admin",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                )
                            }
                        }
                        // admins can manage others (owners can't be touched; only the owner can manage admins)
                        val canManage = isAdmin && member.id != state.myId && member.role != "owner" &&
                            (isOwner || member.role != "admin")
                        if (canManage) {
                            Box {
                                IconButton(onClick = { memberMenu = member }) { Icon(Icons.Filled.MoreVert, contentDescription = "Manage") }
                                DropdownMenu(expanded = memberMenu?.id == member.id, onDismissRequest = { memberMenu = null }) {
                                    if (isOwner) {
                                        DropdownMenuItem(
                                            text = { Text(if (member.role == "admin") "Remove admin" else "Make admin") },
                                            onClick = { vm.setRole(member.id, if (member.role == "admin") "member" else "admin"); memberMenu = null },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Remove from group", color = MaterialTheme.colorScheme.error) },
                                        onClick = { confirmRemove = member; memberMenu = null },
                                    )
                                }
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
                    if (isOwner) {
                        TextButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        ) {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("Delete group for everyone", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            } else if (!isSaved) {
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

    // ---------------------------------------------------------------- dialogs

    if (showEdit && chat != null) {
        var name by remember(chat.id) { mutableStateOf(chat.name) }
        var desc by remember(chat.id) { mutableStateOf(chat.description) }
        AlertDialog(
            onDismissRequest = { showEdit = false; vm.clearNewAvatar() },
            title = { Text("Edit group") },
            text = {
                Column {
                    Box(Modifier.align(Alignment.CenterHorizontally).clickable { picker.launch("image/*") }) {
                        Avatar(name.ifBlank { "Group" }, state.newAvatar ?: chat.avatar, size = 84.dp, icon = if ((state.newAvatar ?: chat.avatar).isNullOrBlank()) Icons.Filled.Group else null)
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.uploading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            else Icon(Icons.Filled.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, maxLines = 3)
                }
            },
            confirmButton = {
                TextButton(enabled = !state.uploading && name.isNotBlank(), onClick = { vm.saveGroup(name, desc); showEdit = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEdit = false; vm.clearNewAvatar() }) { Text("Cancel") } },
        )
    }

    if (showAdd) {
        var q by remember { mutableStateOf("") }
        LaunchedEffect(q) { kotlinx.coroutines.delay(300); vm.searchPeople(q) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add member") },
            text = {
                Column {
                    OutlinedTextField(value = q, onValueChange = { q = it }, placeholder = { Text("Search name or @username") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(state.searchResults, key = { it.id }) { u ->
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.addMember(u.id); showAdd = false }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(u.name, u.picture, size = 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(u.name, style = MaterialTheme.typography.titleSmall)
                                    Text("@${u.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Close") } },
        )
    }

    state.inviteCode?.let { code ->
        AlertDialog(
            onDismissRequest = { vm.dismissInvite() },
            title = { Text("Invite code") },
            text = {
                Column {
                    Text(
                        "Share this code. People can join from  You \u2192 Join with invite code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(code, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)); vm.dismissInvite() }) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
            },
            dismissButton = { TextButton(onClick = { vm.dismissInvite() }) { Text("Close") } },
        )
    }

    confirmRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove ${member.name}?") },
            text = { Text("They will no longer receive messages from this group.") },
            confirmButton = {
                TextButton(onClick = { vm.removeMember(member.id); confirmRemove = null }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave group?") },
            text = { Text("You will stop receiving messages from this group.") },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; vm.leave() }) { Text("Leave", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete group?") },
            text = { Text("The group and all its messages are deleted for every member. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteGroup() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
