package com.talksy.app.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talksy.app.AppGraph
import com.talksy.app.BuildConfig
import com.talksy.app.data.remote.dto.UserDto
import com.talksy.app.ui.components.Avatar
import com.talksy.app.ui.components.LoadingBox
import com.talksy.app.ui.components.SectionCard
import com.talksy.app.ui.components.SectionLabel
import com.talksy.app.ui.nav.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: UserDto? = null,
    val saving: Boolean = false,
    val uploading: Boolean = false,
    val picture: String? = null,
    val message: String? = null,
    val saved: Boolean = false,
)

class ProfileViewModel : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            AppGraph.users.me().onSuccess { res -> _state.update { it.copy(user = res.user, picture = res.user.picture) } }
        }
    }

    fun uploadAvatar(uri: Uri) {
        _state.update { it.copy(uploading = true) }
        viewModelScope.launch {
            AppGraph.uploads.image(uri)
                .onSuccess { url -> _state.update { it.copy(uploading = false, picture = url) } }
                .onFailure { e -> _state.update { it.copy(uploading = false, message = e.message) } }
        }
    }

    fun save(name: String, username: String, bio: String) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            AppGraph.users.update(name.trim(), bio.trim(), username.trim(), _state.value.picture)
                .onSuccess { res -> _state.update { it.copy(saving = false, user = res.user, saved = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, message = e.message) } }
        }
    }

    fun setTheme(mode: String) {
        viewModelScope.launch { AppGraph.session.setTheme(mode) }
    }

    fun logout() {
        viewModelScope.launch {
            AppGraph.auth.logout()
            AppGraph.socket.disconnect()
            AppGraph.session.clear()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

// ------------------------------------------------------------------ "You" tab

@Composable
fun ProfileScreen(nav: NavController, vm: ProfileViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val theme by AppGraph.session.themeFlow.collectAsState(initial = "system")
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            val user = state.user
            if (user == null) {
                Box(Modifier.fillMaxWidth().height(240.dp)) { LoadingBox() }
            } else {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(user.name, user.picture, size = 104.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(user.name, style = MaterialTheme.typography.titleLarge)
                    Text("@${user.username}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                    if (user.bio.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(user.bio, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(18.dp))
                    FilledTonalButton(onClick = { nav.navigate(Routes.EDIT_PROFILE) }) {
                        Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Edit profile")
                    }
                }
            }
        }

        SectionLabel("Appearance")
        SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ThemeChoice("System", Icons.Filled.SettingsBrightness, theme == "system", Modifier.weight(1f)) { vm.setTheme("system") }
                ThemeChoice("Light", Icons.Filled.LightMode, theme == "light", Modifier.weight(1f)) { vm.setTheme("light") }
                ThemeChoice("Night", Icons.Filled.DarkMode, theme == "dark", Modifier.weight(1f)) { vm.setTheme("dark") }
            }
        }

        SectionLabel("About")
        SectionCard {
            InfoRow("Server", BuildConfig.BASE_URL)
            Spacer(Modifier.height(10.dp))
            InfoRow("Version", BuildConfig.VERSION_NAME)
        }

        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(18.dp)).clickable { confirmLogout = true },
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(14.dp))
                Text("Log out", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = { Text("You can sign in again with Google anytime.") },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; vm.logout() }) {
                    Text("Log out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ThemeChoice(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon, null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

// ------------------------------------------------------------------ edit profile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(nav: NavController, vm: ProfileViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val user = state.user
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) vm.uploadAvatar(uri) }

    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    LaunchedEffect(state.saved) { if (state.saved) nav.popBackStack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Edit profile") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (user == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        var name by remember(user.id) { mutableStateOf(user.name) }
        var username by remember(user.id) { mutableStateOf(user.username) }
        var bio by remember(user.id) { mutableStateOf(user.bio) }

        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.clickable { picker.launch("image/*") }) {
                Avatar(name.ifBlank { user.name }, state.picture, size = 112.dp)
                Box(
                    Modifier.align(Alignment.BottomEnd).size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.uploading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    else Icon(Icons.Filled.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Tap the photo to change it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = username, onValueChange = { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' } },
                label = { Text("Username") }, prefix = { Text("@") },
                supportingText = { Text("3\u201330 characters: a\u2013z, 0\u20139 and underscore") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = bio, onValueChange = { bio = it },
                label = { Text("Bio") }, modifier = Modifier.fillMaxWidth(), maxLines = 4,
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { vm.save(name, username, bio) },
                enabled = !state.saving && !state.uploading && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else {
                    Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save changes")
                }
            }
        }
    }
}
