package com.talksy.app.ui.screens.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talksy.app.AppGraph
import com.talksy.app.data.remote.dto.UserDto
import com.talksy.app.ui.components.Avatar
import com.talksy.app.ui.nav.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NewGroupViewModel : ViewModel() {
    private val _results = MutableStateFlow<List<UserDto>>(emptyList())
    val results: StateFlow<List<UserDto>> = _results

    fun search(q: String) {
        viewModelScope.launch {
            if (q.trim().length < 2) { _results.value = emptyList(); return@launch }
            AppGraph.users.search(q.trim()).onSuccess { _results.value = it.users }
        }
    }

    fun create(name: String, description: String, members: Set<String>, onDone: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            AppGraph.chats.createGroup(name, description.ifBlank { null }, members.toList())
                .onSuccess { onDone(it.chat.id) }
                .onFailure { onError(it.message) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupScreen(nav: NavController, vm: NewGroupViewModel = viewModel()) {
    val results by vm.results.collectAsState()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<UserDto>() }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New group") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            vm.create(name, description, selected.map { it.id }.toSet(),
                                onDone = { nav.navigate(Routes.chat(it)) { popUpTo(Routes.NEW_GROUP) { inclusive = true } } },
                                onError = { error = it })
                        },
                    ) { Text("Create") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Group name") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
            )
            if (selected.isNotEmpty()) {
                Text(
                    "Selected: ${selected.joinToString { it.name }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            OutlinedTextField(
                value = query, onValueChange = { query = it; vm.search(it) },
                placeholder = { Text("Add members") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                singleLine = true,
            )
            LazyColumn {
                items(results, key = { it.id }) { user ->
                    val isSelected = selected.any { it.id == user.id }
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { if (isSelected) selected.removeAll { it.id == user.id } else selected.add(user) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = isSelected, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Avatar(user.name, user.picture, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(user.name, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
