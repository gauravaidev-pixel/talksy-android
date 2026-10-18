package com.talksy.app.ui.screens.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talksy.app.AppGraph
import com.talksy.app.data.local.ChatPrefs
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
import java.io.File
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
    val disappear: String = "off",
    val searchResults: List<MessageDto>? = null,
    val typingName: String? = null,
    val sendingVoice: Boolean = false,
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
        _state.update { it.copy(disappear = ChatPrefs.disappear(AppGraph.appContext, id)) }
        load()
        observeRealtime()
    }

    private fun load() {
        // 1) Paint instantly from memory (header from the chat list, last messages from the previous visit).
        val cachedChat = AppGraph.chatCache[chatId]
        val cachedMessages = AppGraph.messageCache[chatId]
            ?: AppGraph.disk.loadMessages(chatId)?.also { AppGraph.messageCache[chatId] = it }
        _state.update {
            it.copy(
                chat = cachedChat ?: it.chat,
                messages = cachedMessages ?: it.messages,
                loading = cachedMessages == null,
                peerOnline = cachedChat?.peer?.isOnline ?: it.peerOnline,
                peerLastSeen = cachedChat?.peer?.lastSeen ?: it.peerLastSeen,
            )
        }

        // 2) Refresh in parallel. Each request updates the UI as soon as it lands
        //    (before: chat -> messages -> polls ran one after another and blocked the screen).
        viewModelScope.launch {
            AppGraph.messages.list(chatId)
                .onSuccess { res ->
                    AppGraph.messageCache[chatId] = res.messages
                    AppGraph.disk.saveMessages(chatId, res.messages)
                    _state.update { it.copy(loading = false, messages = res.messages, hasMore = res.messages.size >= PAGE) }
                    AppGraph.socket.joinChat(chatId)
                    markRead()
                    // polls are only needed when the chat actually contains one
                    if (res.messages.any { m -> m.kind == "poll" }) loadPolls()
                }
                .onFailure { e -> _state.update { it.copy(loading = false, toast = e.message) } }
        }
        viewModelScope.launch {
            AppGraph.chats.get(chatId).onSuccess { res ->
                AppGraph.chatCache[chatId] = res.chat
                _state.update {
                    it.copy(
                        chat = res.chat,
                        peerOnline = res.chat.peer?.isOnline ?: false,
                        peerLastSeen = res.chat.peer?.lastSeen,
                    )
                }
            }
        }
    }

    private fun loadPolls() {
        viewModelScope.launch {
            AppGraph.polls.list(chatId).onSuccess { res ->
                _state.update { it.copy(polls = res.polls.associateBy { p -> p.id } + it.polls) }
            }
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
                    val who = _state.value.chat?.members?.firstOrNull { it.id == ev.userId }?.name?.substringBefore(' ')
                    _state.update { it.copy(typing = ev.isTyping, typingName = if (ev.isTyping) who else null) }
                    if (ev.isTyping) {
                        typingClear = launch {
                            delay(3500)
                            _state.update { it.copy(typing = false, typingName = null) }
                        }
                    }
                }
            }
        }
        // the other person opened the chat -> all my messages become "seen" (double tick)
        viewModelScope.launch {
            AppGraph.socket.reads.collect { ev ->
                if (ev.chatId == chatId && ev.userId != _state.value.myId && _state.value.chat?.type == "direct") {
                    _state.update { st ->
                        st.copy(messages = st.messages.map { if (it.sender.id == st.myId && !it.isSeen) it.copy(isSeen = true) else it })
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
            AppGraph.messages.send(chatId, local.text.ifBlank { null }, local.imageUrl.ifBlank { null }, replyToId, false, autoDeleteValue())
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

    // ---------------------------------------------------------------- extra features

    private fun autoDeleteValue(): String = _state.value.disappear.let { if (it == "off") "never" else it }

    fun setDisappear(value: String) {
        ChatPrefs.setDisappear(AppGraph.appContext, chatId, value)
        _state.update { it.copy(disappear = value, toast = if (value == "off") "Disappearing messages off" else "New messages will disappear") }
    }

    /** Send an existing message to other chats. */
    fun forward(m: MessageDto, targetChatIds: List<String>) {
        viewModelScope.launch {
            var failed = 0
            targetChatIds.forEach { id ->
                AppGraph.messages.send(id, m.text.ifBlank { null }, m.imageUrl.ifBlank { null }, null, false)
                    .onFailure { failed++ }
            }
            _state.update { it.copy(toast = if (failed == 0) "Forwarded" else "Could not forward to $failed chat(s)") }
        }
    }

    fun report(m: MessageDto, reason: String) {
        viewModelScope.launch {
            AppGraph.messages.report(m.id, reason)
                .onSuccess { _state.update { it.copy(toast = "Reported. Thanks!") } }
                .onFailure { e -> _state.update { it.copy(toast = e.message) } }
        }
    }

    fun search(q: String) {
        if (q.trim().length < 2) {
            _state.update { it.copy(searchResults = null) }
            return
        }
        viewModelScope.launch {
            AppGraph.messages.search(chatId, q.trim())
                .onSuccess { res -> _state.update { it.copy(searchResults = res.messages) } }
        }
    }

    fun clearSearch() = _state.update { it.copy(searchResults = null) }

    fun toggleMute() {
        val muted = _state.value.chat?.isMuted == true
        viewModelScope.launch {
            AppGraph.chats.mute(chatId, if (muted) 0 else 8)
            AppGraph.chats.get(chatId).onSuccess { res ->
                AppGraph.chatCache[chatId] = res.chat
                _state.update { it.copy(chat = res.chat, toast = if (muted) "Unmuted" else "Muted for 8 hours") }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            AppGraph.chats.clear(chatId)
                .onSuccess {
                    AppGraph.messageCache.remove(chatId)
                    _state.update { it.copy(messages = emptyList(), pending = emptyList(), toast = "History cleared") }
                }
                .onFailure { e -> _state.update { it.copy(toast = e.message) } }
        }
    }

    /** Upload a recorded voice file and send it as a voice message. */
    fun sendVoice(file: File, seconds: Int) {
        _state.update { it.copy(sendingVoice = true) }
        viewModelScope.launch {
            AppGraph.uploads.voice(file)
                .onSuccess { url ->
                    AppGraph.messages.send(chatId, "[Voice ${seconds}s]", url, null, false, autoDeleteValue())
                        .onSuccess { res ->
                            _state.update { st ->
                                st.copy(
                                    sendingVoice = false,
                                    messages = if (st.messages.any { it.id == res.message.id }) st.messages else st.messages + res.message,
                                )
                            }
                        }
                        .onFailure { e -> _state.update { it.copy(sendingVoice = false, toast = e.message) } }
                }
                .onFailure { e -> _state.update { it.copy(sendingVoice = false, toast = e.message) } }
            file.delete()
        }
    }

    override fun onCleared() {
        if (chatId.isNotBlank()) AppGraph.socket.leaveChat(chatId)
        super.onCleared()
    }
}
