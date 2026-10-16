package com.talksy.app.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
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
import com.talksy.app.ui.theme.bubbleShape
import com.talksy.app.util.Time
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ChatScreen(nav: NavController, chatId: String, vm: ChatViewModel = viewModel()) {
    LaunchedEffect(chatId) { vm.start(chatId) }
    val state by vm.state.collectAsState()
    val chat = LocalChatColors.current
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size)
    }

    val title = state.chat?.title?.ifBlank { state.chat?.peer?.name ?: "Chat" } ?: "Chat"

    Column(Modifier.fillMaxSize().background(chat.chatBackground)) {
        // ---- header ----
        Row(
            Modifier.fillMaxWidth().background(chat.panel).padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = chat.t2)
            }
            Avatar(title, state.chat?.peer?.picture ?: state.chat?.avatar, size = 40.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(
                    when {
                        state.peerTyping -> "typing\u2026"
                        state.chat?.type == "group" -> "${state.chat?.memberCount ?: 0} members"
                        else -> "online"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.peerTyping) chat.accent else chat.t2,
                )
            }
            IconButton(onClick = { nav.navigate(Routes.newPoll(chatId)) }) {
                Icon(Icons.Filled.Poll, contentDescription = "Create poll", tint = chat.t2)
            }
        }

        // ---- messages ----
        Box(Modifier.weight(1f)) {
            if (state.loading) {
                LoadingBox()
            } else {
                val msgs = state.messages
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    var lastDay = ""
                    msgs.forEachIndexed { i, msg ->
                        val day = dayLabel(msg.createdAt)
                        if (day != lastDay) {
                            lastDay = day
                            item(key = "day-" + msg.id) { DayChip(day) }
                        }
                        val isMine = msg.sender.id == state.myId
                        val next = msgs.getOrNull(i + 1)
                        val tail = next == null || next.sender.id != msg.sender.id || next.kind == "poll"
                        item(key = msg.id) {
                            if (msg.kind == "poll") {
                                val poll = msg.pollId?.let { state.polls[it] }
                                if (poll != null) PollCard(poll) { vm.vote(poll.id, it) }
                            } else {
                                MessageBubble(
                                    msg = msg,
                                    isMine = isMine,
                                    tail = tail,
                                    showSender = state.chat?.type == "group" && !isMine,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- composer ----
        Row(
            Modifier.fillMaxWidth().background(chat.panel).padding(horizontal = 10.dp, vertical = 8.dp).imePadding(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(chat.inputBg).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Attach", tint = chat.t2, modifier = Modifier.padding(8.dp).size(22.dp))
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it; vm.notifyTyping() },
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp, horizontal = 4.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (draft.isNotBlank()) { vm.send(draft); draft = "" } }),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) Text("Message", color = chat.t3, style = MaterialTheme.typography.bodyLarge)
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(if (draft.isNotBlank()) chat.accent else chat.accent.copy(alpha = 0.5f))
                    .clickable { if (draft.isNotBlank()) { vm.send(draft); draft = "" } },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = chat.onAccent, modifier = Modifier.size(23.dp))
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
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(14.dp), color = Color.Black.copy(alpha = 0.30f)) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MessageBubble(msg: MessageDto, isMine: Boolean, tail: Boolean, showSender: Boolean) {
    val chat = LocalChatColors.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Surface(
                color = if (isMine) chat.outgoingBubble else chat.incomingBubble,
                shape = bubbleShape(isMine, tail),
                shadowElevation = 0.dp,
            ) {
                Column(
                    Modifier.padding(
                        start = if (tail && !isMine) 18.dp else 12.dp,
                        end = if (tail && isMine) 18.dp else 12.dp,
                        top = 7.dp,
                        bottom = if (tail) 13.dp else 7.dp,
                    )
                ) {
                    if (showSender) {
                        Text(
                            msg.sender.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = chat.accent,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
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
                            color = if (isMine) chat.outgoingText.copy(alpha = 0.65f) else chat.t2,
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
    val chat = LocalChatColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = chat.incomingBubble,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Poll, contentDescription = null, tint = chat.accent)
                Spacer(Modifier.width(8.dp))
                Text(poll.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = chat.incomingText)
            }
            Spacer(Modifier.height(10.dp))
            poll.options.forEachIndexed { index, option ->
                val selected = poll.myVotes.contains(index)
                val count = poll.counts.getOrElse(index) { 0 }
                val fraction = if (poll.totalVotes == 0) 0f else count.toFloat() / poll.totalVotes
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(chat.accent.copy(alpha = 0.14f))
                        .clickable(enabled = !poll.isClosed) { onVote(index) },
                ) {
                    Box(Modifier.fillMaxWidth(fraction).height(44.dp).background(chat.accent.copy(alpha = 0.35f)))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option, modifier = Modifier.weight(1f), color = chat.incomingText)
                        Text("$count", style = MaterialTheme.typography.labelSmall, color = chat.t2)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${poll.totalVotes} votes" + if (poll.isClosed) " \u00B7 closed" else "",
                style = MaterialTheme.typography.labelSmall,
                color = chat.t2,
            )
        }
    }
}
