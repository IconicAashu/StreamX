package com.streamx.app.di

import android.content.Context
import com.streamx.app.data.local.UserPreferencesDataStore
import com.streamx.app.data.jellyfin.JellyfinRepositoryImpl
import com.streamx.app.data.playback.jellyfin.JellyfinPlaybackProvider
import com.streamx.app.data.playback.p2p.P2PPlaybackProvider
import com.streamx.app.data.playback.stremio.StremioPlaybackProvider
import com.streamx.app.p2p.engine.P2PSessionManager
import com.streamx.app.data.repository.AppUpdateRepositoryImpl
import com.streamx.app.data.repository.CatalogRepositoryImpl
import com.streamx.app.data.repository.ChannelFilterRepositoryImpl
import com.streamx.app.data.repository.ManifestAddonRepositoryImpl
import com.streamx.app.data.repository.PlaybackRepositoryImpl
import com.streamx.app.data.repository.TmdbRepositoryImpl
import com.streamx.app.data.repository.UserPreferencesRepositoryImpl
import com.streamx.app.domain.jellyfin.JellyfinRepository
import com.streamx.app.domain.repository.AppUpdateRepository
import com.streamx.app.domain.repository.CatalogRepository
import com.streamx.app.domain.repository.ChannelFilterRepository
import com.streamx.app.domain.repository.ManifestAddonRepository
import com.streamx.app.domain.repository.PlaybackRepository
import com.streamx.app.domain.repository.TmdbRepository
import com.streamx.app.domain.repository.UserPreferencesRepository
import com.streamx.app.network.api.TmdbApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

interface AppContainer {
    val userPreferencesDataStore: UserPreferencesDataStore
    val userPreferencesRepository: UserPreferencesRepository
    val tmdbRepository: TmdbRepository
    val catalogRepository: CatalogRepository
    val playbackRepository: PlaybackRepository
    val manifestAddonRepository: ManifestAddonRepository
    val channelFilterRepository: ChannelFilterRepository
    val jellyfinRepository: JellyfinRepository
    val p2pSessionManager: P2PSessionManager
    val appUpdateRepository: AppUpdateRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val userPreferencesDataStore: UserPreferencesDataStore by lazy {
        UserPreferencesDataStore(context)
    }

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepositoryImpl(userPreferencesDataStore)
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val tmdbRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val tmdbApiService: TmdbApiService by lazy {
        tmdbRetrofit.create(TmdbApiService::class.java)
    }

    override val tmdbRepository: TmdbRepository by lazy {
        TmdbRepositoryImpl(tmdbApiService)
    }

    override val manifestAddonRepository: ManifestAddonRepository by lazy {
        ManifestAddonRepositoryImpl(userPreferencesDataStore)
    }

    override val catalogRepository: CatalogRepository by lazy {
        CatalogRepositoryImpl(
            tmdbRepository = tmdbRepository,
            userPreferencesRepository = userPreferencesRepository,
            manifestAddonRepository = manifestAddonRepository
        )
    }

    override val jellyfinRepository: JellyfinRepository by lazy {
        JellyfinRepositoryImpl(userPreferencesDataStore)
    }

    override val p2pSessionManager: P2PSessionManager by lazy {
        P2PSessionManager.getInstance(context)
    }

    override val playbackRepository: PlaybackRepository by lazy {
        val stremioProvider = StremioPlaybackProvider(manifestAddonRepository)
        val jellyfinProvider = JellyfinPlaybackProvider(jellyfinRepository)
        val p2pProvider = P2PPlaybackProvider(p2pSessionManager)
        PlaybackRepositoryImpl(
            dataStore = userPreferencesDataStore,
            manifestAddonRepository = manifestAddonRepository,
            providers = listOf(p2pProvider, jellyfinProvider, stremioProvider)
        )
    }

    override val channelFilterRepository: ChannelFilterRepository by lazy {
        ChannelFilterRepositoryImpl(tmdbRepository = tmdbRepository)
    }

    override val appUpdateRepository: AppUpdateRepository by lazy {
        AppUpdateRepositoryImpl(
            context = context,
            userPreferencesRepository = userPreferencesRepository,
            httpClient = okHttpClient
        )
    }
}
