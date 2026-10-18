package com.talksy.app.data.local

import android.content.Context

/** Small per-chat preferences that only live on this device. */
object ChatPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)

    /** "off" | "1day" | "1week" | "1month" - new messages in this chat auto-delete after that time. */
    fun disappear(context: Context, chatId: String): String = prefs(context).getString("dis_$chatId", "off") ?: "off"

    fun setDisappear(context: Context, chatId: String, value: String) {
        prefs(context).edit().putString("dis_$chatId", value).apply()
    }
}
