package com.talksy.app.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.talksy.app.AppGraph
import com.talksy.app.data.remote.dto.MessageDto
import com.talksy.app.data.remote.dto.PollDto
import com.talksy.app.data.remote.dto.ReplyDto
import com.talksy.app.ui.components.Avatar
import com.talksy.app.ui.components.LoadingBox
import com.talksy.app.ui.components.senderColor
import com.talksy.app.ui.nav.Routes
import com.talksy.app.ui.theme.extras
import com.talksy.app.ui.screens.call.rememberCallLauncher
import com.talksy.app.util.Time
import com.talksy.app.util.VoicePlayer
import com.talksy.app.util.VoiceRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ------------------------------------------------------------------ UI models

private enum class SendState { SENT, PENDING, FAILED }

private data class UiMsg(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderPicture: String,
    val text: String,
    val imageUrl: String,
    val kind: String,
    val pollId: String?,
    val mine: Boolean,
    val time: String,
    val dayKey: Long,
    val reply: ReplyDto?,
    val reactions: Map<String, List<String>>,
    val edited: Boolean,
    val pinned: Boolean,
    val seen: Boolean,
    val send: SendState,
    val raw: MessageDto?,
)

private sealed interface ChatRow {
    data class Day(val dayKey: Long) : ChatRow
    data class Msg(val m: UiMsg, val first: Boolean, val last: Boolean) : ChatRow
}

private fun MessageDto.toUi(myId: String) = UiMsg(
    id = id, senderId = sender.id, senderName = sender.name, senderPicture = sender.picture,
    text = text, imageUrl = imageUrl, kind = kind, pollId = pollId, mine = sender.id == myId,
    time = Time.clock(createdAt), dayKey = Time.dayKey(createdAt), reply = replyTo,
    reactions = reactions, edited = isEdited, pinned = isPinned, seen = isSeen, send = SendState.SENT, raw = this,
)

private fun PendingMessage.toUi(myId: String) = UiMsg(
    id = localId, senderId = myId, senderName = "", senderPicture = "",
    text = text, imageUrl = imageUrl, kind = "text", pollId = null, mine = true,
    time = Time.nowClock(), dayKey = Time.todayKey(), reply = replyTo,
    reactions = emptyMap(), edited = false, pinned = false, seen = false,
    send = if (failed) SendState.FAILED else SendState.PENDING, raw = null,
)

/** Oldest-first list with date separators and "same sender" grouping flags. */
private fun buildRows(items: List<UiMsg>): List<ChatRow> {
    val out = ArrayList<ChatRow>(items.size + 8)
    var lastDay = Long.MIN_VALUE
    items.forEachIndexed { i, m ->
        if (m.dayKey != lastDay) {
            out.add(ChatRow.Day(m.dayKey))
            lastDay = m.dayKey
        }
        val prev = items.getOrNull(i - 1)
        val next = items.getOrNull(i + 1)
        val groupable = m.kind != "poll"
        val first = prev == null || prev.dayKey != m.dayKey || prev.senderId != m.senderId || !groupable || prev.kind == "poll"
        val last = next == null || next.dayKey != m.dayKey || next.senderId != m.senderId || !groupable || next.kind == "poll"
        out.add(ChatRow.Msg(m, first, last))
    }
    return out
}

