package com.example.moodlog.data

import android.content.Context

/**
 * 安全底座那个「临时浮现的入口」用过没有。
 * 只有连着两天都很难熬的时候它才出现，点过一次当天就不再出现——
 * 它不是常驻入口，常驻会把「你可能撑不住」这件事天天摆在眼前。
 */
object SafetyPrefs {

    private const val FILE = "moodlog_safety"
    private const val KEY = "entry_used_on"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 今天已经点过了吗。 */
    fun usedToday(context: Context): Boolean =
        prefs(context).getString(KEY, "") == todayKey()

    fun markUsed(context: Context) {
        prefs(context).edit().putString(KEY, todayKey()).apply()
    }
}
