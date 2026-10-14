package com.talksy.app.ui.screens.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talksy.app.AppGraph
import com.talksy.app.data.remote.dto.ChatDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatsUiState(
    val loading: Boolean = true,
    val chats: List<ChatDto> = emptyList(),
    val error: String? = null,
)

class ChatsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ChatsUiState())
    val state: StateFlow<ChatsUiState> = _state

    init {
        refresh()
        // Any inbound message bumps the list ordering.
        viewModelScope.launch {
            AppGraph.socket.messages.collect { refresh(silent = true) }
        }
    }

    fun refresh(silent: Boolean = false) {
        if (!silent) _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            AppGraph.chats.list()
                .onSuccess { _state.value = ChatsUiState(loading = false, chats = it.chats) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }
}
