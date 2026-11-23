package com.streamx.app.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    companion object {
        fun safeEncode(value: String): String {
            return try {
                Uri.encode(value) ?: java.net.URLEncoder.encode(value, "UTF-8")
            } catch (_: Throwable) {
                value
            }
        }
    }

    object Welcome : Screen("welcome")
    object Main : Screen("main")
    object Details : Screen("details/{mediaId}") {
        fun createRoute(mediaId: String): String {
            val encoded = safeEncode(mediaId)
            return "details/$encoded"
        }
    }
    object SourceSelection : Screen("source_selection/{mediaId}?targetId={targetId}&episodeTitle={episodeTitle}") {
        fun createRoute(
            mediaId: String,
            targetId: String? = null,
            episodeTitle: String? = null
        ): String {
            val encodedMediaId = safeEncode(mediaId)
            val params = mutableListOf<String>()
            if (!targetId.isNullOrBlank()) {
                params.add("targetId=${safeEncode(targetId)}")
            }
            if (!episodeTitle.isNullOrBlank()) {
                params.add("episodeTitle=${safeEncode(episodeTitle)}")
            }
            return if (params.isNotEmpty()) {
                "source_selection/$encodedMediaId?${params.joinToString("&")}"
            } else {
                "source_selection/$encodedMediaId"
            }
        }
    }

    object Player : Screen("player/{mediaId}?streamUrl={streamUrl}&title={title}&provider={provider}") {
        fun createRoute(
            mediaId: String,
            streamUrl: String? = null,
            title: String? = null,
            provider: String? = null
        ): String {
            val encodedId = safeEncode(mediaId)
            val params = mutableListOf<String>()
            if (!streamUrl.isNullOrBlank()) {
                params.add("streamUrl=${safeEncode(streamUrl)}")
            }
            if (!title.isNullOrBlank()) {
                params.add("title=${safeEncode(title)}")
            }
            if (!provider.isNullOrBlank()) {
                params.add("provider=${safeEncode(provider)}")
            }
            return if (params.isNotEmpty()) {
                "player/$encodedId?${params.joinToString("&")}"
            } else {
                "player/$encodedId"
            }
        }
    }
    object Settings : Screen("settings")
    object About : Screen("about")
    object Addons : Screen("addons")
    object Discover : Screen("discover")
    object ContentDiscovery : Screen("content_discovery")
    object Integrations : Screen("integrations")

    object JellyfinConnect : Screen("jellyfin_connect")
    object JellyfinLogin : Screen("jellyfin_login?serverUrl={serverUrl}") {
        fun createRoute(serverUrl: String = ""): String {
            val encoded = Uri.encode(serverUrl)
            return "jellyfin_login?serverUrl=$encoded"
        }
    }
    object JellyfinLibrary : Screen("jellyfin_library")
    object JellyfinDetail : Screen("jellyfin_detail/{itemId}") {
        fun createRoute(itemId: String): String {
            val encoded = Uri.encode(itemId)
            return "jellyfin_detail/$encoded"
        }
    }

    object P2PDashboard : Screen("p2p_dashboard")
}
