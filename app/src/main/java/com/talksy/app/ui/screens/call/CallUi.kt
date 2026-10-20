package com.talksy.app.ui.screens.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.talksy.app.call.CallManager
import com.talksy.app.call.CallState
import com.talksy.app.data.remote.dto.UserBrief
import com.talksy.app.ui.components.Avatar
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** Returns a function that asks for mic (+camera) permission when needed, then starts the call. */
@Composable
fun rememberCallLauncher(): (UserBrief, Boolean) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Pair<UserBrief, Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val p = pending
        pending = null
        if (p != null && result[Manifest.permission.RECORD_AUDIO] == true && (!p.second || result[Manifest.permission.CAMERA] == true)) {
            CallManager.startCall(p.first, p.second)
        }
    }
    return { peer, video ->
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (video) add(Manifest.permission.CAMERA)
        }
        if (needed.all { granted(context, it) }) {
            CallManager.startCall(peer, video)
        } else {
            pending = peer to video
            launcher.launch(needed.toTypedArray())
        }
    }
}

/** Full-screen call UI, drawn above the whole app whenever a call is ringing / active. */
@Composable
fun CallOverlay() {
    val state by CallManager.state.collectAsState()
    if (state is CallState.Idle) return

    val context = LocalContext.current
    val s = state
    val video = when (s) {
        is CallState.Incoming -> s.video
        is CallState.Outgoing -> s.video
        is CallState.Active -> s.video
        else -> false
    }
    val acceptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true && (!video || result[Manifest.permission.CAMERA] == true)) {
            CallManager.accept()
        } else {
            CallManager.decline()
        }
    }
    val onAccept = {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (video) add(Manifest.permission.CAMERA)
        }
        if (needed.all { granted(context, it) }) CallManager.accept() else acceptLauncher.launch(needed.toTypedArray())
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E1621), Color(0xFF17344F), Color(0xFF0E1621))))
            .clickable(enabled = false) {},
    ) {
        when (s) {
            is CallState.Incoming -> RingingUi(
                peer = s.peer,
                subtitle = if (s.video) "Incoming video call" else "Incoming voice call",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(72.dp)) {
                    RoundButton(Icons.Filled.CallEnd, Color(0xFFE5484D), "Decline") { CallManager.decline() }
                    RoundButton(if (s.video) Icons.Filled.Videocam else Icons.Filled.Call, Color(0xFF2EBD59), "Accept", onClick = onAccept)
                }
            }
            is CallState.Outgoing -> RingingUi(
                peer = s.peer,
                subtitle = if (s.ringing) "Ringing\u2026" else "Calling\u2026",
            ) {
                RoundButton(Icons.Filled.CallEnd, Color(0xFFE5484D), "Cancel") { CallManager.hangUp("Call cancelled") }
            }
            is CallState.Active -> ActiveUi(s)
            is CallState.Ended -> RingingUi(peer = null, subtitle = s.message) {}
            else -> Unit
        }
    }
}

@Composable
private fun RingingUi(peer: UserBrief?, subtitle: String, controls: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        if (peer != null) {
            Avatar(peer.name, peer.picture, size = 132.dp)
            Spacer(Modifier.height(24.dp))
            Text(peer.name, color = Color.White, fontSize = 28.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
        Text(subtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 17.sp)
        Spacer(Modifier.weight(1f))
        controls()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ActiveUi(s: CallState.Active) {
    val remote by CallManager.remoteVideo.collectAsState()
    val local by CallManager.localVideo.collectAsState()
    val muted by CallManager.muted.collectAsState()
    val cameraOff by CallManager.cameraOff.collectAsState()
    val speaker by CallManager.speaker.collectAsState()

    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(s.connected, s.startedAt) {
        while (s.connected) {
            seconds = ((System.currentTimeMillis() - s.startedAt) / 1000L).toInt()
            delay(500)
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (s.video && remote != null) {
            VideoView(remote, Modifier.fillMaxSize())
        }
        // local preview (picture-in-picture)
        if (s.video && local != null && !cameraOff) {
            VideoView(
                local, mirror = true, overlay = true,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp)
                    .size(width = 110.dp, height = 160.dp).clip(RoundedCornerShape(16.dp)),
            )
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!(s.video && remote != null)) {
                Spacer(Modifier.height(56.dp))
                Avatar(s.peer.name, s.peer.picture, size = 120.dp)
                Spacer(Modifier.height(20.dp))
            }
            Text(s.peer.name, color = Color.White, fontSize = 24.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                if (!s.connected) "Connecting\u2026" else String.format("%02d:%02d", seconds / 60, seconds % 60),
                color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp,
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleButton(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, muted, "Mute") { CallManager.toggleMute() }
                ToggleButton(Icons.Filled.VolumeUp, speaker, "Speaker") { CallManager.toggleSpeaker() }
                if (s.video) {
                    ToggleButton(if (cameraOff) Icons.Filled.VideocamOff else Icons.Filled.Videocam, cameraOff, "Camera") { CallManager.toggleCamera() }
                    ToggleButton(Icons.Filled.FlipCameraAndroid, false, "Flip") { CallManager.switchCamera() }
                }
                RoundButton(Icons.Filled.CallEnd, Color(0xFFE5484D), null, size = 56.dp) { CallManager.hangUp() }
            }
        }
    }
}

@Composable
private fun ToggleButton(icon: ImageVector, active: Boolean, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).clip(CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = 0.18f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = if (active) Color(0xFF17344F) else Color.White)
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, color: Color, label: String?, size: androidx.compose.ui.unit.Dp = 68.dp, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size).clip(CircleShape).background(color).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(size * 0.45f)) }
        if (label != null) {
            Spacer(Modifier.height(8.dp))
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
    }
}

/** A WebRTC video surface. Attaches / detaches the track as it changes. */
@Composable
fun VideoView(track: VideoTrack?, modifier: Modifier, mirror: Boolean = false, overlay: Boolean = false) {
    val holder = remember { arrayOfNulls<SurfaceViewRenderer>(1) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).also { view ->
                view.init(CallManager.eglBase.eglBaseContext, null)
                view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                view.setEnableHardwareScaler(true)
                view.setMirror(mirror)
                if (overlay) view.setZOrderMediaOverlay(true)
                holder[0] = view
            }
        },
    )
    DisposableEffect(track) {
        val view = holder[0]
        if (view != null && track != null) track.addSink(view)
        onDispose { view?.let { track?.removeSink(it) } }
    }
    DisposableEffect(Unit) {
        onDispose { holder[0]?.release(); holder[0] = null }
    }
}
