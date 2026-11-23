package com.streamx.app.network.jellyfin.api

import com.streamx.app.network.jellyfin.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface JellyfinApiService {

    @GET("System/Info/Public")
    suspend fun getPublicSystemInfo(): Response<JellyfinPublicSystemInfo>

    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(
        @Body request: JellyfinAuthenticateUserByName
    ): Response<JellyfinAuthenticationResult>

    @POST("QuickConnect/Initiate")
    suspend fun initiateQuickConnect(): Response<JellyfinQuickConnectInitiate>

    @GET("QuickConnect/Connect")
    suspend fun checkQuickConnect(
        @Query("secret") secret: String
    ): Response<JellyfinQuickConnectResult>

    @POST("Users/AuthenticateWithQuickConnect")
    suspend fun authenticateWithQuickConnect(
        @Body body: Map<String, String>
    ): Response<JellyfinAuthenticationResult>

    @GET("Users/{userId}/Views")
    suspend fun getUserViews(
        @Path("userId") userId: String
    ): Response<JellyfinItemsResponse>

    @GET("Users/{userId}/Items")
    suspend fun getItems(
        @Path("userId") userId: String,
        @Query("IncludeItemTypes") includeItemTypes: String? = null,
        @Query("Recursive") recursive: Boolean = true,
        @Query("ParentId") parentId: String? = null,
        @Query("SortBy") sortBy: String = "SortName",
        @Query("SortOrder") sortOrder: String = "Ascending",
        @Query("Limit") limit: Int = 50,
        @Query("StartIndex") startIndex: Int = 0,
        @Query("Fields") fields: String = "Overview,Genres,ImageTags,BackdropImageTags,PrimaryImageAspectRatio,ProductionYear,CommunityRating,CriticRating,OfficialRating,RunTimeTicks,UserData"
    ): Response<JellyfinItemsResponse>

    @GET("Users/{userId}/Items/Resume")
    suspend fun getContinueWatching(
        @Path("userId") userId: String,
        @Query("Limit") limit: Int = 20,
        @Query("Fields") fields: String = "Overview,Genres,ImageTags,BackdropImageTags,PrimaryImageAspectRatio,ProductionYear,CommunityRating,CriticRating,OfficialRating,RunTimeTicks,UserData"
    ): Response<JellyfinItemsResponse>

    @GET("Users/{userId}/Items/Latest")
    suspend fun getLatestItems(
        @Path("userId") userId: String,
        @Query("ParentId") parentId: String? = null,
        @Query("IncludeItemTypes") includeItemTypes: String? = null,
        @Query("Limit") limit: Int = 20,
        @Query("Fields") fields: String = "Overview,Genres,ImageTags,BackdropImageTags,PrimaryImageAspectRatio,ProductionYear,CommunityRating,CriticRating,OfficialRating,RunTimeTicks,UserData"
    ): Response<List<JellyfinItemDto>>

    @GET("Shows/{seriesId}/Seasons")
    suspend fun getSeasons(
        @Path("seriesId") seriesId: String,
        @Query("userId") userId: String,
        @Query("Fields") fields: String = "Overview,ImageTags,BackdropImageTags,PrimaryImageAspectRatio,ProductionYear,UserData"
    ): Response<JellyfinItemsResponse>

    @GET("Shows/{seriesId}/Episodes")
    suspend fun getEpisodes(
        @Path("seriesId") seriesId: String,
        @Query("seasonId") seasonId: String? = null,
        @Query("userId") userId: String,
        @Query("Fields") fields: String = "Overview,ImageTags,PrimaryImageAspectRatio,CommunityRating,RunTimeTicks,UserData"
    ): Response<JellyfinItemsResponse>

    @GET("Users/{userId}/Items/{itemId}")
    suspend fun getItemDetails(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String
    ): Response<JellyfinItemDto>

    @POST("Items/{itemId}/PlaybackInfo")
    suspend fun getPlaybackInfo(
        @Path("itemId") itemId: String,
        @Query("UserId") userId: String
    ): Response<JellyfinPlaybackInfoDto>

    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(
        @Body request: JellyfinPlaybackProgressRequest
    ): Response<ResponseBody>

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(
        @Body request: JellyfinPlaybackProgressRequest
    ): Response<ResponseBody>

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(
        @Body request: JellyfinPlaybackProgressRequest
    ): Response<ResponseBody>
}
