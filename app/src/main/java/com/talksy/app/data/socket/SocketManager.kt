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

/** Realtime transport. Wraps socket.io-client and re-exposes events as flows. */
class SocketManager {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private var socket: Socket? = null

    private val _messages = MutableSharedFlow<MessageDto>(extraBufferCapacity = 64)
    val messages: SharedFlow<MessageDto> = _messages

    private val _typing = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 32)
    val typing: SharedFlow<Pair<String, String>> = _typing   // (chatId, userId)

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
            _typing.tryEmit(obj.optString("chat_id") to obj.optString("user_id"))
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

    private fun emitPoll(raw: Any) {
        val obj = raw as? JSONObject ?: return
        val chatId = obj.optString("chat_id")
        val pollObj = obj.optJSONObject("poll") ?: return
        runCatching { json.decodeFromString<PollDto>(pollObj.toString()) }
            .onSuccess { _polls.tryEmit(chatId to it) }
    }
}
