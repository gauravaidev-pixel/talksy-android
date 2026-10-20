package com.talksy.app

import android.content.Context
import com.talksy.app.data.local.SessionStore
import com.talksy.app.data.remote.ApiClient
import com.talksy.app.data.remote.ApiService
import com.talksy.app.data.repo.*
import com.talksy.app.data.socket.SocketManager

/** Tiny hand-rolled service locator (no DI framework needed). */
object AppGraph {
    lateinit var session: SessionStore
        private set
    lateinit var api: ApiService
        private set
    lateinit var socket: SocketManager
        private set
    lateinit var appContext: Context
        private set
    lateinit var disk: com.talksy.app.data.local.DiskCache
        private set

    /** In-memory caches so a chat can paint instantly while fresh data loads (stale-while-revalidate). */
    val chatCache = java.util.concurrent.ConcurrentHashMap<String, com.talksy.app.data.remote.dto.ChatDto>()
    val messageCache = java.util.concurrent.ConcurrentHashMap<String, List<com.talksy.app.data.remote.dto.MessageDto>>()

    fun clearCaches() {
        chatCache.clear()
        messageCache.clear()
        disk.clear()
    }

    val auth by lazy { AuthRepository(api) }
    val users by lazy { UserRepository(api) }
    val chats by lazy { ChatRepository(api) }
    val messages by lazy { MessageRepository(api) }
    val polls by lazy { PollRepository(api) }
    val statuses by lazy { StatusRepository(api) }
    val uploads by lazy { UploadRepository(api, appContext) }
    val calls by lazy { CallRepository(api) }

    fun init(context: Context) {
        if (::session.isInitialized) return
        appContext = context.applicationContext
        disk = com.talksy.app.data.local.DiskCache(context.applicationContext)
        session = SessionStore(context.applicationContext)
        api = ApiClient.create(session)
        socket = SocketManager()
    }
}
