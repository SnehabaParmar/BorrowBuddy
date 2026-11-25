package com.borrowbuddy.app.model

import android.content.Context
import android.content.SharedPreferences

object AppPreferencesManager {

    private const val PREFS_NAME = "borrowbuddy_app_preferences"
    private const val KEY_HAPTIC_FEEDBACK = "key_haptic_feedback_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Check whether haptic feedback is enabled.
     * Defaults to true for standard tactile response.
     */
    fun isHapticFeedbackEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HAPTIC_FEEDBACK, true)
    }

    /**
     * Persist user's haptic feedback preference.
     */
    fun setHapticFeedbackEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
    }
}
