package com.talksy.app.call

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.talksy.app.AppGraph
import com.talksy.app.data.remote.dto.UserBrief
import com.talksy.app.data.socket.CallSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoTrack
import java.util.UUID

sealed interface CallState {
    object Idle : CallState
    data class Outgoing(val callId: String, val peer: UserBrief, val video: Boolean, val ringing: Boolean = false) : CallState
    data class Incoming(val callId: String, val peer: UserBrief, val video: Boolean) : CallState
    data class Active(
        val callId: String,
        val peer: UserBrief,
        val video: Boolean,
        val connected: Boolean,
        val startedAt: Long,
    ) : CallState
    data class Ended(val message: String) : CallState
}

/** Minimal adapters so we only override what we need. */
private open class SdpAdapter : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

private open class PcAdapter : PeerConnection.Observer {
    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(p0: Boolean) {}
    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidate(p0: IceCandidate?) {}
    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
    override fun onAddStream(p0: MediaStream?) {}
    override fun onRemoveStream(p0: MediaStream?) {}
    override fun onDataChannel(p0: DataChannel?) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
}

/**
 * Voice / video calls over WebRTC. Media is peer-to-peer; signalling (invite / accept / offer / answer / ICE)
 * is relayed by the Talksy backend over the existing Socket.IO connection. No third-party calling service.
 *
 * Works while the app is open (foreground). Ringing a closed app needs push notifications (FCM).
 */
object CallManager {
    val state = MutableStateFlow<CallState>(CallState.Idle)
    val muted = MutableStateFlow(false)
    val cameraOff = MutableStateFlow(false)
    val speaker = MutableStateFlow(false)
    val localVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo = MutableStateFlow<VideoTrack?>(null)

    val eglBase: EglBase by lazy { EglBase.create() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var appContext: Context

    private var factoryReady = false
    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var localAudio: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var captureHelper: SurfaceTextureHelper? = null

    private var currentCallId: String? = null
    private var isCaller = false
    private var remoteDescriptionSet = false
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var timeoutJob: Job? = null
    private var ringtone: Ringtone? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch { AppGraph.socket.callEvents.collect { handle(it) } }
    }

    // ------------------------------------------------------------------ public API

