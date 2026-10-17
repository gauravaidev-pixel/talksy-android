package com.talksy.app.ui.screens.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talksy.app.AppGraph
import com.talksy.app.data.remote.dto.ChatDto
import com.talksy.app.data.remote.dto.MessageDto
import com.talksy.app.data.remote.dto.PollDto
import com.talksy.app.data.remote.dto.ReplyDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** A message the user just sent that the server hasn't confirmed yet (shown with a clock icon). */
data class PendingMessage(
    val localId: String,
    val text: String,
    val imageUrl: String,
    val replyTo: ReplyDto?,
    val failed: Boolean = false,
)

data class ChatUiState(
    val loading: Boolean = true,
    val chat: ChatDto? = null,
    val messages: List<MessageDto> = emptyList(),
    val pending: List<PendingMessage> = emptyList(),
    val polls: Map<String, PollDto> = emptyMap(),
    val myId: String = "",
    val typing: Boolean = false,
    val peerOnline: Boolean = false,
    val peerLastSeen: String? = null,
    val replyTo: MessageDto? = null,
    val editing: MessageDto? = null,
    val attachment: String? = null,
    val uploading: Boolean = false,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val toast: String? = null,
)

private const val PAGE = 50

class ChatViewModel : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    private var chatId: String = ""
    private var typingClear: Job? = null
    private var readJob: Job? = null
    private var lastTypingSent = 0L

    fun start(id: String) {
        if (chatId == id) return
        chatId = id
        viewModelScope.launch { _state.update { it.copy(myId = AppGraph.session.userIdFlow.first() ?: "") } }
        load()
        observeRealtime()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val chat = AppGraph.chats.get(chatId).getOrNull()?.chat
            val messages = AppGraph.messages.list(chatId).getOrNull()?.messages ?: emptyList()
            val polls = AppGraph.polls.list(chatId).getOrNull()?.polls ?: emptyList()
            _state.update {
                it.copy(
                    loading = false,
                    chat = chat,
                    messages = messages,
                    polls = polls.associateBy { p -> p.id },
                    hasMore = messages.size >= PAGE,
                    peerOnline = chat?.peer?.isOnline ?: false,
                    peerLastSeen = chat?.peer?.lastSeen,
                )
            }
            AppGraph.socket.joinChat(chatId)
            markRead()
        }
    }

    fun loadOlder() {
        val s = _state.value
        val oldest = s.messages.firstOrNull() ?: return
        if (!s.hasMore || s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            AppGraph.messages.list(chatId, before = oldest.id)
                .onSuccess { res ->
                    _state.update {
                        it.copy(
                            messages = res.messages + it.messages,
                            hasMore = res.messages.size >= PAGE,
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }

    private fun observeRealtime() {
        viewModelScope.launch {
            AppGraph.socket.messages.collect { msg ->
                if (msg.chatId != chatId) return@collect
                _state.update { st ->
                    val index = st.messages.indexOfFirst { it.id == msg.id }
                    val updated = st.messages.toMutableList()
                    if (index >= 0) updated[index] = msg else updated.add(msg)
                    st.copy(messages = updated, typing = if (msg.sender.id == st.peerId()) false else st.typing)
                }
                if (msg.sender.id != _state.value.myId) markRead()
            }
        }
        viewModelScope.launch {
            AppGraph.socket.polls.collect { (cid, poll) ->
                if (cid == chatId) {
                    _state.update { st ->
                        // socket payloads are viewer-neutral: keep MY votes / the quiz answer I already know
                        val old = st.polls[poll.id]
                        val merged = if (old == null) poll
                        else poll.copy(myVotes = old.myVotes, correctOption = poll.correctOption ?: old.correctOption)
                        st.copy(polls = st.polls + (poll.id to merged))
                    }
                }
            }
        }
        viewModelScope.launch {
            AppGraph.socket.deleted.collect { id ->
                _state.update { st -> st.copy(messages = st.messages.filterNot { it.id == id }) }
            }
        }
        viewModelScope.launch {
            AppGraph.socket.typing.collect { ev ->
                if (ev.chatId == chatId && ev.userId != _state.value.myId) {
                    typingClear?.cancel()
                    _state.update { it.copy(typing = ev.isTyping) }
                    if (ev.isTyping) {
                        typingClear = launch {
                            delay(3500)
                            _state.update { it.copy(typing = false) }
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            AppGraph.socket.presence.collect { (userId, online) ->
                val peer = _state.value.chat?.peer?.id
                if (peer != null && peer == userId) {
                    _state.update {
                        it.copy(peerOnline = online, peerLastSeen = if (online) it.peerLastSeen else java.time.Instant.now().toString())
                    }
                }
            }
        }
    }

    private fun ChatUiState.peerId(): String? = chat?.peer?.id

    /** Tell the server we've seen everything (drives the unread badges); debounced. */
    private fun markRead() {
        readJob?.cancel()
        readJob = viewModelScope.launch {
            delay(400)
            AppGraph.chats.markRead(chatId)
        }
    }

    // ---------------------------------------------------------------- composer

    fun send(text: String) {
        val s = _state.value
        val trimmed = text.trim()
        val editing = s.editing
        if (editing != null) {
            if (trimmed.isBlank()) return
            _state.update { it.copy(editing = null) }
            viewModelScope.launch {
                AppGraph.messages.edit(editing.id, trimmed)
                    .onSuccess { res -> replaceMessage(res.message) }
                    .onFailure { e -> _state.update { it.copy(toast = e.message) } }
            }
            return
        }
        if (trimmed.isBlank() && s.attachment == null) return

        val reply = s.replyTo
        val image = s.attachment
        val local = PendingMessage(
            localId = UUID.randomUUID().toString(),
            text = trimmed,
            imageUrl = image.orEmpty(),
            replyTo = reply?.let { ReplyDto(it.id, it.text, it.sender) },
        )
        _state.update { it.copy(pending = it.pending + local, replyTo = null, attachment = null) }
        dispatch(local, reply?.id)
    }

    private fun dispatch(local: PendingMessage, replyToId: String?) {
        viewModelScope.launch {
            AppGraph.messages.send(chatId, local.text.ifBlank { null }, local.imageUrl.ifBlank { null }, replyToId, false)
                .onSuccess { res ->
                    _state.update { st ->
                        st.copy(
                            pending = st.pending.filterNot { it.localId == local.localId },
                            // the socket echo may already have delivered it
                            messages = if (st.messages.any { it.id == res.message.id }) st.messages
                            else st.messages + res.message,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { st ->
                        st.copy(
                            pending = st.pending.map { if (it.localId == local.localId) it.copy(failed = true) else it },
                            toast = e.message,
                        )
                    }
                }
        }
    }

    fun retry(localId: String) {
        val local = _state.value.pending.firstOrNull { it.localId == localId } ?: return
        _state.update { st -> st.copy(pending = st.pending.map { if (it.localId == localId) it.copy(failed = false) else it }) }
        dispatch(local, local.replyTo?.id)
    }

    fun discard(localId: String) {
        _state.update { st -> st.copy(pending = st.pending.filterNot { it.localId == localId }) }
    }

    fun notifyTyping() {
        val now = System.currentTimeMillis()
        if (now - lastTypingSent > 2000) {
            lastTypingSent = now
            AppGraph.socket.typing(chatId, true)
        }
    }

    fun attach(uri: Uri) {
        _state.update { it.copy(uploading = true) }
        viewModelScope.launch {
            AppGraph.uploads.image(uri)
                .onSuccess { url -> _state.update { it.copy(uploading = false, attachment = url) } }
                .onFailure { e -> _state.update { it.copy(uploading = false, toast = e.message) } }
        }
    }

    fun clearAttachment() = _state.update { it.copy(attachment = null) }
    fun startReply(m: MessageDto) = _state.update { it.copy(replyTo = m, editing = null) }
    fun startEdit(m: MessageDto) = _state.update { it.copy(editing = m, replyTo = null) }
    fun cancelCompose() = _state.update { it.copy(replyTo = null, editing = null) }
    fun consumeToast() = _state.update { it.copy(toast = null) }

    // ---------------------------------------------------------------- message actions

    private fun replaceMessage(msg: MessageDto) {
        _state.update { st -> st.copy(messages = st.messages.map { if (it.id == msg.id) msg else it }) }
    }

    fun delete(m: MessageDto) {
        viewModelScope.launch {
            AppGraph.messages.delete(m.id)
                .onSuccess { _state.update { st -> st.copy(messages = st.messages.filterNot { it.id == m.id }) } }
                .onFailure { e -> _state.update { it.copy(toast = e.message) } }
        }
    }

    fun react(m: MessageDto, emoji: String) {
        val me = _state.value.myId
        // optimistic toggle; the socket update will confirm it
        val users = m.reactions[emoji].orEmpty()
        val toggled = if (me in users) users - me else users + me
        val reactions = if (toggled.isEmpty()) m.reactions - emoji else m.reactions + (emoji to toggled)
        replaceMessage(m.copy(reactions = reactions))
        viewModelScope.launch {
            AppGraph.messages.react(m.id, emoji).onFailure { e ->
                replaceMessage(m)
                _state.update { it.copy(toast = e.message) }
            }
        }
    }

    fun pin(m: MessageDto) {
        viewModelScope.launch {
            AppGraph.messages.pin(m.id)
                .onSuccess { replaceMessage(m.copy(isPinned = !m.isPinned)) }
                .onFailure { e -> _state.update { it.copy(toast = e.message) } }
        }
    }

    fun vote(pollId: String, index: Int) {
        viewModelScope.launch {
            AppGraph.polls.vote(pollId, index)
                .onSuccess { res -> _state.update { it.copy(polls = it.polls + (res.poll.id to res.poll)) } }
                .onFailure { e -> _state.update { it.copy(toast = e.message) } }
        }
    }

    override fun onCleared() {
        if (chatId.isNotBlank()) AppGraph.socket.leaveChat(chatId)
        super.onCleared()
    }
}
