package com.talksy.app.ui.screens.status

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.talksy.app.AppGraph
import com.talksy.app.data.remote.dto.StatusDto
import com.talksy.app.data.remote.dto.StatusViewerDto
import com.talksy.app.ui.components.Avatar
import com.talksy.app.ui.components.EmptyState
import com.talksy.app.ui.components.LoadingBox
import com.talksy.app.ui.components.SectionLabel
import com.talksy.app.util.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatusUiState(
    val loading: Boolean = true,
    val statuses: List<StatusDto> = emptyList(),
    val uploading: Boolean = false,
    val attachment: String? = null,
    val message: String? = null,
)

class StatusViewModel : ViewModel() {
    private val _state = MutableStateFlow(StatusUiState())
    val state: StateFlow<StatusUiState> = _state

    init {
        // paint the last known statuses instantly, then refresh in the background
        val cached = AppGraph.disk.loadStatuses()
        if (cached.isNotEmpty()) _state.update { it.copy(loading = false, statuses = cached) }
        refresh()
    }

    /** Download story images / avatars ahead of time so the viewer opens without a spinner. */
    private fun prefetch(list: List<StatusDto>) {
        val ctx = AppGraph.appContext
        list.filter { !it.viewed || it.isMine }.take(12).forEach { st ->
            if (st.imageUrl.isNotBlank()) ctx.imageLoader.enqueue(ImageRequest.Builder(ctx).data(st.imageUrl).build())
            if (st.user.picture.isNotBlank()) ctx.imageLoader.enqueue(ImageRequest.Builder(ctx).data(st.user.picture).build())
        }
    }

    fun refresh() {
        viewModelScope.launch {
            AppGraph.statuses.feed()
                .onSuccess { res ->
                    AppGraph.disk.saveStatuses(res.statuses)
                    _state.update { it.copy(loading = false, statuses = res.statuses) }
                    prefetch(res.statuses)
                }
                .onFailure { e -> _state.update { it.copy(loading = false, message = e.message) } }
        }
    }

    fun attach(uri: Uri) {
        _state.update { it.copy(uploading = true) }
        viewModelScope.launch {
            AppGraph.uploads.image(uri)
                .onSuccess { url -> _state.update { it.copy(uploading = false, attachment = url) } }
                .onFailure { e -> _state.update { it.copy(uploading = false, message = e.message) } }
        }
    }

    fun clearAttachment() = _state.update { it.copy(attachment = null) }

    fun post(text: String, onDone: () -> Unit) {
        val image = _state.value.attachment
        if (text.isBlank() && image == null) return
        viewModelScope.launch {
            AppGraph.statuses.create(text.trim().ifBlank { null }, image)
                .onSuccess { _state.update { it.copy(attachment = null) }; refresh(); onDone() }
                .onFailure { e -> _state.update { it.copy(message = e.message) } }
        }
    }

