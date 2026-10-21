package com.talksy.app.data.remote.dto

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class UserBrief(
    val id: String,
    val name: String = "",
    val username: String = "",
    val picture: String = "",
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("last_seen") val lastSeen: String? = null,
    val role: String = "",
)

@Serializable
@Immutable
data class UserDto(
    val id: String,
    val name: String = "",
    val username: String = "",
    val picture: String = "",
    val bio: String = "",
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("last_seen") val lastSeen: String? = null,
)

@Serializable
@Immutable
data class ReplyDto(
    val id: String,
    val text: String = "",
    val sender: UserBrief? = null,
)

@Serializable
@Immutable
data class MessageDto(
    val id: String,
    @SerialName("chat_id") val chatId: String,
    val sender: UserBrief,
    val text: String = "",
    @SerialName("image_url") val imageUrl: String = "",
    val kind: String = "text",
    @SerialName("poll_id") val pollId: String? = null,
    val reactions: Map<String, List<String>> = emptyMap(),
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("view_once") val viewOnce: Boolean = false,
    @SerialName("is_edited") val isEdited: Boolean = false,
    @SerialName("is_seen") val isSeen: Boolean = false,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("reply_to") val replyTo: ReplyDto? = null,
)

@Serializable
@Immutable
data class ChatDto(
    val id: String,
    val type: String = "direct",
    val name: String = "",
    val title: String = "",
    val description: String = "",
    val avatar: String = "",
    val members: List<UserBrief> = emptyList(),
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("last_message") val lastMessage: MessageDto? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("my_role") val myRole: String? = null,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("is_muted") val isMuted: Boolean = false,
    val peer: UserBrief? = null,
)

@Serializable
@Immutable
data class PollDto(
    val id: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("creator_id") val creatorId: String = "",
    val question: String,
    val options: List<String> = emptyList(),
    val counts: List<Int> = emptyList(),
    @SerialName("total_votes") val totalVotes: Int = 0,
    @SerialName("is_quiz") val isQuiz: Boolean = false,
    @SerialName("correct_option") val correctOption: Int? = null,
    @SerialName("allow_multiple") val allowMultiple: Boolean = false,
    @SerialName("is_closed") val isClosed: Boolean = false,
    @SerialName("my_votes") val myVotes: List<Int> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
@Immutable
data class StatusDto(
    val id: String,
    val user: UserBrief,
    val text: String = "",
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val viewed: Boolean = false,
    @SerialName("is_mine") val isMine: Boolean = false,
    @SerialName("view_count") val viewCount: Int = 0,
)

// ---- Response envelopes ----
@Serializable data class AuthResponse(
    val success: Boolean = false,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    val user: UserDto,
)
@Serializable data class MeResponse(val success: Boolean = false, val user: UserDto)
@Serializable data class ChatListResponse(val success: Boolean = false, val chats: List<ChatDto> = emptyList())
@Serializable data class ChatResponse(val success: Boolean = false, val chat: ChatDto)
@Serializable data class MessageResponse(val success: Boolean = false, val message: MessageDto)
@Serializable data class MessagesResponse(val success: Boolean = false, val messages: List<MessageDto> = emptyList())
@Serializable data class PollResponse(val success: Boolean = false, val poll: PollDto)
@Serializable data class PollListResponse(val success: Boolean = false, val polls: List<PollDto> = emptyList())
@Serializable data class StatusListResponse(val success: Boolean = false, val statuses: List<StatusDto> = emptyList())
@Serializable data class UserListResponse(val success: Boolean = false, val users: List<UserDto> = emptyList())
@Serializable data class SimpleResponse(val success: Boolean = false, val error: String? = null, val code: String? = null)
@Serializable data class UploadResponse(val success: Boolean = false, val url: String = "", val kind: String = "", val error: String? = null)

// ---- Request bodies ----
@Serializable data class GoogleLoginBody(@SerialName("id_token") val idToken: String)
@Serializable data class UpdateMeBody(
    val name: String? = null, val bio: String? = null,
    val username: String? = null, val picture: String? = null,
    @SerialName("fcm_token") val fcmToken: String? = null,
)
@Serializable data class DirectChatBody(@SerialName("user_id") val userId: String)
@Serializable data class CreateGroupBody(
    val name: String, val description: String? = null,
    @SerialName("member_ids") val memberIds: List<String> = emptyList(),
)
@Serializable data class SendMessageBody(
    val text: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("view_once") val viewOnce: Boolean = false,
    @SerialName("auto_delete") val autoDelete: String = "never",
)
@Serializable data class CreatePollBody(
    val question: String, val options: List<String>,
    @SerialName("is_quiz") val isQuiz: Boolean = false,
    @SerialName("correct_option") val correctOption: Int? = null,
    @SerialName("allow_multiple") val allowMultiple: Boolean = false,
)
@Serializable data class VoteBody(@SerialName("option_index") val optionIndex: Int)
@Serializable data class StatusBody(val text: String? = null, @SerialName("image_url") val imageUrl: String? = null)
@Serializable data class ReactionBody(val emoji: String)
@Serializable data class EditBody(val text: String)
@Serializable data class InviteBody(
    @SerialName("expire_hours") val expireHours: Int = 0,
    @SerialName("max_uses") val maxUses: Int = 0,
)
@Serializable data class InviteResponse(val success: Boolean = false, val code: String = "")

@Serializable data class ChatSettingsBody(
    val pinned: Boolean? = null,
    @SerialName("mute_hours") val muteHours: Int? = null,
)
@Serializable data class ReportBody(val reason: String)
@Serializable data class EditChatBody(val name: String? = null, val description: String? = null, val avatar: String? = null)
@Serializable data class AddMembersBody(@SerialName("member_ids") val memberIds: List<String>)
@Serializable data class RoleBody(val role: String)
@Serializable data class StatusViewerDto(
    val user: UserBrief,
    @SerialName("viewed_at") val viewedAt: String? = null,
)
@Serializable data class StatusViewersResponse(val success: Boolean = false, val viewers: List<StatusViewerDto> = emptyList())

@Serializable @Immutable data class CallDto(
    val id: String,
    val video: Boolean = false,
    val status: String = "",
    val direction: String = "",
    val peer: UserBrief,
    @SerialName("created_at") val createdAt: String? = null,
    val duration: Int = 0,
)
@Serializable data class CallListResponse(val success: Boolean = false, val calls: List<CallDto> = emptyList())
@Serializable data class IceServerDto(val urls: List<String> = emptyList(), val username: String? = null, val credential: String? = null)
@Serializable data class IceResponse(
    val success: Boolean = false,
    @SerialName("ice_servers") val iceServers: List<IceServerDto> = emptyList(),
)
