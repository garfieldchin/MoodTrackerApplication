package com.example.moodlog.data

import android.content.Context

/**
 * 「我想成为的那个人」和「给今天的自己」。
 *
 * 两句话都存在本机，不联网、不上传。
 * 第一句是靶子：没有它，总结页就没法说「离那个人还有多远」，只剩一堆数字。
 * 第二句只属于某一天 —— 它是那一页里唯一由你生产的内容。
 */
object MeStore {

    private const val FILE = "moodlog_me"
    private const val KEY_GOAL = "goal"
    private const val PREFIX_NOTE = "note_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun goal(context: Context): String =
        prefs(context).getString(KEY_GOAL, "")?.trim().orEmpty()

    fun saveGoal(context: Context, text: String) {
        prefs(context).edit().putString(KEY_GOAL, text.trim()).apply()
    }

    /** 某一天写给自己那一句。没写过就是空的，不编。 */
    fun note(context: Context, dateKey: String): String =
        prefs(context).getString(PREFIX_NOTE + dateKey, "")?.trim().orEmpty()

    fun saveNote(context: Context, dateKey: String, text: String) {
        prefs(context).edit().putString(PREFIX_NOTE + dateKey, text.trim()).apply()
    }
}
