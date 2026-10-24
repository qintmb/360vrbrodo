package com.elbruz.vrvideo.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.elbruz.vrvideo.data.local.UserPrefs

/** Factory that injects UserPrefs into MainViewModel. */
class MainViewModelFactory(
    private val prefs: UserPrefs,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MainViewModel(prefs, SavedStateHandle()) as T
}
