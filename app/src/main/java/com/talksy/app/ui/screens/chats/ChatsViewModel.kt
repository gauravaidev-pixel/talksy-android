package com.talksy.app.ui.screens.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talksy.app.AppGraph
import com.talksy.app.data.remote.dto.ChatDto
import com.talksy.app.data.remote.dto.UserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChatsUiState(
    val loading: Boolean = true,
    val chats: List<ChatDto> = emptyList(),
    val myId: String = "",
    /** Live presence overrides (userId -> online) on top of what the API returned. */
    val presence: Map<String, Boolean> = emptyMap(),
    /** People found by the unified search (users I don't have a chat with yet). */
    val people: List<UserDto> = emptyList(),
    val error: String? = null,
)

class ChatsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ChatsUiState())
    val state: StateFlow<ChatsUiState> = _state

    private var refreshJob: Job? = null

    init {
        // Cold start: paint the last known chat list from disk immediately, then refresh from the network.
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { AppGraph.disk.loadChats() }
            if (cached.isNotEmpty() && _state.value.chats.isEmpty()) {
                cached.forEach { AppGraph.chatCache[it.id] = it }
                _state.value = _state.value.copy(loading = false, chats = cached)
            }
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(myId = AppGraph.session.userIdFlow.first() ?: "")
        }
        refresh()
        // Any inbound message bumps the ordering / unread badges.
        viewModelScope.launch { AppGraph.socket.messages.collect { refreshSoon() } }
        // Added to / removed from a group, group renamed, ...
        viewModelScope.launch { AppGraph.socket.chatsChanged.collect { refreshSoon() } }
        viewModelScope.launch {
            AppGraph.socket.presence.collect { (userId, online) ->
                _state.value = _state.value.copy(presence = _state.value.presence + (userId to online))
            }
        }
    }

    private fun refreshSoon() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(250)
            fetch()
        }
    }

    fun refresh(silent: Boolean = false) {
        if (!silent && _state.value.chats.isEmpty()) _state.value = _state.value.copy(loading = true)
        viewModelScope.launch { fetch() }
    }

    private var fetching = false
    private var fetchAgain = false

    /** Only one list request at a time; bursts of events collapse into a single follow-up request. */
    private suspend fun fetch() {
        if (fetching) { fetchAgain = true; return }
        fetching = true
        try {
            do {
                fetchAgain = false
                fetchOnce()
            } while (fetchAgain)
        } finally {
            fetching = false
        }
    }

    private suspend fun fetchOnce() {
        AppGraph.chats.list()
            .onSuccess {
                it.chats.forEach { chat -> AppGraph.chatCache[chat.id] = chat }
                AppGraph.disk.saveChats(it.chats)
                _state.value = _state.value.copy(loading = false, chats = it.chats, error = null)
                prefetch(it.chats)
            }
            .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
    }

    /** Warm the message cache of the top chats so opening them is instant. `peek` = does not mark as read. */
    private fun prefetch(chats: List<ChatDto>) {
        chats.take(6).forEach { chat ->
            if (AppGraph.messageCache[chat.id] == null) {
                viewModelScope.launch {
                    AppGraph.messages.list(chat.id, peek = true).onSuccess { res ->
                        AppGraph.messageCache[chat.id] = res.messages
                        AppGraph.disk.saveMessages(chat.id, res.messages)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- per-chat actions

    fun togglePin(chat: ChatDto) = act { AppGraph.chats.setPinned(chat.id, !chat.isPinned) }
    fun mute(chat: ChatDto, hours: Int) = act { AppGraph.chats.mute(chat.id, hours) }
    fun clear(chat: ChatDto) = act { AppGraph.chats.clear(chat.id) }
    fun hide(chat: ChatDto) = act { AppGraph.chats.hide(chat.id) }

    private fun act(block: suspend () -> Result<*>) {
        viewModelScope.launch {
            block().onFailure { e -> _state.value = _state.value.copy(error = e.message) }
            fetch()
        }
    }

    /** "Saved Messages" is created on first use. */
    fun openSaved(onReady: (String) -> Unit) {
        viewModelScope.launch {
            AppGraph.chats.openSaved()
                .onSuccess { onReady(it.chat.id); refresh(silent = true) }
                .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
        }
    }

    fun consumeError() {
        _state.value = _state.value.copy(error = null)
    }

    // ---------------------------------------------------------------- unified search (chats + everyone on Talksy)

    fun searchPeople(q: String) {
        if (q.trim().length < 2) {
            _state.value = _state.value.copy(people = emptyList())
            return
        }
        viewModelScope.launch {
            AppGraph.users.search(q.trim()).onSuccess { res ->
                _state.value = _state.value.copy(people = res.users.filter { it.id != _state.value.myId })
            }
        }
    }

    fun clearPeople() {
        _state.value = _state.value.copy(people = emptyList())
    }

    /** Open (or create) the 1-to-1 chat with someone found in search. */
    fun openDirect(userId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            AppGraph.chats.openDirect(userId)
                .onSuccess { res ->
                    AppGraph.chatCache[res.chat.id] = res.chat
                    onReady(res.chat.id)
                    refresh(silent = true)
                }
                .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
        }
    }
}
