package com.example.moodlog.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 第三条的名字 —— 默认是「想确认」，但它可以由你改。
 *
 * 同一条尺子，在不同的人那里是不同的事：有人是「想确认TA在哪」，
 * 有人其实是「想接触」「想靠近」「想证实」。一个写死的名字会替你把那件事定义掉，
 * 而这件事只有你自己说得准。所以名字（和它下面那句小字）归你。
 *
 * 改的只是这两个字：底下的数字、记录、CSV、长图一条不少，只是那一栏换了名字。
 * 跟 LossMode 一样是个全局状态，读它的地方不用传 context。
 */
object ConfirmLabel {
    const val DEFAULT_NAME = "想确认"
    const val DEFAULT_HINT = "有多想确认TA在哪、在做什么"
    /** 改了名字却没改那句小字时用它 —— 不能让小字还写着原来那件事。 */
    const val NEUTRAL_HINT = "现在有多想"

    var name by mutableStateOf(DEFAULT_NAME)
        private set
    var hint by mutableStateOf(DEFAULT_HINT)
        private set

    private const val FILE = "label"
    private const val KEY_NAME = "confirm_name"
    private const val KEY_HINT = "confirm_hint"

    fun load(context: Context) {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        name = sp.getString(KEY_NAME, null)?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_NAME
        hint = sp.getString(KEY_HINT, null)?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_HINT
    }

    fun set(context: Context, newName: String, newHint: String) {
        val n = newName.trim().takeIf { it.isNotEmpty() } ?: DEFAULT_NAME
        val h = newHint.trim().takeIf { it.isNotEmpty() } ?: NEUTRAL_HINT
        // 名字换了、小字还停在默认那一句的话，那句说的就不是同一件事了 —— 换成中性的。
        val finalHint = if (n != DEFAULT_NAME && h == DEFAULT_HINT) NEUTRAL_HINT else h
        name = n
        hint = finalHint
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, n)
            .putString(KEY_HINT, finalHint)
            .apply()
    }

    fun reset(context: Context) = set(context, DEFAULT_NAME, DEFAULT_HINT)
}
