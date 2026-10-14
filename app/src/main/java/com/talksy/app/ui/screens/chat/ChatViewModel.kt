package com.talksy.app.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talksy.app.AppGraph
import com.talksy.app.data.remote.dto.ChatDto
import com.talksy.app.data.remote.dto.MessageDto
import com.talksy.app.data.remote.dto.PollDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChatUiState(
    val loading: Boolean = true,
    val chat: ChatDto? = null,
    val messages: List<MessageDto> = emptyList(),
    val polls: Map<String, PollDto> = emptyMap(),
    val myId: String = "",
    val peerTyping: Boolean = false,
    val error: String? = null,
)

class ChatViewModel : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    private var chatId: String = ""

    fun start(id: String) {
        if (chatId == id) return
        chatId = id
        viewModelScope.launch { _state.value = _state.value.copy(myId = AppGraph.session.userIdFlow.first() ?: "") }
        load()
        observeRealtime()
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val chat = AppGraph.chats.get(chatId).getOrNull()?.chat
            val messages = AppGraph.messages.list(chatId).getOrNull()?.messages ?: emptyList()
            val polls = AppGraph.polls.list(chatId).getOrNull()?.polls ?: emptyList()
            _state.value = _state.value.copy(
                loading = false,
                chat = chat,
                messages = messages,
                polls = polls.associateBy { it.id },
            )
            AppGraph.socket.joinChat(chatId)
        }
    }

    private fun observeRealtime() {
        viewModelScope.launch {
            AppGraph.socket.messages.collect { msg ->
                if (msg.chatId != chatId) return@collect
                val existing = _state.value.messages.indexOfFirst { it.id == msg.id }
                val updated = _state.value.messages.toMutableList()
                if (existing >= 0) updated[existing] = msg else updated.add(msg)
                _state.value = _state.value.copy(messages = updated)
            }
        }
        viewModelScope.launch {
            AppGraph.socket.polls.collect { (cid, poll) ->
                if (cid != chatId) return@collect
                _state.value = _state.value.copy(polls = _state.value.polls + (poll.id to poll))
            }
        }
        viewModelScope.launch {
            AppGraph.socket.deleted.collect { id ->
                _state.value = _state.value.copy(messages = _state.value.messages.filterNot { it.id == id })
            }
        }
        viewModelScope.launch {
            AppGraph.socket.typing.collect { (cid, uid) ->
                if (cid == chatId && uid != _state.value.myId) {
                    _state.value = _state.value.copy(peerTyping = true)
                    kotlinx.coroutines.delay(2500)
                    _state.value = _state.value.copy(peerTyping = false)
                }
            }
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            AppGraph.messages.send(chatId, text.trim(), null, null, false)
                .onSuccess { msg ->
                    if (_state.value.messages.none { it.id == msg.message.id }) {
                        _state.value = _state.value.copy(messages = _state.value.messages + msg.message)
                    }
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun notifyTyping() = AppGraph.socket.typing(chatId, true)

    fun vote(pollId: String, index: Int) {
        viewModelScope.launch {
            AppGraph.polls.vote(pollId, index)
                .onSuccess { _state.value = _state.value.copy(polls = _state.value.polls + (it.poll.id to it.poll)) }
        }
    }

    fun createPoll(question: String, options: List<String>) {
        viewModelScope.launch {
            AppGraph.polls.create(chatId, question, options).onSuccess {
                _state.value = _state.value.copy(polls = _state.value.polls + (it.poll.id to it.poll))
            }
        }
    }

    override fun onCleared() {
        if (chatId.isNotBlank()) AppGraph.socket.leaveChat(chatId)
        super.onCleared()
    }
}