    fun startCall(peer: UserBrief, video: Boolean) {
        if (state.value != CallState.Idle && state.value !is CallState.Ended) return
        val id = UUID.randomUUID().toString().replace("-", "")
        currentCallId = id
        isCaller = true
        resetFlags()
        state.value = CallState.Outgoing(id, peer, video)
        AppGraph.socket.emit(
            "call_invite",
            JSONObject().put("call_id", id).put("to_user_id", peer.id).put("video", video),
        )
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(45_000)
            if (state.value is CallState.Outgoing) hangUp("No answer")
        }
    }

    fun accept() {
        val s = state.value as? CallState.Incoming ?: return
        stopRinging()
        currentCallId = s.callId
        isCaller = false
        state.value = CallState.Active(s.callId, s.peer, s.video, connected = false, startedAt = 0L)
        scope.launch {
            prepareMedia(s.video)
            createPeerConnection()
            // the caller sends its offer only after it sees this event, so our connection is ready for it
            AppGraph.socket.emit("call_accept", JSONObject().put("call_id", s.callId))
        }
    }

    fun decline() {
        val s = state.value as? CallState.Incoming ?: return
        AppGraph.socket.emit("call_reject", JSONObject().put("call_id", s.callId))
        cleanup("Call declined")
    }

    fun hangUp(reason: String = "Call ended") {
        currentCallId?.let { AppGraph.socket.emit("call_end", JSONObject().put("call_id", it)) }
        cleanup(reason)
    }

    fun toggleMute() {
        muted.value = !muted.value
        localAudio?.setEnabled(!muted.value)
    }

    fun toggleCamera() {
        cameraOff.value = !cameraOff.value
        localVideoTrack?.setEnabled(!cameraOff.value)
    }

    fun switchCamera() {
        capturer?.switchCamera(null)
    }

    fun toggleSpeaker() {
        speaker.value = !speaker.value
        audioManager().isSpeakerphoneOn = speaker.value
    }

    fun dismissEnded() {
        if (state.value is CallState.Ended) state.value = CallState.Idle
    }

    // ------------------------------------------------------------------ signalling

    private fun handle(sig: CallSignal) {
        val d = sig.data
        val id = d.optString("call_id")
        when (sig.event) {
            "incoming_call" -> {
                if (state.value != CallState.Idle && state.value !is CallState.Ended) {
                    AppGraph.socket.emit("call_reject", JSONObject().put("call_id", id))
                    return
                }
                val from = d.optJSONObject("from") ?: return
                val peer = UserBrief(
                    id = from.optString("id"),
                    name = from.optString("name"),
                    username = from.optString("username"),
                    picture = from.optString("picture"),
                )
                resetFlags()
                currentCallId = id
                isCaller = false
                state.value = CallState.Incoming(id, peer, d.optBoolean("video"))
                startRinging()
            }
            "call_ringing" -> {
                val s = state.value
                if (s is CallState.Outgoing && s.callId == id) state.value = s.copy(ringing = true)
            }
            "call_accepted" -> {
                val s = state.value as? CallState.Outgoing ?: return
                if (s.callId != id) return
                timeoutJob?.cancel()
                state.value = CallState.Active(s.callId, s.peer, s.video, connected = false, startedAt = 0L)
                scope.launch {
                    prepareMedia(s.video)
                    createPeerConnection()
                    createOffer(s.video)
                }
            }
            "call_rejected" -> if (id == currentCallId) cleanup("Call declined")
            "call_busy" -> if (id == currentCallId) cleanup("User is busy")
            "call_unavailable" -> if (id == currentCallId) cleanup(
                if (d.optString("reason") == "offline") "User is offline" else "User unavailable",
            )
            "call_ended" -> if (id == currentCallId) cleanup("Call ended")
            "webrtc_signal" -> if (id == currentCallId) onSignal(d)
        }
    }

    private fun sendSignal(kind: String, build: JSONObject.() -> Unit) {
        val id = currentCallId ?: return
        val payload = JSONObject().put("call_id", id).put("kind", kind)
        payload.build()
        AppGraph.socket.emit("webrtc_signal", payload)
    }

    private fun onSignal(d: JSONObject) {
        val conn = pc ?: return
        when (d.optString("kind")) {
            "offer" -> {
                val sdp = SessionDescription(SessionDescription.Type.OFFER, d.optString("sdp"))
                conn.setRemoteDescription(object : SdpAdapter() {
                    override fun onSetSuccess() {
                        scope.launch {
                            remoteDescriptionSet = true
                            drainCandidates()
                            createAnswer()
                        }
                    }
                }, sdp)
            }
            "answer" -> {
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, d.optString("sdp"))
                conn.setRemoteDescription(object : SdpAdapter() {
                    override fun onSetSuccess() {
                        scope.launch { remoteDescriptionSet = true; drainCandidates() }
                    }
                }, sdp)
            }
            "candidate" -> {
                val c = IceCandidate(d.optString("sdp_mid"), d.optInt("sdp_mline_index"), d.optString("candidate"))
                if (remoteDescriptionSet) conn.addIceCandidate(c) else pendingCandidates.add(c)
            }
        }
    }

    private fun drainCandidates() {
        val conn = pc ?: return
        pendingCandidates.forEach { conn.addIceCandidate(it) }
        pendingCandidates.clear()
    }

    // ------------------------------------------------------------------ WebRTC plumbing

    private fun ensureFactory(): PeerConnectionFactory {
        factory?.let { return it }
        if (!factoryReady) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions(),
            )
            factoryReady = true
        }
        val f = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
        factory = f
        return f
    }

    private fun prepareMedia(video: Boolean) {
        val f = ensureFactory()
        val audioSource = f.createAudioSource(MediaConstraints())
        localAudio = f.createAudioTrack("audio0", audioSource).also { it.setEnabled(!muted.value) }
        if (video) startCamera(f)
    }

    private fun startCamera(f: PeerConnectionFactory) {
        val enumerator = Camera2Enumerator(appContext)
        val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull() ?: return
        val cap = enumerator.createCapturer(name, null) ?: return
        val source = f.createVideoSource(cap.isScreencast)
        val helper = SurfaceTextureHelper.create("TalksyCapture", eglBase.eglBaseContext)
        cap.initialize(helper, appContext, source.capturerObserver)
        cap.startCapture(640, 480, 24)
        capturer = cap
        captureHelper = helper
        localVideoTrack = f.createVideoTrack("video0", source)
        localVideo.value = localVideoTrack
    }

    private suspend fun iceServers(): List<PeerConnection.IceServer> {
        val fetched = AppGraph.calls.ice().getOrNull()?.iceServers.orEmpty()
        val servers = fetched.filter { it.urls.isNotEmpty() }.map { s ->
            val b = PeerConnection.IceServer.builder(s.urls)
            if (!s.username.isNullOrBlank()) b.setUsername(s.username)
            if (!s.credential.isNullOrBlank()) b.setPassword(s.credential)
            b.createIceServer()
        }
        return servers.ifEmpty {
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        }
    }

    private suspend fun createPeerConnection() {
        val f = ensureFactory()
        val config = PeerConnection.RTCConfiguration(iceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val conn = f.createPeerConnection(config, object : PcAdapter() {
            override fun onIceCandidate(p0: IceCandidate?) {
                val c = p0 ?: return
                sendSignal("candidate") {
                    put("candidate", c.sdp).put("sdp_mid", c.sdpMid).put("sdp_mline_index", c.sdpMLineIndex)
                }
            }

            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                scope.launch {
                    when (p0) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> markConnected()
                        PeerConnection.IceConnectionState.FAILED -> hangUp("Connection lost")
                        else -> Unit
                    }
                }
            }

            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                val track = p0?.track()
                if (track is VideoTrack) scope.launch { remoteVideo.value = track }
            }
        }) ?: return
        pc = conn
        localAudio?.let { conn.addTrack(it, listOf("talksy")) }
        localVideoTrack?.let { conn.addTrack(it, listOf("talksy")) }
    }

    private fun createOffer(video: Boolean) {
        val conn = pc ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (video) "true" else "false"))
        }
        conn.createOffer(object : SdpAdapter() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                val sdp = p0 ?: return
                conn.setLocalDescription(SdpAdapter(), sdp)
                sendSignal("offer") { put("sdp", sdp.description) }
            }
        }, constraints)
    }

    private fun createAnswer() {
        val conn = pc ?: return
        conn.createAnswer(object : SdpAdapter() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                val sdp = p0 ?: return
                conn.setLocalDescription(SdpAdapter(), sdp)
                sendSignal("answer") { put("sdp", sdp.description) }
            }
        }, MediaConstraints())
    }

    private fun markConnected() {
        val s = state.value as? CallState.Active ?: return
        if (s.connected) return
        state.value = s.copy(connected = true, startedAt = System.currentTimeMillis())
        val am = audioManager()
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        speaker.value = s.video
        am.isSpeakerphoneOn = s.video
    }

    // ------------------------------------------------------------------ housekeeping

    private fun resetFlags() {
        muted.value = false
        cameraOff.value = false
        speaker.value = false
        remoteDescriptionSet = false
        pendingCandidates.clear()
    }

    private fun audioManager() = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun startRinging() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(appContext, uri).apply {
                if (Build.VERSION.SDK_INT >= 28) isLooping = true
                play()
            }
            val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 700), 0))
        }
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { (appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.cancel() }
    }

    private fun cleanup(message: String) {
        timeoutJob?.cancel()
        stopRinging()
        runCatching { capturer?.stopCapture() }
        runCatching { captureHelper?.dispose() }
        capturer = null
        captureHelper = null
        runCatching { pc?.close() }
        pc = null
        localAudio = null
        localVideoTrack = null
        localVideo.value = null
        remoteVideo.value = null
        currentCallId = null
        remoteDescriptionSet = false
        pendingCandidates.clear()
        runCatching {
            val am = audioManager()
            am.isSpeakerphoneOn = false
            am.mode = AudioManager.MODE_NORMAL
        }
        state.value = CallState.Ended(message)
        scope.launch {
            delay(1400)
            dismissEnded()
        }
    }
}
