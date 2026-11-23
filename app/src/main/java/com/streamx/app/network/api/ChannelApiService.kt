package com.streamx.app.network.api

import com.streamx.app.network.model.ChannelFilterResponseDto
import retrofit2.http.GET
import retrofit2.http.Url

interface ChannelApiService {

    @GET
    suspend fun getChannelFilters(@Url endpointUrl: String): ChannelFilterResponseDto

    @GET("api/v1/channel/filters")
    suspend fun getDefaultChannelFilters(): ChannelFilterResponseDto
}
