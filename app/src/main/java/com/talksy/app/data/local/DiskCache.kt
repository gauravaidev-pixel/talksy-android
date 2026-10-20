package com.talksy.app.data.local

import android.content.Context
import com.talksy.app.data.remote.dto.CallDto
import com.talksy.app.data.remote.dto.ChatDto
import com.talksy.app.data.remote.dto.MessageDto
import com.talksy.app.data.remote.dto.StatusDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * JSON-file cache in app storage (no Room/kapt needed).
 *
 * Writes happen on a background thread (atomic: temp file + rename) and reads are tiny, so the UI can paint
 * the last known chats / messages / statuses / calls instantly on a cold start while the network refreshes them.
 */
class DiskCache(context: Context) {
    private val dir = File(context.filesDir, "cache").apply { mkdirs() }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private fun messagesFile(chatId: String) = "m_${chatId.filter { it.isLetterOrDigit() }}.json"

    private fun <T> write(name: String, serializer: KSerializer<List<T>>, value: List<T>) {
        io.launch {
            runCatching {
                val tmp = File(dir, "$name.tmp")
                tmp.writeText(json.encodeToString(serializer, value))
                tmp.renameTo(File(dir, name))
            }
        }
    }

    private fun <T> read(name: String, serializer: KSerializer<List<T>>): List<T>? = runCatching {
        val f = File(dir, name)
        if (f.exists()) json.decodeFromString(serializer, f.readText()) else null
    }.getOrNull()

    // ---- chats
    fun saveChats(chats: List<ChatDto>) = write("chats.json", ListSerializer(ChatDto.serializer()), chats)
    fun loadChats(): List<ChatDto> = read("chats.json", ListSerializer(ChatDto.serializer())) ?: emptyList()

    // ---- messages (newest page only)
    fun saveMessages(chatId: String, messages: List<MessageDto>) {
        val tail = if (messages.size > 60) messages.takeLast(60) else messages
        write(messagesFile(chatId), ListSerializer(MessageDto.serializer()), tail)
    }
    fun loadMessages(chatId: String): List<MessageDto>? = read(messagesFile(chatId), ListSerializer(MessageDto.serializer()))

    // ---- statuses
    fun saveStatuses(list: List<StatusDto>) = write("statuses.json", ListSerializer(StatusDto.serializer()), list)
    fun loadStatuses(): List<StatusDto> = read("statuses.json", ListSerializer(StatusDto.serializer())) ?: emptyList()

    // ---- call history
    fun saveCalls(list: List<CallDto>) = write("calls.json", ListSerializer(CallDto.serializer()), list)
    fun loadCalls(): List<CallDto> = read("calls.json", ListSerializer(CallDto.serializer())) ?: emptyList()

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        io.launch { dir.listFiles()?.forEach { it.delete() } }
    }
}
