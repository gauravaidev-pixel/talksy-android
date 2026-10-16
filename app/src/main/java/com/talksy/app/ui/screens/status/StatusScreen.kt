package com.talksy.app.ui.screens.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.talksy.app.AppGraph
import com.talksy.app.data.remote.dto.StatusDto
import com.talksy.app.ui.components.Avatar
import com.talksy.app.ui.components.EmptyState
import com.talksy.app.ui.components.LoadingBox
import com.talksy.app.ui.theme.LocalChatColors
import com.talksy.app.util.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StatusViewModel : ViewModel() {
    private val _state = MutableStateFlow<Pair<Boolean, List<StatusDto>>>(true to emptyList())
    val state: StateFlow<Pair<Boolean, List<StatusDto>>> = _state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = true to _state.value.second
            AppGraph.statuses.feed().onSuccess { _state.value = false to it.statuses }
        }
    }

    fun post(text: String) {
        viewModelScope.launch {
            AppGraph.statuses.create(text, null).onSuccess { refresh() }
        }
    }
}

@Composable
fun StatusScreen(vm: StatusViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val loading = state.first
    val statuses = state.second
    val chat = LocalChatColors.current
    var composer by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Status", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            when {
                loading -> LoadingBox()
                statuses.isEmpty() -> EmptyState("No status updates", "Status disappears after 24 hours.", Icons.Filled.DonutLarge)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        // "My status" add row
                        Row(
                            Modifier.fillMaxWidth().clickable { composer = true }.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Avatar("Me", null, size = 52.dp)
                                Box(
                                    Modifier.size(52.dp).padding(2.dp),
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("My status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Tap to add a status update", style = MaterialTheme.typography.bodyMedium, color = chat.t2)
                            }
                        }
                    }
                    items(statuses, key = { it.id }) { s ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(s.user.name, s.user.picture, size = 52.dp, ring = true, ringSeen = s.viewed)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    s.text.ifBlank { "Photo" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = chat.t2,
                                    maxLines = 1,
                                )
                            }
                            Text(Time.short(s.createdAt), style = MaterialTheme.typography.labelSmall, color = chat.t3)
                        }
                    }
                }
            }
        }

        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp)
                .size(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(chat.accent)
                .clickable { composer = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New status", tint = chat.onAccent, modifier = Modifier.size(26.dp))
        }
    }

    if (composer) {
        AlertDialog(
            onDismissRequest = { composer = false },
            title = { Text("New status") },
            text = {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it },
                    placeholder = { Text("Share something\u2026") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = { if (draft.isNotBlank()) { vm.post(draft); draft = ""; composer = false } }) { Text("Post") }
            },
            dismissButton = { TextButton(onClick = { composer = false }) { Text("Cancel") } },
        )
    }
}
