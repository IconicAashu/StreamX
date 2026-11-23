package com.streamx.app.data.mapper

import com.streamx.app.domain.model.*
import com.streamx.app.network.model.*

object ChannelFilterMapper {

    fun toDomain(dto: ChannelTypeDto): ChannelType {
        val style = ChannelStyle(
            colNum = dto.style?.colNum ?: 3,
            shape = dto.style?.shape ?: "Vertical"
        )

        val filters = dto.items.map { itemDto ->
            val filterType = ChannelFilterType.fromKey(itemDto.filterType)

            // Map options from filterValsV2 or fallback to filterVals
            val options = itemDto.filterValsV2?.map { optDto ->
                FilterOption(id = optDto.id, name = optDto.name)
            } ?: itemDto.filterVals?.map { valStr ->
                FilterOption(id = valStr, name = valStr)
            } ?: emptyList()

            val range = itemDto.rangeVals?.let { rDto ->
                FilterRange(
                    minVal = rDto.minVal?.toFloatOrNull() ?: 0f,
                    maxVal = rDto.maxVal?.toFloatOrNull() ?: 10f
                )
            }

            ChannelFilter(
                type = filterType,
                rawFilterType = itemDto.filterType,
                title = itemDto.title,
                showOut = itemDto.showOut ?: false,
                options = options,
                range = range
            )
        }

        return ChannelType(
            channelId = dto.channelId,
            channelName = dto.channelName,
            subjectType = dto.subjectType ?: 1,
            style = style,
            filters = filters
        )
    }

    fun toDomainList(responseDto: ChannelFilterResponseDto): List<ChannelType> {
        return responseDto.data?.typeList?.map { toDomain(it) } ?: emptyList()
    }
}
