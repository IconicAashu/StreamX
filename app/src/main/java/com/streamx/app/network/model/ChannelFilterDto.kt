package com.streamx.app.network.model

import com.google.gson.annotations.SerializedName

data class ChannelFilterResponseDto(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("message") val message: String = "ok",
    @SerializedName("data") val data: ChannelFilterDataDto? = null
)

data class ChannelFilterDataDto(
    @SerializedName("typeList") val typeList: List<ChannelTypeDto> = emptyList()
)

data class ChannelTypeDto(
    @SerializedName("channelId") val channelId: Int,
    @SerializedName("channelName") val channelName: String,
    @SerializedName("subjectType") val subjectType: Int? = 1,
    @SerializedName("style") val style: ChannelStyleDto? = null,
    @SerializedName("items") val items: List<ChannelFilterItemDto> = emptyList()
)

data class ChannelStyleDto(
    @SerializedName("colNum") val colNum: Int = 3,
    @SerializedName("shape") val shape: String = "Vertical"
)

data class ChannelFilterItemDto(
    @SerializedName("filterType") val filterType: String,
    @SerializedName("title") val title: String,
    @SerializedName("showOut") val showOut: Boolean = false,
    @SerializedName("filterVals") val filterVals: List<String>? = null,
    @SerializedName("filterValsV2") val filterValsV2: List<FilterOptionDto>? = null,
    @SerializedName("rangeVals") val rangeVals: RangeValsDto? = null
)

data class FilterOptionDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String
)

data class RangeValsDto(
    @SerializedName("minVal") val minVal: String? = "0",
    @SerializedName("maxVal") val maxVal: String? = "10"
)
