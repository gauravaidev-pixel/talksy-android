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
}

class ChatRepository(private val api: ApiService) {
    suspend fun list() = safeCall { api.listChats() }
    suspend fun openDirect(userId: String) = safeCall { api.openDirect(DirectChatBody(userId)) }
    suspend fun createGroup(name: String, description: String?, memberIds: List<String>) =
        safeCall { api.createGroup(CreateGroupBody(name, description, memberIds)) }
    suspend fun get(chatId: String) = safeCall { api.getChat(chatId) }
    suspend fun markRead(chatId: String) = safeCall { api.markRead(chatId) }
    suspend fun leave(chatId: String, myId: String) = safeCall { api.removeMember(chatId, myId) }
}

class MessageRepository(private val api: ApiService) {
    suspend fun list(chatId: String, before: String? = null) = safeCall { api.listMessages(chatId, before = before) }
    suspend fun send(chatId: String, text: String?, imageUrl: String?, replyTo: String?, viewOnce: Boolean) =
        safeCall { api.sendMessage(chatId, SendMessageBody(text, imageUrl, replyTo, viewOnce)) }
    suspend fun edit(messageId: String, text: String) = safeCall { api.editMessage(messageId, EditBody(text)) }
    suspend fun delete(messageId: String) = safeCall { api.deleteMessage(messageId) }
    suspend fun react(messageId: String, emoji: String) = safeCall { api.react(messageId, ReactionBody(emoji)) }
    suspend fun pin(messageId: String) = safeCall { api.pin(messageId) }
}

class PollRepository(private val api: ApiService) {
    suspend fun list(chatId: String) = safeCall { api.listPolls(chatId) }
    suspend fun create(chatId: String, question: String, options: List<String>) =
        safeCall { api.createPoll(chatId, CreatePollBody(question, options)) }
    suspend fun vote(pollId: String, index: Int) = safeCall { api.vote(pollId, VoteBody(index)) }
}

class StatusRepository(private val api: ApiService) {
    suspend fun feed() = safeCall { api.statusFeed() }
    suspend fun create(text: String?, imageUrl: String?) = safeCall { api.createStatus(StatusBody(text, imageUrl)) }
    suspend fun view(id: String) = safeCall { api.viewStatus(id) }
    suspend fun delete(id: String) = safeCall { api.deleteStatus(id) }
}

/** Uploads a picked image (content:// uri) and returns the public URL. */
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
}