private val QuickReactions = listOf("\u2764\uFE0F", "\uD83D\uDC4D", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDD25")

// ------------------------------------------------------------------ screen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(nav: NavController, chatId: String, vm: ChatViewModel = viewModel()) {
    LaunchedEffect(chatId) { vm.start(chatId) }
    val state by vm.state.collectAsState()
    val extras = MaterialTheme.extras

    var draft by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<UiMsg?>(null) }
    var confirmDelete by remember { mutableStateOf<MessageDto?>(null) }
    var viewerImage by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val startCall = rememberCallLauncher()

    // voice recording
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var recSeconds by remember { mutableIntStateOf(0) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recording = recorder.start()
    }
    val startRecording: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recording = recorder.start()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(recording) {
        recSeconds = 0
        while (recording) { delay(1000); recSeconds++ }
    }
    DisposableEffect(Unit) { onDispose { recorder.cancel(); VoicePlayer.stop() } }

    // search / dialogs
    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var forwardMsg by remember { mutableStateOf<MessageDto?>(null) }
    var reportMsg by remember { mutableStateOf<MessageDto?>(null) }
    var showDisappear by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(searchQuery) { delay(300); vm.search(searchQuery) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.attach(uri)
    }

    val chat = state.chat
    val isGroup = chat?.type == "group"
    val isSaved = chat?.type == "saved"
    val title = chat?.title?.ifBlank { null } ?: chat?.peer?.name ?: "Chat"
    val canModerate = chat?.myRole == "owner" || chat?.myRole == "admin"

    // rows (oldest -> newest), reversed because the list uses reverseLayout (newest at the bottom, auto-sticky)
    val rows = remember(state.messages, state.pending, state.myId) {
        val all = state.messages.map { it.toUi(state.myId) } + state.pending.map { it.toUi(state.myId) }
        buildRows(all).asReversed()
    }
    val newestId = (rows.firstOrNull { it is ChatRow.Msg } as? ChatRow.Msg)?.m?.id

    // stick to the newest message, or count unseen ones if the user scrolled up
    var newBelow by remember { mutableIntStateOf(0) }
    LaunchedEffect(newestId) {
        if (newestId == null) return@LaunchedEffect
        if (listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
            newBelow = 0
        } else {
            val newest = (rows.firstOrNull { it is ChatRow.Msg } as? ChatRow.Msg)?.m
            if (newest != null && !newest.mine) newBelow++ else listState.animateScrollToItem(0)
        }
    }
    val showJump by remember { derivedStateOf { listState.firstVisibleItemIndex > 4 } }
    LaunchedEffect(showJump) { if (!showJump) newBelow = 0 }

    // infinite scroll to older messages
    val nearTop by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearTop, rows.size) { if (nearTop && state.hasMore && !state.loadingMore) vm.loadOlder() }

    LaunchedEffect(state.toast) {
        state.toast?.let { snackbar.showSnackbar(it); vm.consumeToast() }
    }
    LaunchedEffect(state.editing?.id) { state.editing?.let { draft = it.text } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding()) {
        // ------------------------------------------------ header
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { nav.navigate(Routes.info(chatId)) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(
                        title,
                        if (isGroup) chat?.avatar else chat?.peer?.picture,
                        size = 42.dp,
                        online = !isGroup && !isSaved && state.peerOnline,
                        icon = when {
                            isSaved -> Icons.Filled.Bookmark
                            isGroup && chat?.avatar.isNullOrBlank() -> Icons.Filled.Group
                            else -> null
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        HeaderSubtitle(
                            typing = state.typing,
                            who = if (isGroup) state.typingName else null,
                            text = when {
                                isSaved -> "Notes, links and photos"
                                isGroup -> "${chat?.memberCount ?: 0} members"
                                else -> Time.lastSeen(state.peerOnline, state.peerLastSeen)
                            },
                            highlight = state.typing || (!isGroup && !isSaved && state.peerOnline),
                        )
                    }
                }
                val callPeer = chat?.takeIf { it.type == "direct" }?.peer
                if (callPeer != null) {
                    IconButton(onClick = { startCall(callPeer, false) }) {
                        Icon(Icons.Filled.Call, contentDescription = "Voice call")
                    }
                    IconButton(onClick = { startCall(callPeer, true) }) {
                        Icon(Icons.Filled.Videocam, contentDescription = "Video call")
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (isGroup) "Group info" else "Contact info") },
                            leadingIcon = { Icon(Icons.Filled.Info, null) },
                            onClick = { menu = false; nav.navigate(Routes.info(chatId)) },
                        )
                        DropdownMenuItem(
                            text = { Text("Create poll") },
                            leadingIcon = { Icon(Icons.Filled.Poll, null) },
                            onClick = { menu = false; nav.navigate(Routes.newPoll(chatId)) },
                        )
                        DropdownMenuItem(
                            text = { Text("Search") },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            onClick = { menu = false; searchMode = true },
                        )
                        if (!isSaved) {
                            DropdownMenuItem(
                                text = { Text(if (chat?.isMuted == true) "Unmute" else "Mute for 8 hours") },
                                leadingIcon = { Icon(Icons.Filled.NotificationsOff, null) },
                                onClick = { menu = false; vm.toggleMute() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (state.disappear == "off") "Disappearing messages" else "Disappearing: on") },
                            leadingIcon = { Icon(Icons.Filled.Timer, null) },
                            onClick = { menu = false; showDisappear = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear history") },
                            leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) },
                            onClick = { menu = false; confirmClear = true },
                        )
                    }
                }
            }
        }

        if (searchMode) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search in this chat") },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.extras.field,
                            unfocusedContainerColor = MaterialTheme.extras.field,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    IconButton(onClick = { searchMode = false; searchQuery = ""; vm.clearSearch() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close search")
                    }
                }
            }
        }

        // ------------------------------------------------ messages on the wallpaper
        Box(Modifier.weight(1f).fillMaxWidth().wallpaper(extras.wallpaperTop, extras.wallpaperBottom, extras.isDark)) {
            if (state.loading) {
                LoadingBox()
            } else if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.Black.copy(alpha = 0.3f)) {
                        Text(
                            "No messages yet \u2014 say hi \uD83D\uDC4B",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    items(
                        rows,
                        key = { if (it is ChatRow.Day) "day-${it.dayKey}" else (it as ChatRow.Msg).m.id },
                    ) { row ->
                        when (row) {
                            is ChatRow.Day -> DayChip(Time.dayLabel(row.dayKey))
                            is ChatRow.Msg -> {
                                val m = row.m
                                if (m.kind == "poll") {
                                    val poll = m.pollId?.let { state.polls[it] }
                                    if (poll != null) PollCard(poll) { vm.vote(poll.id, it) }
                                } else {
                                    MessageRow(
                                        item = row,
                                        isGroup = isGroup,
                                        onLongPress = { if (m.send == SendState.SENT) selected = m },
                                        onSwipeReply = { m.raw?.let(vm::startReply) },
                                        onImage = { viewerImage = it },
                                        onReact = { emoji -> m.raw?.let { vm.react(it, emoji) } },
                                        onRetry = { vm.retry(m.id) },
                                        onDiscard = { vm.discard(m.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (searchMode) {
                val results = state.searchResults
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    when {
                        results == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Type at least 2 letters", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No messages found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> LazyColumn {
                            items(results, key = { it.id }) { r ->
                                Column(
                                    Modifier.fillMaxWidth().clickable {
                                        val idx = rows.indexOfFirst { it is ChatRow.Msg && it.m.id == r.id }
                                        searchMode = false; searchQuery = ""; vm.clearSearch()
                                        scope.launch {
                                            if (idx >= 0) listState.scrollToItem(idx)
                                            else snackbar.showSnackbar("That message is older \u2013 scroll up to load it")
                                        }
                                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                                ) {
                                    Row {
                                        Text(r.sender.name, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        Text(Time.listLabel(r.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(r.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }

            // jump-to-latest button
            AnimatedVisibility(
                visible = showJump,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
            ) {
                BadgedBox(badge = { if (newBelow > 0) Badge { Text(newBelow.toString()) } }) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0); newBelow = 0 } },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Latest")
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }

        // ------------------------------------------------ composer
        Composer(
            draft = draft,
            onDraft = { draft = it; if (it.isNotBlank()) vm.notifyTyping() },
            replyTo = state.replyTo,
            editing = state.editing,
            attachment = state.attachment,
            uploading = state.uploading,
            onCancelCompose = { vm.cancelCompose(); if (state.editing != null) draft = "" },
            onClearAttachment = vm::clearAttachment,
            onAttach = { picker.launch("image/*") },
            onSend = { vm.send(draft); draft = "" },
            recording = recording,
            recSeconds = recSeconds,
            sendingVoice = state.sendingVoice,
            onMic = startRecording,
            onCancelRec = { recorder.cancel(); recording = false },
            onSendRec = {
                val result = recorder.stop()
                recording = false
                if (result != null) vm.sendVoice(result.first, result.second)
            },
        )
    }

    // ------------------------------------------------ message actions sheet
    selected?.let { m ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    QuickReactions.forEach { emoji ->
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { m.raw?.let { vm.react(it, emoji) }; selected = null },
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 22.sp) }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)
                SheetAction(Icons.AutoMirrored.Filled.Reply, "Reply") { m.raw?.let(vm::startReply); selected = null }
                SheetAction(Icons.Filled.Share, "Forward") { forwardMsg = m.raw; selected = null }
                if (m.text.isNotBlank()) {
                    SheetAction(Icons.Filled.ContentCopy, "Copy text") {
                        clipboard.setText(AnnotatedString(m.text)); selected = null
                    }
                }
                if (m.mine && m.text.isNotBlank()) {
                    SheetAction(Icons.Filled.Edit, "Edit") { m.raw?.let(vm::startEdit); selected = null }
                }
                SheetAction(Icons.Filled.PushPin, if (m.pinned) "Unpin" else "Pin") { m.raw?.let(vm::pin); selected = null }
                if (!m.mine) {
                    SheetAction(Icons.Filled.Flag, "Report", destructive = true) { reportMsg = m.raw; selected = null }
                }
                if (m.mine || canModerate) {
                    SheetAction(Icons.Filled.Delete, "Delete", destructive = true) {
                        confirmDelete = m.raw; selected = null
                    }
                }
            }
        }
    }

    confirmDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete message?") },
            text = { Text("This removes the message for everyone in the chat.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(m); confirmDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    forwardMsg?.let { m ->
        val targets = remember(chatId) {
            AppGraph.chatCache.values.filter { it.id != chatId }.sortedByDescending { it.lastMessageAt ?: "" }
        }
        AlertDialog(
            onDismissRequest = { forwardMsg = null },
            title = { Text("Forward to") },
            text = {
                if (targets.isEmpty()) {
                    Text("Open the Chats tab once so your chats can be listed here.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(targets, key = { it.id }) { c ->
                            val name = c.title.ifBlank { c.peer?.name ?: c.name }
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.forward(m, listOf(c.id)); forwardMsg = null }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(
                                    name, if (c.type == "group") c.avatar else c.peer?.picture, size = 40.dp,
                                    icon = when {
                                        c.type == "saved" -> Icons.Filled.Bookmark
                                        c.type == "group" && c.avatar.isBlank() -> Icons.Filled.Group
                                        else -> null
                                    },
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { forwardMsg = null }) { Text("Cancel") } },
        )
    }

    reportMsg?.let { m ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reportMsg = null },
            title = { Text("Report message") },
            text = {
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    placeholder = { Text("What is wrong with it?") }, maxLines = 3,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.report(m, reason.ifBlank { "No reason" }); reportMsg = null }) { Text("Report") }
            },
            dismissButton = { TextButton(onClick = { reportMsg = null }) { Text("Cancel") } },
        )
    }

    if (showDisappear) {
        val options = listOf("off" to "Off", "1day" to "24 hours", "1week" to "7 days", "1month" to "30 days")
        AlertDialog(
            onDismissRequest = { showDisappear = false },
            title = { Text("Disappearing messages") },
            text = {
                Column {
                    Text(
                        "New messages you send in this chat will be deleted after the chosen time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    options.forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.setDisappear(value); showDisappear = false },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = state.disappear == value, onClick = { vm.setDisappear(value); showDisappear = false })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showDisappear = false }) { Text("Close") } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("All current messages are hidden for you. Other people keep their copy.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; vm.clearHistory() }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }

    viewerImage?.let { url ->
        Dialog(onDismissRequest = { viewerImage = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                IconButton(onClick = { viewerImage = null }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ small helpers

@Composable
private fun HeaderSubtitle(typing: Boolean, text: String, highlight: Boolean, who: String? = null) {
    var dots by remember { mutableIntStateOf(0) }
    LaunchedEffect(typing) {
        while (typing) {
            delay(400)
            dots = (dots + 1) % 4
        }
        dots = 0
    }
    Text(
        if (typing) (if (who != null) "$who is typing" else "typing") + ".".repeat(dots) else text,
        style = MaterialTheme.typography.bodySmall,
        color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/** Subtle gradient + faint doodle pattern, like Telegram's default wallpaper. */
private fun Modifier.wallpaper(top: Color, bottom: Color, dark: Boolean): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(top, bottom)))
    val step = 64.dp.toPx()
    val ink = if (dark) Color.White.copy(alpha = 0.035f) else Color.White.copy(alpha = 0.22f)
    var row = 0
    var y = step / 2
    while (y < size.height) {
        var x = if (row % 2 == 0) step / 2 else step
        while (x < size.width) {
            val k = ((x / step).toInt() + row) % 3
            when (k) {
                0 -> drawCircle(ink, radius = 4.dp.toPx(), center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()))
                1 -> {
                    val a = 4.dp.toPx()
                    drawLine(ink, Offset(x - a, y), Offset(x + a, y), strokeWidth = 1.6.dp.toPx())
                    drawLine(ink, Offset(x, y - a), Offset(x, y + a), strokeWidth = 1.6.dp.toPx())
                }
                else -> drawCircle(ink, radius = 1.8.dp.toPx(), center = Offset(x, y))
            }
            x += step
        }
        y += step * 0.75f
        row++
    }
}

@Composable
private fun DayChip(label: String) {
    if (label.isBlank()) return
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(14.dp), color = Color.Black.copy(alpha = 0.32f)) {
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Text(label, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}

// ------------------------------------------------------------------ message row + bubble

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    item: ChatRow.Msg,
    isGroup: Boolean,
    onLongPress: () -> Unit,
    onSwipeReply: () -> Unit,
    onImage: (String) -> Unit,
    onReact: (String) -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    val m = item.m
    var drag by remember { mutableFloatStateOf(0f) }
    val maxDrag = 96f

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = if (item.first) 7.dp else 1.5.dp)
            .offset { IntOffset(drag.roundToInt(), 0) }
            .pointerInput(m.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (drag > maxDrag * 0.7f && m.send == SendState.SENT) onSwipeReply()
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { change, delta ->
                    change.consume()
                    drag = (drag + delta).coerceIn(0f, maxDrag)
                }
            },
        horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!m.mine && isGroup) {
            Box(Modifier.width(36.dp), contentAlignment = Alignment.BottomStart) {
                if (item.last) Avatar(m.senderName, m.senderPicture, size = 30.dp)
            }
            Spacer(Modifier.width(2.dp))
        }
        Bubble(
            m = m,
            first = item.first,
            last = item.last,
            showSender = isGroup && !m.mine && item.first,
            onLongPress = onLongPress,
            onImage = onImage,
            onReact = onReact,
            onRetry = onRetry,
            onDiscard = onDiscard,
        )
    }
}

private fun Modifier.drawBubble(color: Color, mine: Boolean, first: Boolean, last: Boolean): Modifier = drawBehind {
    val r = 17.dp.toPx()
    val s = 5.dp.toPx()
    val w = size.width
    val h = size.height
    val tl = if (!mine && !first) s else r
    val tr = if (mine && !first) s else r
    val br = if (mine) (if (last) 2.dp.toPx() else s) else r
    val bl = if (!mine) (if (last) 2.dp.toPx() else s) else r
    val body = Path().apply {
        addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(tl), CornerRadius(tr), CornerRadius(br), CornerRadius(bl)))
    }
    drawPath(body, color)
    if (last) {
        val tw = 7.dp.toPx()
        val th = 12.dp.toPx()
        val tail = Path()
        if (mine) {
            tail.moveTo(w - 4.dp.toPx(), h - th)
            tail.quadraticBezierTo(w - 1.dp.toPx(), h - 2.dp.toPx(), w + tw, h)
            tail.lineTo(w - 8.dp.toPx(), h)
        } else {
            tail.moveTo(4.dp.toPx(), h - th)
            tail.quadraticBezierTo(1.dp.toPx(), h - 2.dp.toPx(), -tw, h)
            tail.lineTo(8.dp.toPx(), h)
        }
        tail.close()
        drawPath(tail, color)
    }
}

private val UrlRegex = Regex("https?://[^\\s]+")

@Composable
private fun linkified(text: String, linkColor: Color): AnnotatedString {
    return remember(text, linkColor) {
        buildAnnotatedString {
            var cursor = 0
            for (match in UrlRegex.findAll(text)) {
                append(text.substring(cursor, match.range.first))
                pushStringAnnotation("URL", match.value)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { append(match.value) }
                pop()
                cursor = match.range.last + 1
            }
            append(text.substring(cursor))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Bubble(
    m: UiMsg,
    first: Boolean,
    last: Boolean,
    showSender: Boolean,
    onLongPress: () -> Unit,
    onImage: (String) -> Unit,
    onReact: (String) -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    val ex = MaterialTheme.extras
    val bg = if (m.mine) ex.bubbleOut else ex.bubbleIn
    val fg = if (m.mine) ex.onBubbleOut else ex.onBubbleIn
    val metaColor = if (m.mine) ex.metaOut else ex.metaIn
    val isVoice = m.kind == "voice" || (m.imageUrl.isNotBlank() &&
        listOf(".m4a", ".ogg", ".webm", ".mp3", ".wav").any { m.imageUrl.substringBefore('?').lowercase().endsWith(it) })
    val hasImage = m.imageUrl.isNotBlank() && !isVoice
    val imageOnly = hasImage && m.text.isBlank()
    val uriHandler = LocalUriHandler.current

    Box(
        Modifier
            .widthIn(max = 304.dp)
            .drawBubble(bg, m.mine, first, last)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (m.send == SendState.FAILED) onRetry() },
                onLongClick = onLongPress,
            )
            .padding(if (hasImage) 3.dp else 0.dp),
    ) {
        Column(Modifier.padding(horizontal = if (hasImage) 0.dp else 11.dp, vertical = if (hasImage) 0.dp else 6.dp)) {
            if (showSender) {
                Text(
                    m.senderName,
                    color = senderColor(m.senderId),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = if (hasImage) 8.dp else 0.dp, top = if (hasImage) 4.dp else 0.dp, bottom = 2.dp),
                )
            }
            m.reply?.let { ReplyQuote(it, m.mine, Modifier.padding(horizontal = if (hasImage) 6.dp else 0.dp).padding(bottom = 4.dp)) }

            if (isVoice) {
                VoiceContent(m.imageUrl, m.text, fg, m.mine)
                Box(Modifier.align(Alignment.End)) { MetaRow(m, metaColor) }
            }

            if (hasImage) {
                Box {
                    AsyncImage(
                        model = m.imageUrl,
                        contentDescription = "Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 248.dp, height = 248.dp).clip(RoundedCornerShape(14.dp)).clickable { onImage(m.imageUrl) },
                    )
                    if (imageOnly) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = 0.45f),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                        ) {
                            Box(Modifier.padding(horizontal = 7.dp, vertical = 2.dp)) { MetaRow(m, Color.White) }
                        }
                    }
                }
            }

            if (m.text.isNotBlank() && !isVoice) {
                val pad = if (m.mine) "\u00A0".repeat(if (m.edited) 18 else 11) else "\u00A0".repeat(if (m.edited) 14 else 7)
                val hasUrl = UrlRegex.containsMatchIn(m.text)
                Box(Modifier.padding(horizontal = if (hasImage) 8.dp else 0.dp).padding(top = if (hasImage) 4.dp else 0.dp, bottom = if (hasImage) 2.dp else 0.dp)) {
                    if (hasUrl) {
                        val annotated = linkified(m.text + pad, ex.link)
                        androidx.compose.foundation.text.ClickableText(
                            text = annotated,
                            style = MaterialTheme.typography.bodyLarge.copy(color = fg),
                            onClick = { offset ->
                                annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                                    runCatching { uriHandler.openUri(it.item) }
                                }
                            },
                        )
                    } else {
                        Text(m.text + pad, color = fg, style = MaterialTheme.typography.bodyLarge)
                    }
                    Box(Modifier.align(Alignment.BottomEnd)) { MetaRow(m, metaColor) }
                }
            }

            if (m.reactions.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 4.dp, start = if (hasImage) 6.dp else 0.dp, bottom = if (hasImage) 4.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    m.reactions.forEach { (emoji, users) ->
                        Surface(
                            onClick = { onReact(emoji) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(emoji, fontSize = 14.sp)
                                if (users.size > 1) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(users.size.toString(), color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            if (m.send == SendState.FAILED) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Not sent \u2022 tap to retry", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Discard", color = fg.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable(onClick = onDiscard),
                    )
                }
            }

        }
    }
}

@Composable
private fun MetaRow(m: UiMsg, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (m.pinned) {
            Icon(Icons.Filled.PushPin, null, tint = color, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(3.dp))
        }
        if (m.edited) {
            Text("edited ", color = color, fontSize = 11.sp)
        }
        Text(m.time, color = color, fontSize = 11.sp)
        if (m.mine) {
            Spacer(Modifier.width(3.dp))
            when (m.send) {
                SendState.PENDING -> Icon(Icons.Filled.Schedule, null, tint = color, modifier = Modifier.size(13.dp))
                SendState.FAILED -> Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(13.dp))
                SendState.SENT ->
                    if (m.seen) Icon(Icons.Filled.DoneAll, null, tint = if (color == Color.White) color else MaterialTheme.extras.tick, modifier = Modifier.size(16.dp))
                    else Icon(Icons.Filled.Check, null, tint = color, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun ReplyQuote(reply: ReplyDto, mine: Boolean, modifier: Modifier = Modifier) {
    val accent = if (mine) MaterialTheme.extras.tick else MaterialTheme.colorScheme.primary
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.14f))
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(reply.sender?.name.orEmpty(), color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                reply.text.ifBlank { "Photo" },
                color = (if (mine) MaterialTheme.extras.onBubbleOut else MaterialTheme.extras.onBubbleIn).copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ------------------------------------------------------------------ composer

@Composable
private fun Composer(
    draft: String,
    onDraft: (String) -> Unit,
    replyTo: MessageDto?,
    editing: MessageDto?,
    attachment: String?,
    uploading: Boolean,
    onCancelCompose: () -> Unit,
    onClearAttachment: () -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    recording: Boolean,
    recSeconds: Int,
    sendingVoice: Boolean,
    onMic: () -> Unit,
    onCancelRec: () -> Unit,
    onSendRec: () -> Unit,
) {
    val canSend = draft.isNotBlank() || attachment != null
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
        if (replyTo != null || editing != null) {
            val label = if (editing != null) "Edit message" else replyTo?.sender?.name.orEmpty()
            val body = (editing ?: replyTo)?.text.orEmpty().ifBlank { "Photo" }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (editing != null) Icons.Filled.Edit else Icons.AutoMirrored.Filled.Reply, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onCancelCompose) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
            }
        }
        if (attachment != null || uploading) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (uploading) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Uploading photo\u2026", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    AsyncImage(attachment, null, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(12.dp))
                    Text("Photo ready \u2014 add a caption", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = onClearAttachment) { Icon(Icons.Filled.Close, contentDescription = "Remove photo") }
                }
            }
        }
        if (recording) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.extras.field,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Row(Modifier.padding(start = 18.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                        Spacer(Modifier.width(10.dp))
                        Text(String.format("%d:%02d", recSeconds / 60, recSeconds % 60), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(12.dp))
                        Text("Recording\u2026", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        IconButton(onClick = onCancelRec) {
                            Icon(Icons.Filled.Delete, contentDescription = "Cancel recording", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onSendRec,
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send voice message") }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                TextField(
                    value = draft,
                    onValueChange = onDraft,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    shape = RoundedCornerShape(26.dp),
                    maxLines = 6,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Default),
                    leadingIcon = null,
                    trailingIcon = {
                        IconButton(onClick = onAttach, enabled = !uploading) {
                            Icon(Icons.Filled.AttachFile, contentDescription = "Attach photo")
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.extras.field,
                        unfocusedContainerColor = MaterialTheme.extras.field,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = if (canSend) onSend else onMic,
                    enabled = !uploading && !sendingVoice,
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (sendingVoice) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(
                            when {
                                !canSend -> Icons.Filled.Mic
                                editing != null -> Icons.Filled.Check
                                else -> Icons.AutoMirrored.Filled.Send
                            },
                            contentDescription = if (canSend) "Send" else "Record voice message",
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ voice messages

@Composable
private fun VoiceContent(url: String, label: String, fg: Color, mine: Boolean) {
    val playing = VoicePlayer.currentUrl == url
    val progress = if (playing) VoicePlayer.progress else 0f
    val seconds = remember(label) { Regex("(\\d+)").find(label)?.value?.toIntOrNull() ?: 0 }
    Row(Modifier.widthIn(min = 220.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(
            onClick = { VoicePlayer.toggle(url) },
            modifier = Modifier.size(42.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (mine) Color.White else MaterialTheme.colorScheme.primary,
                contentColor = if (mine) MaterialTheme.extras.bubbleOut else MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Pause" else "Play")
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = fg,
                trackColor = fg.copy(alpha = 0.25f),
            )
            Spacer(Modifier.height(6.dp))
            Text(String.format("%d:%02d", seconds / 60, seconds % 60), color = fg.copy(alpha = 0.75f), fontSize = 11.sp)
        }
    }
}

// ------------------------------------------------------------------ polls

@Composable
internal fun PollCard(poll: PollDto, onVote: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.88f).padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.extras.bubbleIn,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Poll, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(poll.question, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.extras.onBubbleIn)
            }
            Text(
                if (poll.isQuiz) "Quiz" else if (poll.allowMultiple) "Select one or more" else "Select one",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, bottom = 8.dp),
            )
            poll.options.forEachIndexed { index, option ->
                val selected = poll.myVotes.contains(index)
                val count = poll.counts.getOrElse(index) { 0 }
                val fraction = if (poll.totalVotes == 0) 0f else count.toFloat() / poll.totalVotes
                val correct = poll.correctOption == index
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .clickable(enabled = !poll.isClosed) { onVote(index) },
                ) {
                    Box(
                        Modifier.fillMaxWidth(fraction).height(46.dp)
                            .background((if (correct) MaterialTheme.extras.online else MaterialTheme.colorScheme.primary).copy(alpha = 0.25f)),
                    )
                    Row(
                        Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(option, modifier = Modifier.weight(1f), color = MaterialTheme.extras.onBubbleIn)
                        Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${poll.totalVotes} vote${if (poll.totalVotes == 1) "" else "s"}" + if (poll.isClosed) " \u2022 closed" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