    fun markViewed(id: String) {
        // update the ring immediately; the server call happens in the background
        _state.update { st -> st.copy(statuses = st.statuses.map { if (it.id == id) it.copy(viewed = true) else it }) }
        AppGraph.disk.saveStatuses(_state.value.statuses)
        viewModelScope.launch { AppGraph.statuses.view(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            AppGraph.statuses.delete(id).onSuccess { refresh() }
        }
    }

    fun loadViewers(id: String, onResult: (List<StatusViewerDto>) -> Unit) {
        viewModelScope.launch {
            AppGraph.statuses.viewers(id).onSuccess { onResult(it.viewers) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

private val StoryGradients = listOf(
    listOf(Color(0xFF2B5278), Color(0xFF7C5CFF)),
    listOf(Color(0xFFFF516A), Color(0xFFFF885E)),
    listOf(Color(0xFF28C9B7), Color(0xFF2A9EF1)),
    listOf(Color(0xFF54CB68), Color(0xFF2A9EF1)),
    listOf(Color(0xFFD669ED), Color(0xFF665FFF)),
)

private fun storyGradient(id: String): Brush {
    val h = id.hashCode().let { if (it == Int.MIN_VALUE) 0 else if (it < 0) -it else it }
    return Brush.linearGradient(StoryGradients[h % StoryGradients.size])
}

private fun oldestFirst(list: List<StatusDto>) = list.sortedBy { Time.parse(it.createdAt)?.time ?: 0L }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(vm: StatusViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var composing by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<List<StatusDto>?>(null) }
    var viewers by remember { mutableStateOf<List<StatusViewerDto>?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    val grouped = state.statuses.groupBy { it.user.id }
    val mine = state.statuses.filter { it.isMine }
    val others = grouped.filter { entry -> entry.value.none { it.isMine } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                "Status",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 10.dp),
            )
            when {
                state.loading -> com.talksy.app.ui.components.SkeletonList()
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    item {
                        StatusRow(
                            name = "My status",
                            picture = null,
                            ringed = mine.isNotEmpty(),
                            seen = true,
                            subtitle = if (mine.isNotEmpty()) "${mine.size} update${if (mine.size == 1) "" else "s"} \u2022 ${Time.listLabel(mine.first().createdAt)}"
                            else "Tap to add a status update",
                            addBadge = mine.isEmpty(),
                            onClick = { if (mine.isNotEmpty()) viewing = oldestFirst(mine) else composing = true },
                        )
                    }
                    item { SectionLabel("Recent updates") }
                    if (others.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().height(260.dp)) {
                                EmptyState("No updates yet", "Status updates from your chats appear here for 24 hours.", Icons.Filled.DonutLarge)
                            }
                        }
                    }
                    items(others.entries.toList(), key = { it.key }) { entry ->
                        val list = entry.value
                        val first = list.first()
                        StatusRow(
                            name = first.user.name,
                            picture = first.user.picture,
                            ringed = true,
                            seen = list.all { it.viewed },
                            subtitle = Time.listLabel(first.createdAt) + if (list.size > 1) " \u2022 ${list.size} updates" else "",
                            addBadge = false,
                            onClick = { viewing = oldestFirst(list) },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { composing = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Edit, contentDescription = "New status") }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (composing) {
        ComposeStatusSheet(
            state = state,
            onAttach = vm::attach,
            onClearAttachment = vm::clearAttachment,
            onPost = { text -> vm.post(text) { composing = false } },
            onDismiss = { composing = false; vm.clearAttachment() },
        )
    }

    viewing?.let { list ->
        StatusViewer(
            statuses = list,
            onViewed = vm::markViewed,
            onDelete = { vm.delete(it); viewing = null },
            onShowViewers = { id -> vm.loadViewers(id) { viewers = it } },
            onClose = { viewing = null; vm.refresh() },
        )
    }

    viewers?.let { list ->
        AlertDialog(
            onDismissRequest = { viewers = null },
            title = { Text("Viewed by ${list.size}") },
            text = {
                if (list.isEmpty()) {
                    Text("Nobody has seen this yet.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(list, key = { it.user.id }) { v ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(v.user.name, v.user.picture, size = 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(v.user.name, style = MaterialTheme.typography.titleSmall)
                                    Text(Time.listLabel(v.viewedAt) + "  " + Time.clock(v.viewedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewers = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun StatusRow(
    name: String,
    picture: String?,
    ringed: Boolean,
    seen: Boolean,
    subtitle: String,
    addBadge: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ring: Brush = when {
            !ringed -> SolidColor(Color.Transparent)
            seen -> SolidColor(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            else -> Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFF7C5CFF), MaterialTheme.colorScheme.primary))
        }
        Box {
            Box(Modifier.size(62.dp).border(3.dp, ring, CircleShape).padding(5.dp), contentAlignment = Alignment.Center) {
                Avatar(name, picture, size = 50.dp)
            }
            if (addBadge) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(22.dp)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeStatusSheet(
    state: StatusUiState,
    onAttach: (Uri) -> Unit,
    onClearAttachment: () -> Unit,
    onPost: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) onAttach(uri) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).navigationBarsPadding().imePadding()) {
            Text("New status", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            if (state.attachment != null) {
                Box {
                    AsyncImage(
                        state.attachment, null,
                        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    IconButton(onClick = onClearAttachment, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(10.dp))
            } else {
                Box(
                    Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp))
                        .background(storyGradient(text.take(4) + "x")),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text.ifBlank { "Your text appears here" },
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (state.attachment != null) "Add a caption\u2026" else "What's on your mind?") },
                maxLines = 4,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !state.uploading) {
                    if (state.uploading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Image, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Photo")
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onPost(text) },
                    enabled = !state.uploading && (text.isNotBlank() || state.attachment != null),
                ) { Text("Post") }
            }
        }
    }
}

@Composable
private fun StatusViewer(
    statuses: List<StatusDto>,
    onViewed: (String) -> Unit,
    onDelete: (String) -> Unit,
    onShowViewers: (String) -> Unit,
    onClose: () -> Unit,
) {
    var index by remember { mutableStateOf(0) }
    val progress = remember { Animatable(0f) }
    val current = statuses[index.coerceIn(0, statuses.lastIndex)]

    LaunchedEffect(index) {
        if (!current.isMine) onViewed(current.id)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 5000, easing = LinearEasing))
        if (index < statuses.lastIndex) index++ else onClose()
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (current.imageUrl.isNotBlank()) {
                AsyncImage(current.imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                if (current.text.isNotBlank()) {
                    Text(
                        current.text,
                        color = Color.White,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f)).navigationBarsPadding().padding(20.dp),
                    )
                }
            } else {
                Box(Modifier.fillMaxSize().background(storyGradient(current.id)), contentAlignment = Alignment.Center) {
                    Text(
                        current.text,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 36.sp,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }

            // tap zones: left = previous, right = next
            Row(Modifier.fillMaxSize()) {
                val none = remember { MutableInteractionSource() }
                Box(Modifier.weight(0.35f).fillMaxHeight().clickable(interactionSource = none, indication = null) {
                    if (index > 0) index-- else index = 0
                })
                Box(Modifier.weight(0.65f).fillMaxHeight().clickable(interactionSource = none, indication = null) {
                    if (index < statuses.lastIndex) index++ else onClose()
                })
            }

            if (current.isMine) {
                Row(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.5f))
                        .clickable { onShowViewers(current.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${current.viewCount} views", color = Color.White)
                }
            }

            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    statuses.forEachIndexed { i, _ ->
                        LinearProgressIndicator(
                            progress = { if (i < index) 1f else if (i == index) progress.value else 0f },
                            modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(current.user.name, current.user.picture, size = 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current.user.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(Time.listLabel(current.createdAt) + "  " + Time.clock(current.createdAt), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                    }
                    if (current.isMine) {
                        IconButton(onClick = { onDelete(current.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete status", tint = Color.White)
                        }
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White) }
                }
            }
        }
    }
}
