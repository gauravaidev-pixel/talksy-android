package com.talksy.app.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
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
import com.talksy.app.ui.components.EmptyState
import com.talksy.app.ui.components.LoadingBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlockedState(val loading: Boolean = true, val users: List<UserDto> = emptyList(), val message: String? = null)

class BlockedViewModel : ViewModel() {
    private val _state = MutableStateFlow(BlockedState())
    val state: StateFlow<BlockedState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            AppGraph.users.blocked()
                .onSuccess { res -> _state.update { it.copy(loading = false, users = res.users) } }
                .onFailure { e -> _state.update { it.copy(loading = false, message = e.message) } }
        }
    }

    /** The backend toggles: calling block on an already-blocked user unblocks them. */
    fun unblock(user: UserDto) {
        viewModelScope.launch {
            AppGraph.users.block(user.id)
                .onSuccess { load() }
                .onFailure { e -> _state.update { it.copy(message = e.message) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedScreen(nav: NavController, vm: BlockedViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Blocked users") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingBox()
                state.users.isEmpty() -> EmptyState("No blocked users", "People you block will appear here.", Icons.Filled.Block)
                else -> LazyColumn {
                    items(state.users, key = { it.id }) { user ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(user.name, user.picture, size = 46.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user.name, style = MaterialTheme.typography.titleMedium)
                                Text("@${user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { vm.unblock(user) }) { Text("Unblock") }
                        }
                    }
                }
            }
        }
    }
}
