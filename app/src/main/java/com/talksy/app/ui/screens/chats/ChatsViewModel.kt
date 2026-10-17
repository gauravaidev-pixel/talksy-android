package com.talksy.app.ui.screens.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talksy.app.AppGraph
import com.talksy.app.data.remote.dto.ChatDto
import kotlinx.coroutines.Job
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
    val error: String? = null,
)

class ChatsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ChatsUiState())
    val state: StateFlow<ChatsUiState> = _state

    private var refreshJob: Job? = null

    init {
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

    private suspend fun fetch() {
        AppGraph.chats.list()
            .onSuccess { _state.value = _state.value.copy(loading = false, chats = it.chats, error = null) }
            .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
    }
}
