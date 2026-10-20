package com.talksy.app.ui.screens.calls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.talksy.app.AppGraph
import com.talksy.app.data.remote.dto.CallDto
import com.talksy.app.ui.components.Avatar
import com.talksy.app.ui.components.EmptyState
import com.talksy.app.ui.screens.call.rememberCallLauncher
import com.talksy.app.util.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CallsUiState(val loading: Boolean = true, val calls: List<CallDto> = emptyList())

class CallsViewModel : ViewModel() {
    private val _state = MutableStateFlow(CallsUiState())
    val state: StateFlow<CallsUiState> = _state

    init {
        val cached = AppGraph.disk.loadCalls()
        if (cached.isNotEmpty()) _state.value = CallsUiState(loading = false, calls = cached)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            AppGraph.calls.history()
                .onSuccess { res ->
                    AppGraph.disk.saveCalls(res.calls)
                    _state.update { it.copy(loading = false, calls = res.calls) }
                }
                .onFailure { _state.update { it.copy(loading = false) } }
        }
    }
}

@Composable
fun CallsScreen(vm: CallsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val startCall = rememberCallLauncher()
    LaunchedEffect(Unit) { vm.refresh() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        Text("Calls", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 10.dp))
        when {
            state.loading -> com.talksy.app.ui.components.SkeletonList()
            state.calls.isEmpty() -> EmptyState("No calls yet", "Open a chat and tap the phone or video icon to start a call.", Icons.Filled.Call)
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.calls, key = { it.id }) { call ->
                    val missed = call.status == "missed" || call.status == "busy" || call.status == "rejected"
                    val incoming = call.direction == "incoming"
                    Row(
                        Modifier.fillMaxWidth().clickable { startCall(call.peer, call.video) }.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(call.peer.name, call.peer.picture, size = 52.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                call.peer.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (missed && incoming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when {
                                        missed && incoming -> Icons.Filled.CallMissed
                                        incoming -> Icons.Filled.CallReceived
                                        else -> Icons.Filled.CallMade
                                    },
                                    null,
                                    Modifier.size(16.dp),
                                    tint = if (missed && incoming) MaterialTheme.colorScheme.error else Color(0xFF2EBD59),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    buildString {
                                        append(Time.listLabel(call.createdAt))
                                        if (call.duration > 0) append(" \u2022 ").append(String.format("%d:%02d", call.duration / 60, call.duration % 60))
                                        else if (missed) append(" \u2022 ").append(call.status.replaceFirstChar { it.uppercase() })
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Icon(
                            if (call.video) Icons.Filled.Videocam else Icons.Filled.Call,
                            contentDescription = "Call back",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
