package com.talksy.app.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import com.talksy.app.ui.components.LoadingBox
import com.talksy.app.ui.nav.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    private val _user = MutableStateFlow<UserDto?>(null)
    val user: StateFlow<UserDto?> = _user

    init { load() }

    fun load() {
        viewModelScope.launch { AppGraph.users.me().onSuccess { _user.value = it.user } }
    }

    fun save(name: String, username: String, bio: String) {
        viewModelScope.launch {
            AppGraph.users.update(name, bio, username, null).onSuccess { _user.value = it.user }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(nav: NavController, vm: ProfileViewModel = viewModel()) {
    val user by vm.user.collectAsState()
    val current = user

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        if (current == null) {
            LoadingBox(Modifier.padding(padding))
        } else {
            var name by remember(current.id) { mutableStateOf(current.name) }
            var username by remember(current.id) { mutableStateOf(current.username) }
            var bio by remember(current.id) { mutableStateOf(current.bio) }

            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(current.name, current.picture, size = 96.dp, online = current.isOnline)
                Spacer(Modifier.height(12.dp))
                Text(current.name, style = MaterialTheme.typography.titleLarge)
                Text("@${current.username}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = username, onValueChange = { username = it },
                    label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = bio, onValueChange = { bio = it },
                    label = { Text("Bio") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { vm.save(name, username, bio) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Save changes") }
            }
        }
    }
}
