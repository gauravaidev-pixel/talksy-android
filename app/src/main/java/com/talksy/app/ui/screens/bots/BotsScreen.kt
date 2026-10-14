package com.talksy.app.ui.screens.bots

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.talksy.app.AppGraph
import com.talksy.app.data.remote.dto.BotDto
import com.talksy.app.ui.components.EmptyState
import com.talksy.app.ui.components.LoadingBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BotsViewModel : ViewModel() {
    private val _state = MutableStateFlow<Pair<Boolean, List<BotDto>>>(true to emptyList())
    val state: StateFlow<Pair<Boolean, List<BotDto>>> = _state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = true to _state.value.second
            AppGraph.bots.list().onSuccess { _state.value = false to it.bots }
        }
    }

    fun create(name: String, username: String, description: String) {
        viewModelScope.launch {
            AppGraph.bots.create(name, username, description.ifBlank { null }).onSuccess { refresh() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotsScreen(vm: BotsViewModel = viewModel()) {
    val (loading, bots) by vm.state.collectAsState()
    var dialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bots") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New bot")
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> LoadingBox()
                bots.isEmpty() -> EmptyState("No bots yet", "Create a bot to automate replies.", Icons.Filled.Bolt)
                else -> LazyColumn {
                    items(bots, key = { it.id }) { bot ->
                        ListItem(
                            headlineContent = { Text(bot.name) },
                            supportingContent = { Text("@${bot.username} ${bot.description}") },
                            leadingContent = { Icon(Icons.Filled.Bolt, contentDescription = null) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }

    if (dialog) {
        var name by remember { mutableStateOf("") }
        var username by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("New bot") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.create(name, username, description); dialog = false }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } },
        )
    }
}
