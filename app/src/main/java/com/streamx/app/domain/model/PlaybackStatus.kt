package com.streamx.app.domain.model

/**
 * Accurate playback status lifecycle states for STREAMX Media3 Player.
 * Replaces premature "Media3 Verified" notices with strict, verifiable state progression:
 * 1. SOURCE_DISCOVERED: Source identified from add-on/provider.
 * 2. URL_VALIDATED: HTTP syntax, protocol, and pre-flight probe succeeded.
 * 3. PLAYBACK_STARTED: ExoPlayer prepared and playback initiated.
 * 4. PLAYBACK_VERIFIED: ExoPlayer reached STATE_READY and is actively playing.
 */
enum class PlaybackStatus(val label: String) {
    SOURCE_DISCOVERED("Source discovered"),
    URL_VALIDATED("URL validated"),
    PLAYBACK_STARTED("Playback started"),
    PLAYBACK_VERIFIED("Playback verified")
}
