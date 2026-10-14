package com.talksy.app.data.remote

import com.talksy.app.data.remote.dto.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface ApiService {

    @POST("api/auth/google")
    suspend fun googleLogin(@Body body: GoogleLoginBody): AuthResponse

    @POST("api/auth/logout")
    suspend fun logout(): SimpleResponse

    @GET("api/users/me")
    suspend fun me(): MeResponse

    @PATCH("api/users/me")
    suspend fun updateMe(@Body body: UpdateMeBody): MeResponse

    @GET("api/users/search")
    suspend fun searchUsers(@Query("q") query: String): UserListResponse

    @GET("api/chats")
    suspend fun listChats(): ChatListResponse

    @POST("api/chats/direct")
    suspend fun openDirect(@Body body: DirectChatBody): ChatResponse

    @POST("api/chats/group")
    suspend fun createGroup(@Body body: CreateGroupBody): ChatResponse

    @GET("api/chats/{id}")
    suspend fun getChat(@Path("id") chatId: String): ChatResponse

    @GET("api/chats/{id}/messages")
    suspend fun listMessages(
        @Path("id") chatId: String,
        @Query("limit") limit: Int = 50,
        @Query("before") before: String? = null,
    ): MessagesResponse

    @POST("api/chats/{id}/messages")
    suspend fun sendMessage(@Path("id") chatId: String, @Body body: SendMessageBody): MessageResponse

    @DELETE("api/messages/{id}")
    suspend fun deleteMessage(@Path("id") messageId: String): SimpleResponse

    @POST("api/messages/{id}/reaction")
    suspend fun react(@Path("id") messageId: String, @Body body: ReactionBody): SimpleResponse

    @POST("api/messages/{id}/pin")
    suspend fun pin(@Path("id") messageId: String): SimpleResponse

    @GET("api/chats/{id}/pinned")
    suspend fun pinned(@Path("id") chatId: String): MessagesResponse

    @GET("api/chats/{id}/polls")
    suspend fun listPolls(@Path("id") chatId: String): PollListResponse

    @POST("api/chats/{id}/polls")
    suspend fun createPoll(@Path("id") chatId: String, @Body body: CreatePollBody): PollResponse

    @POST("api/polls/{id}/vote")
    suspend fun vote(@Path("id") pollId: String, @Body body: VoteBody): PollResponse

    @GET("api/status")
    suspend fun statusFeed(): StatusListResponse

    @POST("api/status")
    suspend fun createStatus(@Body body: StatusBody): SimpleResponse

    @POST("api/status/{id}/view")
    suspend fun viewStatus(@Path("id") statusId: String): SimpleResponse

    @GET("api/bots")
    suspend fun listBots(): BotListResponse

    @POST("api/bots")
    suspend fun createBot(@Body body: CreateBotBody): BotResponse

    @Multipart
    @POST("api/uploads")
    suspend fun upload(@Part file: MultipartBody.Part): UploadResponse
}
