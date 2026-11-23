package com.streamx.app.verification

import com.streamx.app.domain.model.*
import com.streamx.app.utils.ContentIdResolver
import com.streamx.app.utils.StreamMetadataParser
import org.junit.Assert.*
import org.junit.Test

class NuvioAddonStreamingSystemTest {

    @Test
    fun contentIdResolver_resolvesInternalMockIdsToCanonicalImdbIds() {
        assertEquals("tt9362930", ContentIdResolver.resolveCanonicalId("m1"))
        assertEquals("tt1757678", ContentIdResolver.resolveCanonicalId("m2"))
        assertEquals("tt11315808", ContentIdResolver.resolveCanonicalId("m3"))
        assertEquals("tt12343534", ContentIdResolver.resolveCanonicalId("m4"))
        assertEquals("tt31024314", ContentIdResolver.resolveCanonicalId("m5"))
        assertEquals("tt27961726", ContentIdResolver.resolveCanonicalId("m6"))
        assertEquals("tt8178634", ContentIdResolver.resolveCanonicalId("m7"))
        assertEquals("tt10919420", ContentIdResolver.resolveCanonicalId("m8"))
        assertEquals("tt9362722", ContentIdResolver.resolveCanonicalId("m9"))
        assertEquals("tt15239678", ContentIdResolver.resolveCanonicalId("m10"))
        assertEquals("tt15398776", ContentIdResolver.resolveCanonicalId("m11"))
    }

    @Test
    fun contentIdResolver_resolvesEpisodeIdsWithCanonicalMapping() {
        // Squid Game Season 1 Episode 1
        assertEquals("tt10919420:1:1", ContentIdResolver.resolveCanonicalId("m8:1:1"))
        // Squid Game Season 1 Episode 5
        assertEquals("tt10919420:1:5", ContentIdResolver.resolveCanonicalId("m8:1:5"))
        // Anime episode mapped from internal ID
        assertEquals("tt12343534:1:2", ContentIdResolver.resolveCanonicalId("m4:1:2"))
        // Kitsu native anime episode preserved
        assertEquals("kitsu:42765:1", ContentIdResolver.resolveCanonicalId("kitsu:42765:1"))
        // Namespaced episode
        assertEquals("tt0944947:1:1", ContentIdResolver.resolveCanonicalId("addon_cinemeta:tt0944947:1:1"))
    }

    @Test
    fun contentIdResolver_stripsAddonNamespacePrefixes() {
        assertEquals("tt15239678", ContentIdResolver.resolveCanonicalId("addon_cinemeta:tt15239678"))
        assertEquals("tt0111161", ContentIdResolver.resolveCanonicalId("org.stremio.pubdomainmovies:tt0111161"))
        assertEquals("tt1375666", ContentIdResolver.resolveCanonicalId("com.torrentio.addon:tt1375666"))
    }

    @Test
    fun contentIdResolver_infersContentTypeCorrectly() {
        assertEquals("series", ContentIdResolver.inferContentType(ContentFormat.SERIES, "tt10919420"))
        assertEquals("series", ContentIdResolver.inferContentType(ContentFormat.MOVIE, "tt10919420:1:1"))
        assertEquals("anime", ContentIdResolver.inferContentType(ContentFormat.ANIME, "kitsu:45678"))
        assertEquals("anime", ContentIdResolver.inferContentType(ContentFormat.ANIME, "kitsu:45678:1"))
        assertEquals("movie", ContentIdResolver.inferContentType(ContentFormat.MOVIE, "tt15239678"))
    }

    @Test
    fun streamMetadataParser_extractsQualityCodecSizeAndSeeders() {
        val sampleTitle = "Dune.Part.Two.2024.1080p.WEBRip.x264\n👤 450 💾 2.8 GB"
        val sampleName = "Torrentio\n1080p"
        val parsed = StreamMetadataParser.parse(sampleTitle, sampleName, null)

        assertEquals("1080P", parsed.resolution)
        assertEquals("X264", parsed.codec)
        assertEquals("2.8 GB", parsed.fileSize)
        assertEquals(450, parsed.seeders)
        assertEquals("1080p", parsed.qualityBadge)
        assertEquals("Dune.Part.Two.2024.1080p.WEBRip.x264", parsed.cleanTitle)
    }

    @Test
    fun streamMetadataParser_handles4KAndHevc() {
        val sampleTitle = "Avatar.Fire.And.Ash.2025.2160p.UHD.HEVC.Atmos\n💾 14.5 GB"
        val parsed = StreamMetadataParser.parse(sampleTitle, "Cyberflix", null)

        assertEquals("2160P", parsed.resolution)
        assertEquals("HEVC", parsed.codec)
        assertEquals("14.5 GB", parsed.fileSize)
        assertEquals("4K", parsed.qualityBadge)
    }

    @Test
    fun playbackSource_storesNuvioMetadataAttributes() {
        val source = PlaybackSource(
            mediaId = "tt15239678",
            title = "Torrentio: Dune Part Two (1080p)",
            streamUrl = "https://cdn.stream.com/dune2.mp4",
            providerName = "Torrentio",
            resolution = "1080p",
            codec = "HEVC",
            fileSize = "2.8 GB",
            seeders = 320,
            qualityBadge = "1080p"
        )

        assertEquals("Torrentio", source.providerName)
        assertEquals("1080p", source.resolution)
        assertEquals("HEVC", source.codec)
        assertEquals("2.8 GB", source.fileSize)
        assertEquals(320, source.seeders)
        assertEquals("1080p", source.qualityBadge)
    }

