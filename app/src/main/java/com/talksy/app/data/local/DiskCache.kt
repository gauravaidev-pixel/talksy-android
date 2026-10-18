package com.talksy.app.data.local

import android.content.Context
import com.talksy.app.data.remote.dto.ChatDto
import com.talksy.app.data.remote.dto.MessageDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Tiny JSON-file cache (no Room/kapt needed). The chat list and the latest messages of each chat are
 * written here after every successful fetch, so the app can paint instantly on a cold start while the
 * network request refreshes the data in the background.
 */
class DiskCache(context: Context) {
    private val dir = File(context.filesDir, "cache").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private fun chatsFile() = File(dir, "chats.json")
    private fun messagesFile(chatId: String) = File(dir, "m_${chatId.filter { it.isLetterOrDigit() }}.json")

    fun saveChats(chats: List<ChatDto>) = runCatching {
        chatsFile().writeText(json.encodeToString(ListSerializer(ChatDto.serializer()), chats))
    }

    fun loadChats(): List<ChatDto> = runCatching {
        val f = chatsFile()
        if (f.exists()) json.decodeFromString(ListSerializer(ChatDto.serializer()), f.readText()) else emptyList()
    }.getOrDefault(emptyList())

    fun saveMessages(chatId: String, messages: List<MessageDto>) = runCatching {
        // keep the file small: only the newest page
        val tail = if (messages.size > 60) messages.takeLast(60) else messages
        messagesFile(chatId).writeText(json.encodeToString(ListSerializer(MessageDto.serializer()), tail))
    }

    fun loadMessages(chatId: String): List<MessageDto>? = runCatching {
        val f = messagesFile(chatId)
        if (f.exists()) json.decodeFromString(ListSerializer(MessageDto.serializer()), f.readText()) else null
    }.getOrNull()

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
