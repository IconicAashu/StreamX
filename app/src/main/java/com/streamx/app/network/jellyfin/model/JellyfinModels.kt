package com.streamx.app.network.jellyfin.model

import com.google.gson.annotations.SerializedName

/**
 * Jellyfin API Data Transfer Objects (DTOs)
 * Compliant with Jellyfin REST API v10.8+
 */

data class JellyfinPublicSystemInfo(
    @SerializedName("ServerName") val serverName: String = "",
    @SerializedName("Version") val version: String = "",
    @SerializedName("Id") val id: String = "",
    @SerializedName("OperatingSystem") val operatingSystem: String? = null,
    @SerializedName("StartupWizardCompleted") val startupWizardCompleted: Boolean = true
)

data class JellyfinAuthenticateUserByName(
    @SerializedName("Username") val username: String,
    @SerializedName("Pw") val password: String
)

data class JellyfinUserDto(
    @SerializedName("Id") val id: String = "",
    @SerializedName("Name") val name: String = "",
    @SerializedName("ServerId") val serverId: String? = null,
    @SerializedName("HasPassword") val hasPassword: Boolean = true
)

data class JellyfinAuthenticationResult(
    @SerializedName("AccessToken") val accessToken: String = "",
    @SerializedName("ServerId") val serverId: String = "",
    @SerializedName("User") val user: JellyfinUserDto? = null
)

data class JellyfinQuickConnectInitiate(
    @SerializedName("Secret") val secret: String = "",
    @SerializedName("Code") val code: String = "",
    @SerializedName("AuthenticationApiUrl") val authenticationApiUrl: String? = null
)

data class JellyfinQuickConnectResult(
    @SerializedName("Authenticated") val authenticated: Boolean = false,
    @SerializedName("Secret") val secret: String = ""
)

data class JellyfinUserDataDto(
    @SerializedName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0L,
    @SerializedName("PlayCount") val playCount: Int = 0,
    @SerializedName("IsFavorite") val isFavorite: Boolean = false,
    @SerializedName("Played") val played: Boolean = false,
    @SerializedName("LastPlayedDate") val lastPlayedDate: String? = null
)

data class JellyfinItemDto(
    @SerializedName("Id") val id: String = "",
    @SerializedName("Name") val name: String = "",
    @SerializedName("OriginalTitle") val originalTitle: String? = null,
    @SerializedName("Overview") val overview: String? = null,
    @SerializedName("Type") val type: String = "", // Movie, Series, Season, Episode, CollectionFolder, etc.
    @SerializedName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerializedName("ProductionYear") val productionYear: Int? = null,
    @SerializedName("CommunityRating") val communityRating: Double? = null,
    @SerializedName("CriticRating") val criticRating: Double? = null,
    @SerializedName("OfficialRating") val officialRating: String? = null,
    @SerializedName("Genres") val genres: List<String>? = null,
    @SerializedName("SeriesId") val seriesId: String? = null,
    @SerializedName("SeriesName") val seriesName: String? = null,
    @SerializedName("SeasonId") val seasonId: String? = null,
    @SerializedName("SeasonName") val seasonName: String? = null,
    @SerializedName("IndexNumber") val indexNumber: Int? = null, // Episode number
    @SerializedName("ParentIndexNumber") val parentIndexNumber: Int? = null, // Season number
    @SerializedName("UserData") val userData: JellyfinUserDataDto? = null,
    @SerializedName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerializedName("BackdropImageTags") val backdropImageTags: List<String>? = null,
    @SerializedName("CollectionType") val collectionType: String? = null
)

data class JellyfinItemsResponse(
    @SerializedName("Items") val items: List<JellyfinItemDto> = emptyList(),
    @SerializedName("TotalRecordCount") val totalRecordCount: Int = 0,
    @SerializedName("StartIndex") val startIndex: Int = 0
)

data class JellyfinMediaStreamDto(
    @SerializedName("Type") val type: String = "", // Video, Audio, Subtitle
    @SerializedName("Index") val index: Int = 0,
    @SerializedName("Codec") val codec: String? = null,
    @SerializedName("Language") val language: String? = null,
    @SerializedName("DisplayTitle") val displayTitle: String? = null,
    @SerializedName("IsDefault") val isDefault: Boolean = false,
    @SerializedName("IsForced") val isForced: Boolean = false,
    @SerializedName("IsExternal") val isExternal: Boolean = false,
    @SerializedName("DeliveryUrl") val deliveryUrl: String? = null,
    @SerializedName("Path") val path: String? = null,
    @SerializedName("Width") val width: Int? = null,
    @SerializedName("Height") val height: Int? = null
)

data class JellyfinMediaSourceDto(
    @SerializedName("Id") val id: String = "",
    @SerializedName("Container") val container: String? = null,
    @SerializedName("Path") val path: String? = null,
    @SerializedName("Protocol") val protocol: String? = null,
    @SerializedName("SupportsDirectPlay") val supportsDirectPlay: Boolean = false,
    @SerializedName("SupportsDirectStream") val supportsDirectStream: Boolean = false,
    @SerializedName("SupportsTranscoding") val supportsTranscoding: Boolean = false,
    @SerializedName("DirectStreamUrl") val directStreamUrl: String? = null,
    @SerializedName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerializedName("TranscodingSubProtocol") val transcodingSubProtocol: String? = null,
    @SerializedName("TranscodingContainer") val transcodingContainer: String? = null,
    @SerializedName("MediaStreams") val mediaStreams: List<JellyfinMediaStreamDto> = emptyList()
)

data class JellyfinPlaybackInfoDto(
    @SerializedName("MediaSources") val mediaSources: List<JellyfinMediaSourceDto> = emptyList(),
    @SerializedName("PlaySessionId") val playSessionId: String? = null
)

data class JellyfinPlaybackProgressRequest(
    @SerializedName("ItemId") val itemId: String,
    @SerializedName("PlaySessionId") val playSessionId: String? = null,
    @SerializedName("MediaSourceId") val mediaSourceId: String? = null,
    @SerializedName("PositionTicks") val positionTicks: Long = 0L,
    @SerializedName("IsPaused") val isPaused: Boolean = false,
    @SerializedName("EventName") val eventName: String? = null
)
