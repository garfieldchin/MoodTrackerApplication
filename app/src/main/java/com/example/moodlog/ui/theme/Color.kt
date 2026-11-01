package com.example.moodlog.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 深色模式的总开关。MainActivity 里每半分钟对一次表：
 * 每天 18:00 – 29:59（挂钟 18:00 到次日 05:59）为 true。
 *
 * 颜色都写成 getter 在读这一位 —— 它一翻，整个界面跟着换颜色，
 * 不用重建 Activity，正在写的字也不会丢。
 */
object NightMode {
    /** 跟着时段走：18:00 – 29:59 自动变深色。 */
    const val AUTO = 0
    /** 一直浅色。 */
    const val OFF = 1
    /** 一直深色。 */
    const val ON = 2

    var isDark by mutableStateOf(false)
    /** 当前那一档。改了它，界面立刻跟着变，不用等下一次对表。 */
    var mode by mutableIntStateOf(AUTO)
}

// 四个维度的颜色
val MissColor = Color(0xFFEF7A9B)    // 想TA —— 玫瑰粉
val PanicColor = Color(0xFFF2994A)   // 恐慌 —— 琥珀橙
val ContactColor = Color(0xFF6C9BF2) // 想联系 —— 蓝
val ConfirmColor = Color(0xFF3FB79C) // 想确认 —— 青绿

// 界面主色。下面的语义色全部是 getter：白天一套、夜里一套。
// PrimaryPurple 也是 getter：#8B6FB5 放在 #221E2C 上对比度只有 3.6，
// 小字（「‹ 返回」、日期 ›、说明里的链接）会看不清，夜里换成亮一档的紫。
val PrimaryPurple: Color get() = if (NightMode.isDark) Color(0xFFB49CE0) else Color(0xFF8B6FB5)
val SoftPurple = Color(0xFFB9A3D6)

val TextMain: Color get() = if (NightMode.isDark) Color(0xFFF2EEF8) else Color(0xFF2B2733)
// 说明文字是这一屏里最多的一种字，夜里原来那档灰（#9C94AC）压在深底上偏闷，调亮一档。
val TextGray: Color get() = if (NightMode.isDark) Color(0xFFB4ACC4) else Color(0xFF8A8395)
val GridColor: Color get() = if (NightMode.isDark) Color(0xFF3B3449) else Color(0xFFEDE7F3)
val CardBg: Color get() = if (NightMode.isDark) Color(0xFF221E2C) else Color(0xFFFFFFFF)
val PageBg: Color get() = if (NightMode.isDark) Color(0xFF17131F) else Color(0xFFFAF8FC)

// 卡片描边（原 0xFFEDE7F3）
val CardBorder: Color get() = if (NightMode.isDark) Color(0xFF403952) else Color(0xFFEDE7F3)

// 标签、步进键、未答角标的浅紫底（原 0xFFF5F2F8 / F1ECF8 / F3EFF7 / F0ECF5）
// 夜里原来 #2B2637 和卡片底 #221E2C 只差一点，标签看着像没边框的一块，提亮一档。
val ChipBg: Color get() = if (NightMode.isDark) Color(0xFF332D42) else Color(0xFFF5F2F8)

// 次级正文（原 0xFF3E3651 / 5A5464 / 5C5666）
val SubText: Color get() = if (NightMode.isDark) Color(0xFFD3CCDF) else Color(0xFF5C5666)

// 月历上记过事那天的数字（原 0xFF5F4A85）
val MarkText: Color get() = if (NightMode.isDark) Color(0xFFC0ADE0) else Color(0xFF5F4A85)

// 未答/停用（原 0xFFB8B1C2）
val DisabledC: Color get() = if (NightMode.isDark) Color(0xFF807894) else Color(0xFFB8B1C2)

/**
 * 「还没到的日子」那种字：要比正文弱，但不能弱到看不见。
 * 白天是浅紫灰，夜里如果沿用同一档浅色，它会比正文还亮 —— 于是改用深一档的灰紫。
 */
val FaintText: Color get() = if (NightMode.isDark) Color(0xFF5C5470) else Color(0xFFD9D2E4)
/** 同上，只是用在细线上（分隔、进度槽底）。 */
val FaintLine: Color get() = if (NightMode.isDark) Color(0xFF453E57) else Color(0xFFDDD6E6)

// 成功/已连通的绿（原 0xFF227057）
val SuccessGreen: Color get() = if (NightMode.isDark) Color(0xFF7FD0A6) else Color(0xFF227057)

// 恐慌升高时的六支情绪，顺序与 data/EMOTION_FIELDS 一致
val EmotionColors = listOf(
    Color(0xFF9182C9), // 被抛弃感
    Color(0xFFD0574F), // 愤怒
    Color(0xFFA2729B), // 羞耻
    Color(0xFF6C8BD0), // 恐惧
    Color(0xFF5C9E9E), // 孤独
    Color(0xFFE08A3C)  // 不甘
)

// B 空间的六个维度，顺序与 data/LIFE_DIMS 一致
val LifeDimColors = listOf(
    Color(0xFF3FB79C), // 掌控感
    Color(0xFF639922), // 成就感
    Color(0xFFEF9F27), // 生命力
    Color(0xFF378ADD), // 连接感
    Color(0xFF8B6FB5), // 价值感
    Color(0xFFEF7A9B)  // 愉悦感
)

// 身体干预提示
val CareBg: Color get() = if (NightMode.isDark) Color(0xFF33261C) else Color(0xFFFFF4EC)
val CareBorder: Color get() = if (NightMode.isDark) Color(0xFF6B4A2E) else Color(0xFFF6C9A6)
val CareTitle: Color get() = if (NightMode.isDark) Color(0xFFEDA96A) else Color(0xFFB85C1E)

// 安全底座
val SafeBg: Color get() = if (NightMode.isDark) Color(0xFF33201E) else Color(0xFFFCEFED)
val SafeBorder: Color get() = if (NightMode.isDark) Color(0xFF6B3B35) else Color(0xFFF0C6C0)
val SafeTitle: Color get() = if (NightMode.isDark) Color(0xFFE58D80) else Color(0xFFA33F35)
/** 删除、求助、负向数字。夜里那档要提亮，#C2574C 压在深底上发闷。 */
val DangerRed: Color get() = if (NightMode.isDark) Color(0xFFF09084) else Color(0xFFC2574C)
/** 正向：充电、掌控、站稳。 */
val GainGreen: Color get() = if (NightMode.isDark) Color(0xFF5FD3B4) else Color(0xFF3FB79C)

/**
 * 月历上那根色条的四个档位。
 *
 * 以前只有三档，且「有记录但峰值不到 5」和「根本没记录」共用同一个灰 ——
 * 于是「那天记了，只是不难受」和「那天什么都没记」在日历上是同一个颜色，
 * 等于把人记过的那一天抹掉了。现在四档分开：
 *
 *   没记   → 浅灰（很淡，一眼是空）
 *   0–4    → 绿（那天记了，只是没到 5 分 —— 这是一件好事，值得一个正色）
 *   5–7    → 琥珀
 *   8 以上 → 红
 */
val CalNone: Color get() = if (NightMode.isDark) Color(0xFF3A3450) else Color(0xFFE3DDEB)
val CalLow: Color get() = GainGreen
val CalMid: Color get() = if (NightMode.isDark) Color(0xFFF2B56B) else Color(0xFFF2994A)
val CalHigh: Color get() = DangerRed
