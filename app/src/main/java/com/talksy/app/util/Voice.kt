package com.talksy.app.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** Records a voice message to an AAC/M4A file. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    fun start(): Boolean {
        return try {
            val out = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = out
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            release()
            false
        }
    }

    /** Stops recording. Returns (file, seconds) or null if it was too short / failed. */
    fun stop(): Pair<File, Int>? {
        val r = recorder ?: return null
        val out = file
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000L).toInt().coerceAtLeast(1)
        val ok = runCatching { r.stop() }.isSuccess
        release()
        return if (ok && out != null && out.exists() && seconds >= 1) out to seconds else null
    }

    fun cancel() {
        val out = file
        runCatching { recorder?.stop() }
        release()
        out?.delete()
    }

    private fun release() {
        runCatching { recorder?.release() }
        recorder = null
    }
}

/** One global voice player: only one voice message plays at a time. UI observes [currentUrl] / [progress]. */
object VoicePlayer {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    var currentUrl by mutableStateOf<String?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    private val ticker = object : Runnable {
        override fun run() {
            val p = player ?: return
            val total = runCatching { p.duration }.getOrDefault(0)
            if (total > 0) progress = (p.currentPosition.toFloat() / total).coerceIn(0f, 1f)
            handler.postDelayed(this, 100)
        }
    }

    fun toggle(url: String) {
        if (currentUrl == url) stop() else start(url)
    }

    private fun start(url: String) {
        stop()
        val mp = MediaPlayer()
        try {
            mp.setDataSource(url)
            mp.setOnPreparedListener {
                it.start()
                currentUrl = url
                progress = 0f
                handler.post(ticker)
            }
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ -> stop(); true }
            mp.prepareAsync()
            player = mp
        } catch (e: Exception) {
            mp.release()
            stop()
        }
    }

    fun stop() {
        handler.removeCallbacks(ticker)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        currentUrl = null
        progress = 0f
    }
}
