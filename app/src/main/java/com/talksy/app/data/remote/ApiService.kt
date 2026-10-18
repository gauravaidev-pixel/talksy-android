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
        @Query("peek") peek: Int? = null,
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

    @DELETE("api/status/{id}")
    suspend fun deleteStatus(@Path("id") statusId: String): SimpleResponse

    @PATCH("api/messages/{id}")
    suspend fun editMessage(@Path("id") messageId: String, @Body body: EditBody): MessageResponse

    @POST("api/chats/{id}/read")
    suspend fun markRead(@Path("id") chatId: String): SimpleResponse

    @GET("api/users/{id}")
    suspend fun getUser(@Path("id") userId: String): MeResponse

    @POST("api/users/{id}/block")
    suspend fun blockUser(@Path("id") userId: String): SimpleResponse

    @DELETE("api/chats/{id}/members/{uid}")
    suspend fun removeMember(@Path("id") chatId: String, @Path("uid") userId: String): SimpleResponse

    @POST("api/chats/saved")
    suspend fun openSaved(): ChatResponse

    @PATCH("api/chats/{id}/settings")
    suspend fun chatSettings(@Path("id") chatId: String, @Body body: ChatSettingsBody): SimpleResponse

    @POST("api/chats/{id}/clear")
    suspend fun clearChat(@Path("id") chatId: String): SimpleResponse

    @POST("api/chats/{id}/hide")
    suspend fun hideChat(@Path("id") chatId: String): SimpleResponse

    @GET("api/chats/{id}/search")
    suspend fun searchMessages(@Path("id") chatId: String, @Query("q") query: String): MessagesResponse

    @POST("api/messages/{id}/report")
    suspend fun reportMessage(@Path("id") messageId: String, @Body body: ReportBody): SimpleResponse

    @GET("api/users/blocked")
    suspend fun blockedUsers(): UserListResponse

    @PATCH("api/chats/{id}")
    suspend fun editChat(@Path("id") chatId: String, @Body body: EditChatBody): ChatResponse

    @DELETE("api/chats/{id}")
    suspend fun deleteChat(@Path("id") chatId: String): SimpleResponse

    @POST("api/chats/{id}/members")
    suspend fun addMembers(@Path("id") chatId: String, @Body body: AddMembersBody): SimpleResponse

    @PATCH("api/chats/{id}/members/{uid}")
    suspend fun setRole(@Path("id") chatId: String, @Path("uid") userId: String, @Body body: RoleBody): SimpleResponse

    @POST("api/chats/join/{code}")
    suspend fun joinByCode(@Path("code") code: String): ChatResponse

    @GET("api/status/{id}/viewers")
    suspend fun statusViewers(@Path("id") statusId: String): StatusViewersResponse

    @POST("api/chats/{id}/invite")
    suspend fun createInvite(@Path("id") chatId: String, @Body body: InviteBody): InviteResponse

    @Multipart
    @POST("api/uploads")
    suspend fun upload(@Part file: MultipartBody.Part): UploadResponse
}
