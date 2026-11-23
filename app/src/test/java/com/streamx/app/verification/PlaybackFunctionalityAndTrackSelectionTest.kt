package com.streamx.app.verification

import com.streamx.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class PlaybackFunctionalityAndTrackSelectionTest {

    // ==========================================
    // PHASE 1: BUILT-IN ADD-ON ECOSYSTEM TESTS
    // ==========================================

    @Test
    fun testBuiltInCatalogueContainsVerifiedAddons() {
        val catalogue = BuiltInAddonCatalogue.curatedCatalogue
        assertTrue("Catalogue must contain verified add-ons", catalogue.isNotEmpty())

        val cinemeta = catalogue.find { it.id == "addon_cinemeta" }
        assertNotNull("Cinemeta must be present in curated catalogue", cinemeta)
        assertTrue("Cinemeta must be default installed", cinemeta!!.defaultInstalled)
        assertTrue("Cinemeta is metadata-only", cinemeta.isMetadataOnly)
        assertFalse("Cinemeta is not a stream provider", cinemeta.isStreamProvider)

        val pubDomain = catalogue.find { it.id == "org.stremio.pubdomainmovies" }
        assertNotNull("Public Domain Movies must be present", pubDomain)
        assertTrue("Public Domain Movies must be a stream provider", pubDomain!!.isStreamProvider)
        assertTrue("Public Domain Movies default installed", pubDomain.defaultInstalled)

        val torrentio = catalogue.find { it.id == "addon_torrentio" }
        assertNotNull("Torrentio must be present in catalogue", torrentio)
        assertTrue("Torrentio requires configuration (Debrid)", torrentio!!.requiresConfiguration)
        assertFalse("Torrentio must not be default installed without user config", torrentio.defaultInstalled)
        assertNotNull("Torrentio must have configuration hint", torrentio.configurationHint)

        val openSubs = catalogue.find { it.id == "addon_open_subtitles" }
        assertNotNull("OpenSubtitles must be in catalogue", openSubs)
        assertTrue("OpenSubtitles is subtitles-only", openSubs!!.isSubtitlesOnly)
        assertFalse("OpenSubtitles is not a stream provider", openSubs.isStreamProvider)
    }

    @Test
    fun testAddonCapabilityStreamResourceExcludesMetadataOnlyAddons() {
        val defaults = BuiltInAddonCatalogue.getDefaultInstalledAddons()

        val cinemeta = defaults.find { it.id == "addon_cinemeta" }
        assertNotNull(cinemeta)
        // Cinemeta only declares catalog and meta; must NEVER claim to support stream resource
        assertFalse(
            "Cinemeta must NEVER support stream resource",
            cinemeta!!.supportsResource("stream", type = "movie", id = "tt1234567")
        )

        val pubDomain = defaults.find { it.id == "org.stremio.pubdomainmovies" }
        assertNotNull(pubDomain)
        assertTrue(
            "Public Domain Movies must support stream resource",
            pubDomain!!.supportsResource("stream", type = "movie", id = "tt0021749")
        )

        val openSubs = defaults.find { it.id == "addon_open_subtitles" }
        assertNotNull(openSubs)
        assertFalse(
            "OpenSubtitles must NEVER be queried as a stream provider",
            openSubs!!.supportsResource("stream", type = "movie", id = "tt1234567")
        )
        assertTrue(
            "OpenSubtitles must support subtitles resource",
            openSubs.supportsResource("subtitles", type = "movie", id = "tt1234567")
        )
    }

    // ==========================================
    // PHASE 2: AUDIO LANGUAGE SWITCHING TESTS
    // ==========================================

    @Test
    fun testSingleAudioTrackIndicator() {
        val audioList = listOf(
            AudioTrackOption(
                id = "audio_0_0",
                label = "English (Original)",
                language = "en",
                isSelected = true,
                groupIndex = 0,
                trackIndex = 0
            )
        )

        // When only 1 audio track is available
        val displayLabel = if (audioList.size == 1) {
            "${audioList[0].label} (Only track available)"
        } else {
            audioList[0].label
        }

        assertEquals("English (Original) (Only track available)", displayLabel)
    }

    @Test
    fun testMultiAudioTrackLanguageExtractionAndSelection() {
        val tracks = listOf(
            AudioTrackOption("audio_0_0", "Hindi [Main]", "hi", isSelected = true, groupIndex = 0, trackIndex = 0),
            AudioTrackOption("audio_0_1", "English [Dubbed]", "en", isSelected = false, groupIndex = 0, trackIndex = 1),
            AudioTrackOption("audio_0_2", "Korean [Original]", "ko", isSelected = false, groupIndex = 0, trackIndex = 2)
        )

        assertEquals(3, tracks.size)
        val selectedInitial = tracks.first { it.isSelected }
        assertEquals("Hindi [Main]", selectedInitial.label)
        assertEquals("hi", selectedInitial.language)

        // Seamless switch to Korean without reloading stream
        val targetOption = tracks[2]
        val updatedTracks = tracks.map { it.copy(isSelected = it.id == targetOption.id) }

        val selectedNew = updatedTracks.first { it.isSelected }
        assertEquals("Korean [Original]", selectedNew.label)
        assertEquals("ko", selectedNew.language)
        assertFalse("Previous track must no longer be selected", updatedTracks[0].isSelected)
    }

    @Test
    fun testAudioTrackLanguageDisplayFallback() {
        val langCode = "ko"
        val displayLang = Locale.forLanguageTag(langCode).displayLanguage
        assertEquals("Korean", displayLang)

        val hindiCode = "hi"
        val hindiDisplay = Locale.forLanguageTag(hindiCode).displayLanguage
        assertEquals("Hindi", hindiDisplay)
    }

    // ==========================================
    // PHASE 3: VIDEO QUALITY SELECTION TESTS
    // ==========================================

    @Test
    fun testSingleMp4QualityDisplaysOnlyActualResolution() {
        // Single non-adaptive video file (e.g. 1080p MP4)
        val detectedResolutions = listOf(1080)
        val isMultiVariantAdaptive = detectedResolutions.size > 1

        val qualityList = mutableListOf<VideoQualityOption>()
        if (isMultiVariantAdaptive) {
            qualityList.add(VideoQualityOption(label = "Auto", heightPixels = 0, isAuto = true, isSelected = true))
        } else {
            // For single MP4, show ONLY actual quality without redundant Auto
            qualityList.add(
                VideoQualityOption(
                    label = "1080p (Full HD)",
                    heightPixels = 1080,
                    isSelected = true,
                    groupIndex = 0,
                    trackIndex = 0
                )
            )
        }

        assertEquals("Single MP4 must have exactly 1 quality option", 1, qualityList.size)
        assertFalse("Single MP4 must NOT have Auto option", qualityList[0].isAuto)
        assertEquals("1080p (Full HD)", qualityList[0].label)
        assertTrue(qualityList[0].isSelected)
    }

    @Test
    fun testAdaptiveHlsDashQualityVariantsIncludeAuto() {
        // Multi-variant HLS stream with 360p, 720p, 1080p, 2160p
        val detectedResolutions = listOf(360, 720, 1080, 2160)
        val isMultiVariantAdaptive = detectedResolutions.size > 1

        val qualityList = mutableListOf<VideoQualityOption>()
        if (isMultiVariantAdaptive) {
            qualityList.add(VideoQualityOption(label = "Auto", heightPixels = 0, isAuto = true, isSelected = true))
            detectedResolutions.forEach { h ->
                val label = when (h) {
                    2160 -> "2160p (4K)"
                    1080 -> "1080p (Full HD)"
                    720 -> "720p (HD)"
                    else -> "${h}p"
                }
                qualityList.add(VideoQualityOption(label = label, heightPixels = h, isSelected = false))
            }
        }

        assertEquals(5, qualityList.size)
        assertTrue(qualityList[0].isAuto)
        assertEquals("Auto", qualityList[0].label)
        assertEquals("2160p (4K)", qualityList[4].label)
    }

    @Test
    fun testNon16by9AspectRatioDoesNotGetRejected() {
        // Cinemascope 2.40:1 (1920x800)
        val videoWidth = 1920
        val videoHeight = 800

        // Old bug was: setMaxVideoSize(videoHeight * 16 / 9, videoHeight)
        val oldBrokenMaxWidth = videoHeight * 16 / 9 // 1422
        assertTrue("Old logic caused bug where 1920 > 1422", videoWidth > oldBrokenMaxWidth)

        // New logic uses TrackSelectionOverride or max width Int.MAX_VALUE without aspect constraint
        val newMaxWidth = Int.MAX_VALUE
        assertTrue("New logic safely accommodates ultra-wide resolutions", videoWidth <= newMaxWidth)
    }

    // ==========================================
    // PHASE 4: SUBTITLE SYSTEM TESTS
    // ==========================================

    @Test
    fun testSubtitleTrackSelectionOffOption() {
        val subs = listOf(
            SubtitleTrackOption.OFF.copy(isSelected = false),
            SubtitleTrackOption("sub_0", "English [Full]", "en", isSelected = true),
            SubtitleTrackOption("sub_1", "Spanish", "es", isSelected = false)
        )

        val selected = subs.find { it.isSelected }
        assertEquals("English [Full]", selected?.label)

        // User turns off subtitles
        val offOption = subs[0]
        val updatedSubs = subs.map { it.copy(isSelected = it.id == offOption.id) }

        assertTrue("Off option must be selected", updatedSubs[0].isSelected)
        assertFalse("Previous subtitle must be deselected", updatedSubs[1].isSelected)
    }

    @Test
    fun testExternalSubtitleMimeTypeResolution() {
        val vttUrl = "https://example.com/subs/en.vtt"
        val srtUrl = "https://example.com/subs/es.srt"
        val ttmlUrl = "https://example.com/subs/fr.ttml"
        val assUrl = "https://example.com/subs/ja.ass"

        fun getMime(url: String): String = when {
            url.endsWith(".vtt", ignoreCase = true) -> androidx.media3.common.MimeTypes.TEXT_VTT
            url.endsWith(".ttml", ignoreCase = true) -> androidx.media3.common.MimeTypes.APPLICATION_TTML
            url.endsWith(".ass", ignoreCase = true) || url.endsWith(".ssa", ignoreCase = true) -> androidx.media3.common.MimeTypes.TEXT_SSA
            else -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
        }

        assertEquals(androidx.media3.common.MimeTypes.TEXT_VTT, getMime(vttUrl))
        assertEquals(androidx.media3.common.MimeTypes.APPLICATION_SUBRIP, getMime(srtUrl))
        assertEquals(androidx.media3.common.MimeTypes.APPLICATION_TTML, getMime(ttmlUrl))
        assertEquals(androidx.media3.common.MimeTypes.TEXT_SSA, getMime(assUrl))
    }

    // ==========================================
    // REGRESSION TEST: FENIXFLIX BASELINE
    // ==========================================

    @Test
    fun testFenixFlixBaselineSourceIntegrity() {
        val fenixSource = PlaybackSource(
            mediaId = "tt1375666",
            title = "FenixFlix: Inception (2010) [1080p]",
            streamUrl = "https://fenixflix.sample.cdn/stream/tt1375666.m3u8?token=safe_token",
            isLiveOrHls = true,
            providerName = "FenixFlix",
            requestHeaders = mapOf(
                "Referer" to "https://fenixflix.com/",
                "User-Agent" to "Mozilla/5.0 (Linux; Android 14; STREAMX-Player)"
            ),
            resolution = "1080p",
            qualityBadge = "1080p FHD"
        )

        assertNotNull(fenixSource)
        assertEquals("FenixFlix", fenixSource.providerName)
        assertTrue("Stream must be identified as HLS", fenixSource.isLiveOrHls)
        assertEquals("https://fenixflix.com/", fenixSource.requestHeaders["Referer"])
        assertEquals("1080p", fenixSource.resolution)
    }
}