    @Test
    fun installedAddon_capabilityDistinction() {
        val streamAddon = InstalledAddon(
            id = "org.stremio.pubdomainmovies",
            name = "Public Domain Movies",
            manifestUrl = "https://example.com/manifest.json",
            baseUrl = "https://example.com",
            resources = listOf(StremioResourceDefinition("stream")),
            types = listOf("movie")
        )

        val metaAddon = InstalledAddon(
            id = "addon_cinemeta",
            name = "Cinemeta Official",
            manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
            baseUrl = "https://v3-cinemeta.strem.io",
            resources = listOf(StremioResourceDefinition("catalog"), StremioResourceDefinition("meta")),
            types = listOf("movie", "series")
        )

        val subAddon = InstalledAddon(
            id = "addon_open_subtitles",
            name = "OpenSubtitles v3",
            manifestUrl = "https://opensubtitles-v3.strem.io/manifest.json",
            baseUrl = "https://opensubtitles-v3.strem.io",
            resources = listOf(StremioResourceDefinition("subtitles")),
            types = listOf("movie", "series")
        )

        assertTrue(streamAddon.isStreamProvider)
        assertFalse(streamAddon.isMetadataOnly)
        assertFalse(streamAddon.isSubtitlesOnly)

        assertFalse(metaAddon.isStreamProvider)
        assertTrue(metaAddon.isMetadataOnly)
        assertFalse(metaAddon.isSubtitlesOnly)

        assertFalse(subAddon.isStreamProvider)
        assertFalse(subAddon.isMetadataOnly)
        assertTrue(subAddon.isSubtitlesOnly)
    }

    @Test
    fun multiSourceAggregation_preservesAttributionAndDeduplicates() {
        val sources = listOf(
            PlaybackSource(
                mediaId = "tt15239678",
                title = "Public Domain: Stream A",
                streamUrl = "https://server1.com/video.mp4",
                providerName = "Public Domain Movies"
            ),
            PlaybackSource(
                mediaId = "tt15239678",
                title = "Torrentio: Stream B",
                streamUrl = "https://server2.com/video.mp4",
                providerName = "Torrentio"
            ),
            PlaybackSource(
                mediaId = "tt15239678",
                title = "Duplicate: Stream A",
                streamUrl = "https://server1.com/video.mp4",
                providerName = "Mirror Provider"
            )
        )

        val deduplicated = sources.distinctBy { it.streamUrl }
        assertEquals(2, deduplicated.size)
        assertEquals("Public Domain Movies", deduplicated[0].providerName)
        assertEquals("Torrentio", deduplicated[1].providerName)
    }

    @Test
    fun streamProtocol_unsupportedStreamTypesAreNotTreatedAsPlayableHttp() {
        val ytStream = StremioStream(
            ytId = "dQw4w9WgXcQ",
            title = "YouTube Stream"
        )
        assertFalse(ytStream.isDirectHttp)
        assertFalse(ytStream.isTorrent)
        assertTrue(ytStream.isExternalOrYouTube)

        val externalStream = StremioStream(
            externalUrl = "https://netflix.com/watch/12345",
            title = "External Netflix Stream"
        )
        assertFalse(externalStream.isDirectHttp)
        assertFalse(externalStream.isTorrent)
        assertTrue(externalStream.isExternalOrYouTube)

        val directStream = StremioStream(
            url = "https://cdn.example.com/movie.mp4",
            title = "Direct MP4"
        )
        assertTrue(directStream.isDirectHttp)
        assertFalse(directStream.isExternalOrYouTube)
    }

    @Test
    fun streamProtocol_proxyHeadersParsing() {
        val json = com.google.gson.JsonParser.parseString(
            """
            {
              "request": {
                "User-Agent": "Mozilla/5.0 Custom",
                "Referer": "https://provider.stream"
              },
              "response": {
                "set-cookie": "token=abc123xyz"
              }
            }
            """.trimIndent()
        )

        val hints = StremioStreamBehaviorHints(
            notWebReady = false,
            proxyHeaders = json,
            headers = mapOf("Authorization" to "Bearer token123")
        )

        val reqHeaders = hints.getEffectiveHeaders()
        assertEquals("Mozilla/5.0 Custom", reqHeaders["User-Agent"])
        assertEquals("https://provider.stream", reqHeaders["Referer"])
        assertEquals("Bearer token123", reqHeaders["Authorization"])

        val respHeaders = hints.getResponseHeaders()
        assertEquals("token=abc123xyz", respHeaders["set-cookie"])
    }

    @Test
    fun streamProtocol_inStreamSubtitlesParsed() {
        val stream = StremioStream(
            url = "https://cdn.example.com/movie.m3u8",
            title = "Master Stream",
            subtitles = listOf(
                StremioSubtitle(id = "sub_en", url = "https://cdn.example.com/en.vtt", lang = "eng", label = "English"),
                StremioSubtitle(id = "sub_es", url = "https://cdn.example.com/es.srt", lang = "spa", label = "Spanish")
            )
        )

        assertTrue(stream.isDirectHttp)
        assertNotNull(stream.subtitles)
        assertEquals(2, stream.subtitles!!.size)
        assertEquals("eng", stream.subtitles!![0].lang)
        assertEquals("https://cdn.example.com/en.vtt", stream.subtitles!![0].url)
    }
}
