package com.talksy.app.data.socket

import com.talksy.app.BuildConfig
import com.talksy.app.data.remote.dto.MessageDto
import com.talksy.app.data.remote.dto.PollDto
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.net.URI

/** Any call / WebRTC signalling event coming from the server. */
data class CallSignal(val event: String, val data: JSONObject)

data class ReadEvent(val chatId: String, val userId: String)

data class TypingEvent(val chatId: String, val userId: String, val isTyping: Boolean)

/** Realtime transport. Wraps socket.io-client and re-exposes events as flows. */
class SocketManager {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private var socket: Socket? = null

    private val _messages = MutableSharedFlow<MessageDto>(extraBufferCapacity = 64)
    val messages: SharedFlow<MessageDto> = _messages

    private val _typing = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 32)
    val typing: SharedFlow<TypingEvent> = _typing

    private val _reads = MutableSharedFlow<ReadEvent>(extraBufferCapacity = 32)
    val reads: SharedFlow<ReadEvent> = _reads

    private val _callEvents = MutableSharedFlow<CallSignal>(extraBufferCapacity = 128)
    val callEvents: SharedFlow<CallSignal> = _callEvents

    fun emit(event: String, data: JSONObject) {
        socket?.emit(event, data)
    }

    /** Fired when the chat list itself changed (added to a group, removed, renamed, members changed). */
    private val _chatsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val chatsChanged: SharedFlow<Unit> = _chatsChanged

    private val _polls = MutableSharedFlow<Pair<String, PollDto>>(extraBufferCapacity = 32)
    val polls: SharedFlow<Pair<String, PollDto>> = _polls

    private val _presence = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 32)
    val presence: SharedFlow<Pair<String, Boolean>> = _presence

    private val _deleted = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val deleted: SharedFlow<String> = _deleted

    fun connect(token: String) {
        disconnect()
        val base = BuildConfig.BASE_URL.trimEnd('/')
        val options = IO.Options().apply {
            auth = mapOf("token" to token)
            transports = arrayOf("websocket")
            reconnection = true
            forceNew = true
        }
        val s = IO.socket(URI.create(base), options)
        s.on(Socket.EVENT_CONNECT) { }
        s.on("new_message") { args -> args.firstOrNull()?.let { emitMessage(it) } }
        s.on("message_updated") { args -> args.firstOrNull()?.let { emitMessage(it) } }
        s.on("message_deleted") { args ->
            val obj = args.firstOrNull() as? JSONObject ?: return@on
            _deleted.tryEmit(obj.optString("id"))
        }
        s.on("poll_created") { args -> emitPoll(args.firstOrNull()) }
        s.on("poll_updated") { args -> emitPoll(args.firstOrNull()) }
        s.on("typing") { args ->
            val obj = args.firstOrNull() as? JSONObject ?: return@on
            _typing.tryEmit(
                TypingEvent(obj.optString("chat_id"), obj.optString("user_id"), obj.optBoolean("is_typing", true)),
            )
        }
        s.on("read") { args ->
            val obj = args.firstOrNull() as? JSONObject ?: return@on
            _reads.tryEmit(ReadEvent(obj.optString("chat_id"), obj.optString("user_id")))
        }
        for (event in listOf(
            "incoming_call", "call_ringing", "call_accepted", "call_rejected", "call_ended",
            "call_busy", "call_unavailable", "webrtc_signal",
        )) {
            s.on(event) { args ->
                (args.firstOrNull() as? JSONObject)?.let { _callEvents.tryEmit(CallSignal(event, it)) }
            }
        }
        for (event in listOf("chat_added", "chat_removed", "chat_updated", "members_changed")) {
            s.on(event) { _chatsChanged.tryEmit(Unit) }
        }
        s.on("presence") { args ->
            val obj = args.firstOrNull() as? JSONObject ?: return@on
            _presence.tryEmit(obj.optString("user_id") to obj.optBoolean("is_online"))
        }
        s.connect()
        socket = s
    }

    fun disconnect() {
        socket?.let { if (it.connected()) it.disconnect(); it.off() }
        socket = null
    }

    fun joinChat(chatId: String) = socket?.emit("join_chat", JSONObject().put("chat_id", chatId))
    fun leaveChat(chatId: String) = socket?.emit("leave_chat", JSONObject().put("chat_id", chatId))
    fun typing(chatId: String, isTyping: Boolean) =
        socket?.emit("typing", JSONObject().put("chat_id", chatId).put("is_typing", isTyping))
    fun markRead(chatId: String, messageId: String) =
        socket?.emit("mark_read", JSONObject().put("chat_id", chatId).put("message_id", messageId))

    private fun emitMessage(raw: Any) {
        val obj = raw as? JSONObject ?: return
        runCatching { json.decodeFromString<MessageDto>(obj.toString()) }
            .onSuccess { _messages.tryEmit(it) }
    }

    private fun emitPoll(raw: Any?) {
        val obj = raw as? JSONObject ?: return
        val chatId = obj.optString("chat_id")
        val pollObj = obj.optJSONObject("poll") ?: return
        runCatching { json.decodeFromString<PollDto>(pollObj.toString()) }
            .onSuccess { _polls.tryEmit(chatId to it) }
    }
}
