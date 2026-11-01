package com.example.moodlog.data

import android.content.Context

/**
 * 深色模式的三档：自动（跟着 app 自己的夜晚）/ 一直浅色 / 一直深色。
 *
 * 默认自动 —— 这个 app 的「晚上」由它自己定义（18:00 到次日 05:59），
 * 不是系统的日落，也不是系统的深色开关：难熬的那几个小时往往在夜里，
 * 按系统开关的话，白天躺在被窝里和凌晨三点坐在地上会是同一种颜色。
 */
object NightPrefs {
    private const val FILE = "night"
    private const val KEY = "mode"

    fun get(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY, 0)

    fun set(context: Context, mode: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(KEY, mode).apply()
    }
}
