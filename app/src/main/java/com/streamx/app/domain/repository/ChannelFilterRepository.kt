package com.streamx.app.domain.repository

import com.streamx.app.domain.model.ActiveFilterState
import com.streamx.app.domain.model.ChannelType
import com.streamx.app.domain.model.MediaItem
import kotlinx.coroutines.flow.StateFlow

interface ChannelFilterRepository {
    val channelFiltersFlow: StateFlow<List<ChannelType>>
    suspend fun loadChannelFilters(remoteUrl: String? = null): List<ChannelType>
    fun filterMediaItems(items: List<MediaItem>, filterState: ActiveFilterState): List<MediaItem>
    suspend fun discoverMediaWithFilters(filterState: ActiveFilterState): List<MediaItem>
}
