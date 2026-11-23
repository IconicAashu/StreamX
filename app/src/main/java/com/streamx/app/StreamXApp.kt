package com.streamx.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.streamx.app.di.AppContainer
import com.streamx.app.di.DefaultAppContainer

class StreamXApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    val appContainer: AppContainer
        get() = container

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        com.streamx.app.notification.NotificationHelper.createNotificationChannels(this)
        com.streamx.app.work.UpdateCheckWorker.schedule(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("streamx_image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024) // 128 MB disk cache
                    .build()
            }
            .allowHardware(true)
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}
