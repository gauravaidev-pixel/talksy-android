package com.talksy.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.talksy.app.call.CallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TalksyApp : Application(), ImageLoaderFactory {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        CallManager.init(this)
        // Warm the in-memory token cache before any request goes out.
        scope.launch { AppGraph.session.load() }
    }

    /**
     * One shared image loader: 25% RAM memory cache + a 300 MB disk cache stored in app storage (filesDir, so the
     * system does not purge it like cacheDir). Uploaded images never change, so server cache headers are ignored.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(filesDir.resolve("image_cache"))
                .maxSizeBytes(300L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()
}
