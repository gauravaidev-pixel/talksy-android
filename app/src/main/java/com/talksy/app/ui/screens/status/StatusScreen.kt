package com.talksy.app.ui.screens.status

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.talksy.app.AppGraph
import com.talksy.app.data.remote.dto.StatusDto
import com.talksy.app.ui.components.Avatar
import com.talksy.app.ui.components.EmptyState
import com.talksy.app.ui.components.LoadingBox
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(vm: StatusViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val loading = state.first
    val statuses = state.second
    var composer by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Status") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { composer = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New status")
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> LoadingBox()
                statuses.isEmpty() -> EmptyState("No status updates", "Status disappears after 24 hours.", Icons.Filled.DonutLarge)
                else -> LazyColumn {
                    items(statuses, key = { it.id }) { s ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(s.user.name, s.user.picture, size = 46.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.user.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    s.text.ifBlank { "Photo" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(Time.short(s.createdAt), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }

    if (composer) {
        AlertDialog(
            onDismissRequest = { composer = false },
            title = { Text("New status") },
            text = {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it },
                    placeholder = { Text("Share something...") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.post(draft); draft = ""; composer = false }) { Text("Post") }
            },
            dismissButton = { TextButton(onClick = { composer = false }) { Text("Cancel") } },
        )
    }
}
