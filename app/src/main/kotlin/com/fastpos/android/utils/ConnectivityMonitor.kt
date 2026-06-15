package com.fastpos.android.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityMonitor @Inject constructor() {
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline

    @Volatile var isPeerMode: Boolean = false
        private set

    fun setPeerMode(enabled: Boolean) { isPeerMode = enabled }
    fun setOnline()  { _isOnline.value = true }
    fun setOffline() { _isOnline.value = false }
}
