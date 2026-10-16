package com.talksy.app.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talksy.app.data.remote.dto.MessageDto
import com.talksy.app.data.remote.dto.PollDto
import com.talksy.app.ui.components.Avatar
import com.talksy.app.ui.components.DoubleTick
import com.talksy.app.ui.components.LoadingBox
import com.talksy.app.ui.nav.Routes
import com.talksy.app.ui.theme.LocalChatColors
import com.talksy.app.util.Time
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(nav: NavController, chatId: String, vm: ChatViewModel = viewModel()) {
    LaunchedEffect(chatId) { vm.start(chatId) }
    val state by vm.state.collectAsState()
    val chat = LocalChatColors.current
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    val title = state.chat?.title?.ifBlank { state.chat?.peer?.name ?: "Chat" } ?: "Chat"

    Scaffold(
        containerColor = chat.chatBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(title, state.chat?.peer?.picture ?: state.chat?.avatar, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    state.peerTyping -> "typing\u2026"
                                    state.chat?.type == "group" -> "${state.chat?.memberCount ?: 0} members"
                                    else -> "online"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.peerTyping) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.newPoll(chatId)) }) {
                        Icon(Icons.Filled.Poll, contentDescription = "Create poll")
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        TextField(
                            value = draft,
                            onValueChange = { draft = it; vm.notifyTyping() },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Message") },
                            maxLines = 5,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { vm.send(draft); draft = "" }),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { vm.send(draft); draft = "" },
                        enabled = draft.isNotBlank(),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (state.loading) {
                LoadingBox()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    var lastDay = ""
                    state.messages.forEach { msg ->
                        val day = dayLabel(msg.createdAt)
                        if (day != lastDay) {
                            lastDay = day
                            item(key = "day-" + msg.id) { DayChip(day) }
                        }
                        item(key = msg.id) {
                            if (msg.kind == "poll") {
                                val poll = msg.pollId?.let { state.polls[it] }
                                if (poll != null) PollCard(poll) { vm.vote(poll.id, it) }
                            } else {
                                MessageBubble(
                                    msg = msg,
                                    isMine = msg.sender.id == state.myId,
                                    showSender = state.chat?.type == "group",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun dayLabel(iso: String?): String {
    val date = Time.parse(iso) ?: return ""
    val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val today = fmt.format(Calendar.getInstance().time)
    val yest = fmt.format((Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }).time)
    val d = fmt.format(date)
    return when (d) {
        today -> "Today"
        yest -> "Yesterday"
        else -> d
    }
}

@Composable
private fun DayChip(label: String) {
    if (label.isBlank()) return
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 1.dp,
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageBubble(msg: MessageDto, isMine: Boolean, showSender: Boolean) {
    val chat = LocalChatColors.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            if (!isMine && showSender) {
                Text(
                    msg.sender.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 10.dp, bottom = 2.dp),
                )
            }
            Surface(
                color = if (isMine) chat.outgoingBubble else chat.incomingBubble,
                shape = RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = if (isMine) 18.dp else 5.dp,
                    bottomEnd = if (isMine) 5.dp else 18.dp,
                ),
                tonalElevation = if (isMine) 0.dp else 1.dp,
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                    if (msg.text.isNotBlank()) {
                        Text(
                            msg.text,
                            color = if (isMine) chat.outgoingText else chat.incomingText,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Row(
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            Time.clock(msg.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMine) chat.outgoingText.copy(alpha = 0.75f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isMine) {
                            Spacer(Modifier.width(3.dp))
                            DoubleTick(tint = chat.tick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PollCard(poll: PollDto, onVote: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Poll, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(poll.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            poll.options.forEachIndexed { index, option ->
                val selected = poll.myVotes.contains(index)
                val count = poll.counts.getOrElse(index) { 0 }
                val fraction = if (poll.totalVotes == 0) 0f else count.toFloat() / poll.totalVotes
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = !poll.isClosed) { onVote(index) },
                ) {
                    Box(
                        Modifier.fillMaxWidth(fraction).height(46.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = { if (!poll.isClosed) onVote(index) })
                        Text(option, modifier = Modifier.weight(1f))
                        Text("$count", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${poll.totalVotes} votes" + if (poll.isClosed) " \u00B7 closed" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
