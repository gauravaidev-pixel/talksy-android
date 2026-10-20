package com.talksy.app.data.repo

import android.content.Context
import android.net.Uri
import com.talksy.app.data.remote.ApiService
import com.talksy.app.data.remote.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/** Turns a Retrofit call into a Result, surfacing the backend's error message. */
private val errJson = Json { ignoreUnknownKeys = true }

suspend fun <T> safeCall(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
    try {
        Result.success(block())
    } catch (e: HttpException) {
        val msg = runCatching {
            errJson.decodeFromString<SimpleResponse>(e.response()?.errorBody()?.string() ?: "").error
        }.getOrNull() ?: "Request failed (${e.code()})"
        Result.failure(ApiError(msg))
    } catch (e: Exception) {
        Result.failure(ApiError(e.message ?: "Network error"))
    }
}

class ApiError(override val message: String) : Exception(message)

class AuthRepository(private val api: ApiService) {
    suspend fun loginWithGoogle(idToken: String) = safeCall { api.googleLogin(GoogleLoginBody(idToken)) }
    suspend fun logout() = safeCall { api.logout() }
}

class UserRepository(private val api: ApiService) {
    suspend fun me() = safeCall { api.me() }
    suspend fun update(name: String?, bio: String?, username: String?, picture: String?) =
        safeCall { api.updateMe(UpdateMeBody(name, bio, username, picture)) }
    suspend fun search(q: String) = safeCall { api.searchUsers(q) }
    suspend fun get(userId: String) = safeCall { api.getUser(userId) }
    suspend fun block(userId: String) = safeCall { api.blockUser(userId) }
    suspend fun blocked() = safeCall { api.blockedUsers() }
}

class ChatRepository(private val api: ApiService) {
    suspend fun list() = safeCall { api.listChats() }
    suspend fun openDirect(userId: String) = safeCall { api.openDirect(DirectChatBody(userId)) }
    suspend fun createGroup(name: String, description: String?, memberIds: List<String>) =
        safeCall { api.createGroup(CreateGroupBody(name, description, memberIds)) }
    suspend fun get(chatId: String) = safeCall { api.getChat(chatId) }
    suspend fun markRead(chatId: String) = safeCall { api.markRead(chatId) }
    suspend fun leave(chatId: String, myId: String) = safeCall { api.removeMember(chatId, myId) }
    suspend fun openSaved() = safeCall { api.openSaved() }
    suspend fun setPinned(chatId: String, pinned: Boolean) = safeCall { api.chatSettings(chatId, ChatSettingsBody(pinned = pinned)) }
    suspend fun mute(chatId: String, hours: Int) = safeCall { api.chatSettings(chatId, ChatSettingsBody(muteHours = hours)) }
    suspend fun clear(chatId: String) = safeCall { api.clearChat(chatId) }
    suspend fun hide(chatId: String) = safeCall { api.hideChat(chatId) }
    suspend fun edit(chatId: String, name: String?, description: String?, avatar: String?) =
        safeCall { api.editChat(chatId, EditChatBody(name, description, avatar)) }
    suspend fun delete(chatId: String) = safeCall { api.deleteChat(chatId) }
    suspend fun addMembers(chatId: String, ids: List<String>) = safeCall { api.addMembers(chatId, AddMembersBody(ids)) }
    suspend fun setRole(chatId: String, userId: String, role: String) = safeCall { api.setRole(chatId, userId, RoleBody(role)) }
    suspend fun invite(chatId: String) = safeCall { api.createInvite(chatId, InviteBody()) }
    suspend fun joinByCode(code: String) = safeCall { api.joinByCode(code.trim()) }
}

class MessageRepository(private val api: ApiService) {
    suspend fun list(chatId: String, before: String? = null, peek: Boolean = false) =
        safeCall { api.listMessages(chatId, before = before, peek = if (peek) 1 else null) }
    suspend fun send(
        chatId: String, text: String?, imageUrl: String?, replyTo: String?, viewOnce: Boolean,
        autoDelete: String = "never",
    ) = safeCall { api.sendMessage(chatId, SendMessageBody(text, imageUrl, replyTo, viewOnce, autoDelete)) }
    suspend fun edit(messageId: String, text: String) = safeCall { api.editMessage(messageId, EditBody(text)) }
    suspend fun delete(messageId: String) = safeCall { api.deleteMessage(messageId) }
    suspend fun react(messageId: String, emoji: String) = safeCall { api.react(messageId, ReactionBody(emoji)) }
    suspend fun pin(messageId: String) = safeCall { api.pin(messageId) }
    suspend fun report(messageId: String, reason: String) = safeCall { api.reportMessage(messageId, ReportBody(reason)) }
    suspend fun search(chatId: String, q: String) = safeCall { api.searchMessages(chatId, q) }
}

class PollRepository(private val api: ApiService) {
    suspend fun list(chatId: String) = safeCall { api.listPolls(chatId) }
    suspend fun create(chatId: String, question: String, options: List<String>) =
        safeCall { api.createPoll(chatId, CreatePollBody(question, options)) }
    suspend fun vote(pollId: String, index: Int) = safeCall { api.vote(pollId, VoteBody(index)) }
}

class CallRepository(private val api: ApiService) {
    suspend fun history() = safeCall { api.callHistory() }
    suspend fun ice() = safeCall { api.iceServers() }
}

class StatusRepository(private val api: ApiService) {
    suspend fun feed() = safeCall { api.statusFeed() }
    suspend fun create(text: String?, imageUrl: String?) = safeCall { api.createStatus(StatusBody(text, imageUrl)) }
    suspend fun view(id: String) = safeCall { api.viewStatus(id) }
    suspend fun delete(id: String) = safeCall { api.deleteStatus(id) }
    suspend fun viewers(id: String) = safeCall { api.statusViewers(id) }
}

/** Uploads a picked image (content:// uri) or a recorded voice file and returns the public URL. */
class UploadRepository(private val api: ApiService, private val context: Context) {
    suspend fun image(uri: Uri): Result<String> = safeCall {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw ApiError("Could not read the selected file")
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            else -> "jpg"
        }
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", "image.$ext", body)
        val res = api.upload(part)
        if (!res.success || res.url.isBlank()) throw ApiError(res.error ?: "Upload failed")
        res.url
    }

    /** Upload a local audio file (voice message). */
    suspend fun voice(file: java.io.File): Result<String> = safeCall {
        val bytes = file.readBytes()
        val body = bytes.toRequestBody("audio/mp4".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", "voice.m4a", body)
        val res = api.upload(part)
        if (!res.success || res.url.isBlank()) throw ApiError(res.error ?: "Upload failed")
        res.url
    }
}
