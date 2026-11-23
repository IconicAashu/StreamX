package com.streamx.app.presentation.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamx.app.domain.model.MediaItem
import com.streamx.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WatchlistViewModel(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val watchlist: StateFlow<List<MediaItem>> = userPreferencesRepository.watchlistFlow

    fun removeFromWatchlist(item: MediaItem) {
        viewModelScope.launch {
            userPreferencesRepository.toggleWatchlist(item)
        }
    }
}
