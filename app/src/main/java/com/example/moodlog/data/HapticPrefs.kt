package com.example.moodlog.data

import android.content.Context

/**
 * 点击震动的总开关。默认开。
 *
 * 手感这个东西有人离不开、有人受不了，所以必须能关 ——
 * 关掉之后除了不震，一切都照常：该染色的染色，该提示的提示。
 */
object HapticPrefs {
    private const val FILE = "haptic"
    private const val KEY = "on"

    fun on(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}
