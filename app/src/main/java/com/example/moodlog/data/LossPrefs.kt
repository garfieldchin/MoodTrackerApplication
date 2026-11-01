package com.example.moodlog.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 「我正在经历失去重要的人」这个开关。
 *
 * 它管的只有一件事：记录页要不要多问那三条关于 TA 的（想念TA / 想联系 / 想确认）。
 * 恐慌是通用的，不在它管的范围里 —— 没在经历失去的人一样会慌。
 *
 * 它<b>只管问不问、显不显示，一行数据都不删</b>：
 * 关掉之后，以前记的想念 8 分、想联系 9 分一条不少，
 * 全部记录、CSV、长图里照常能读到；哪天再打开，它们原样回来。
 *
 * 设定一次就记住，不在启动时问，也不在记录时问 ——
 * 每次开 app 都被问一遍「你现在是不是……」是最烦人的一种体贴。
 */
/**
 * 开关的当前值，放在这里是为了让界面能自己跟着变：
 * 设置页拨一下，回到记录页那三条立刻就没了，不用重进 app、不用刷新。
 */
object LossMode {
    var on by mutableStateOf(false)
}

object LossPrefs {
    private const val FILE = "loss"
    private const val KEY = "on"
    private const val KEY_SETTLED = "settled"

    fun on(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)

    /** 用户自己动过之后就不再自动判定。 */
    private fun settled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_SETTLED, false)

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, on).putBoolean(KEY_SETTLED, true).apply()
    }

    /**
     * 只在第一次跑：库里已经记过想念 / 想联系 / 想确认的人，说明正在经历，
     * 升级上来不该发现那三条不见了 —— 自动替他打开。
     * 全新安装的人默认关着，走通用模式。
     */
    fun settleIfNeeded(context: Context, repo: MoodRepository) {
        if (settled(context)) return
        val has = repo.records.any { it.miss > 0 || it.contact > 0 || it.confirm > 0 }
        set(context, has)
    }
}
