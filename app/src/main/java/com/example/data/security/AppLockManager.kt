package com.example.data.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AutoLockTimeout(val seconds: Int, val label: String) {
    IMMEDIATELY(0, "Immediately upon backgrounding"),
    SECONDS_30(30, "30 seconds"),
    MINUTE_1(60, "1 minute"),
    MINUTES_2(120, "2 minutes"),
    MINUTES_5(300, "5 minutes"),
    MINUTES_15(900, "15 minutes")
}

class AppLockManager(context: Context) {

    private val prefs = context.getSharedPreferences("masterkey_lock_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_KEY_TIMEOUT_SECONDS = "timeout_seconds"
        private const val DEFAULT_TIMEOUT_SECONDS = 0 // Immediately
        private const val PREF_KEY_FLAG_SECURE = "pref_flag_secure"
    }

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _selectedTimeout = MutableStateFlow(loadTimeoutPreference())
    val selectedTimeout: StateFlow<AutoLockTimeout> = _selectedTimeout.asStateFlow()

    // Default to false so the app renders visibly in browser streaming emulators
    private val _isFlagSecureEnabled = MutableStateFlow(prefs.getBoolean(PREF_KEY_FLAG_SECURE, false))
    val isFlagSecureEnabled: StateFlow<Boolean> = _isFlagSecureEnabled.asStateFlow()

    fun setFlagSecureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_FLAG_SECURE, enabled).apply()
        _isFlagSecureEnabled.value = enabled
    }

    private var lastBackgroundTimestamp: Long = 0L

    private fun loadTimeoutPreference(): AutoLockTimeout {
        val seconds = prefs.getInt(PREF_KEY_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS)
        return AutoLockTimeout.values().find { it.seconds == seconds } ?: AutoLockTimeout.IMMEDIATELY
    }

    fun setTimeoutPreference(timeout: AutoLockTimeout) {
        prefs.edit().putInt(PREF_KEY_TIMEOUT_SECONDS, timeout.seconds).apply()
        _selectedTimeout.value = timeout
    }

    fun unlock() {
        _isUnlocked.value = true
        lastBackgroundTimestamp = 0L
    }

    fun lock() {
        _isUnlocked.value = false
        lastBackgroundTimestamp = 0L
    }

    fun onAppBackgrounded() {
        if (_isUnlocked.value) {
            lastBackgroundTimestamp = System.currentTimeMillis()
            if (_selectedTimeout.value == AutoLockTimeout.IMMEDIATELY) {
                _isUnlocked.value = false
            }
        }
    }

    fun onAppForegrounded() {
        if (_isUnlocked.value && lastBackgroundTimestamp > 0L) {
            val elapsedMillis = System.currentTimeMillis() - lastBackgroundTimestamp
            val thresholdMillis = _selectedTimeout.value.seconds * 1000L
            if (elapsedMillis >= thresholdMillis) {
                _isUnlocked.value = false
            }
        }
    }
}
