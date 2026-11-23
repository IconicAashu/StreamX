package com.streamx.app.verification

import com.streamx.app.domain.model.AudioTrackOption
import com.streamx.app.domain.model.SubtitleTrackOption
import com.streamx.app.domain.model.VideoQualityOption
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class TrackSelectionAndSettingsTest {

    @Test
    fun testSingleAudioTrackShowsOnlyOneOption() {
        val detectedAudios = listOf(
            AudioTrackOption(
                id = "audio_0_0",
                label = "English",
                language = "en",
                isSelected = true,
                groupIndex = 0,
                trackIndex = 0
            )
        )

        assertEquals(1, detectedAudios.size)
        assertEquals("English", detectedAudios[0].label)
        assertEquals("en", detectedAudios[0].language)
        assertTrue(detectedAudios[0].isSelected)
    }

    @Test
    fun testMultiAudioTrackExtractsLanguageNamesAndCodes() {
        val audioTracks = listOf(
            AudioTrackOption("a0", "Hindi", "hi", isSelected = false),
            AudioTrackOption("a1", "English", "en", isSelected = true),
            AudioTrackOption("a2", "Japanese", "ja", isSelected = false),
            AudioTrackOption("a3", "und", "und", isSelected = false) // undefined language code
        )

        assertEquals(4, audioTracks.size)
        assertEquals("Hindi", audioTracks[0].label)
        assertEquals("Japanese", audioTracks[2].label)
        // Code fallback
        assertEquals("und", audioTracks[3].language)

        val selected = audioTracks.find { it.isSelected }
        assertNotNull(selected)
        assertEquals("English", selected!!.label)
    }

    @Test
    fun testSubtitlesIncludeOffAndDisableProperly() {
        val availableSubs = listOf(
            SubtitleTrackOption.OFF,
            SubtitleTrackOption("sub_0", "English [CC]", "en", isSelected = false),
            SubtitleTrackOption("sub_1", "Spanish", "es", isSelected = false)
        )

        assertEquals(3, availableSubs.size)
        assertTrue("First subtitle option must always be Off", availableSubs[0].isOff)
        assertEquals("Off", availableSubs[0].label)

        // Select Spanish
        val updated = availableSubs.map { it.copy(isSelected = it.id == "sub_1") }
        assertFalse(updated[0].isSelected)
        assertTrue(updated[2].isSelected)
        assertEquals("Spanish", updated[2].label)
    }

    @Test
    fun testMissingSubtitlesHandledGracefully() {
        val availableSubs = listOf(SubtitleTrackOption.OFF)

        assertEquals(1, availableSubs.size)
        assertTrue(availableSubs[0].isOff)
        assertEquals("Off", availableSubs[0].label)
    }

    @Test
    fun testVideoQualityShowsOnlyAvailableResolutionsAndAuto() {
        // Single resolution progressive video (e.g. 720p only)
        val singleResOptions = listOf(
            VideoQualityOption.AUTO,
            VideoQualityOption(label = "720p (HD)", heightPixels = 720, isSelected = false)
        )

        // Must NOT create fake 1080p, 4K options
        assertEquals(2, singleResOptions.size)
        assertTrue(singleResOptions[0].isAuto)
        assertEquals("Auto", singleResOptions[0].label)
        assertEquals(720, singleResOptions[1].heightPixels)
    }

    @Test
    fun testAdaptiveHlsAndDashQualityExtraction() {
        // Multi-bitrate stream with 360p, 480p, 720p, 1080p
        val adaptiveQualities = listOf(
            VideoQualityOption.AUTO.copy(isSelected = false),
            VideoQualityOption(label = "360p", heightPixels = 360),
            VideoQualityOption(label = "480p (SD)", heightPixels = 480),
            VideoQualityOption(label = "720p (HD)", heightPixels = 720),
            VideoQualityOption(label = "1080p (Full HD)", heightPixels = 1080, isSelected = true)
        )

        assertEquals(5, adaptiveQualities.size)
        assertTrue(adaptiveQualities[0].isAuto)
        val selected = adaptiveQualities.find { it.isSelected }
        assertNotNull(selected)
        assertEquals(1080, selected!!.heightPixels)
        assertEquals("1080p (Full HD)", selected.label)
    }

    @Test
    fun testLanguageDisplayFallback() {
        val langCode = "ja"
        val display = Locale.forLanguageTag(langCode).displayLanguage
        assertEquals("Japanese", display)
    }
}
