package com.elbruz.vrvideo.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elbruz.vrvideo.data.local.UserPrefs
import com.elbruz.vrvideo.model.SearchEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * Holds browser state: current search engine, last-visited URL,
 * and the list of search engines read from DataStore.
 */
class MainViewModel(
    val prefs: UserPrefs,
    val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val searchEngines = prefs.searchEnginesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchEngine.DEFAULT_ENGINES,
    )

    val currentEngineId = prefs.currentEngineIdFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "google",
    )

    fun currentEngine(): SearchEngine {
        val id = currentEngineId.value
        return searchEngines.value.find { it.id == id }
            ?: SearchEngine.DEFAULT_ENGINES.first { it.isDefault }
    }

    companion object {
        fun provideFactory(prefs: UserPrefs): ViewModelProvider.Factory =
            MainViewModelFactory(prefs)
    }
}
