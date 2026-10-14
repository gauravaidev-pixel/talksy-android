package com.talksy.app.data.repo

import com.talksy.app.data.remote.ApiService
import com.talksy.app.data.remote.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
}

class ChatRepository(private val api: ApiService) {
    suspend fun list() = safeCall { api.listChats() }
    suspend fun openDirect(userId: String) = safeCall { api.openDirect(DirectChatBody(userId)) }
    suspend fun createGroup(name: String, description: String?, memberIds: List<String>) =
        safeCall { api.createGroup(CreateGroupBody(name, description, memberIds)) }
    suspend fun get(chatId: String) = safeCall { api.getChat(chatId) }
}

class MessageRepository(private val api: ApiService) {
    suspend fun list(chatId: String, before: String? = null) = safeCall { api.listMessages(chatId, before = before) }
    suspend fun send(chatId: String, text: String?, imageUrl: String?, replyTo: String?, viewOnce: Boolean) =
        safeCall { api.sendMessage(chatId, SendMessageBody(text, imageUrl, replyTo, viewOnce)) }
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
}

class BotRepository(private val api: ApiService) {
    suspend fun list() = safeCall { api.listBots() }
    suspend fun create(name: String, username: String, description: String?) =
        safeCall { api.createBot(CreateBotBody(name, username, description)) }
}
