package com.streamx.app.presentation.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamx.app.domain.repository.CatalogRepository
import com.streamx.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WelcomeViewModel(
    private val catalogRepository: CatalogRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _posters = MutableStateFlow<List<String>>(emptyList())
    val posters: StateFlow<List<String>> = _posters.asStateFlow()

    init {
        viewModelScope.launch {
            _posters.value = catalogRepository.getAllPostersForWelcome()
        }
    }

    fun completeOnboarding(onCompleted: () -> Unit) {
        viewModelScope.launch {
            userPreferencesRepository.setOnboardingCompleted(true)
            onCompleted()
        }
    }
}
