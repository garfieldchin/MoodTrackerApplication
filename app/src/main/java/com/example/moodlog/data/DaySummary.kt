package com.example.moodlog.data

import java.time.LocalDate

/**
 * 【成为 · 我的今天】那一段话。
 *
 * 它<b>只由当天真实存在的记录拼出来</b>：哪一项没有记录，哪一项就不会出现在话里 ——
 * 不会在【我话事】空着的时候说「你完成了自己决定要做的事」，
 * 也不会在情绪一栏空着的时候说「今天有点难」。空白的日子读出来就是空白，
 * 不是失败，也不是需要被填上什么。
 *
 * 它不鼓励、不安慰、不打分。禁用词跟 Verdict 那套一样：
 * 你很棒 / 你真勇敢 / 会好的 / 加油 / 你应该 / 明天会更好 / 你今天不错。
 *
 * 末尾那行「整体状态」在没答六维的时候写「以实际填写为准」——
 * 那不是谦虚，是事实：这天没有那份数据，就没有那个数。
 */
data class DaySummary(
    val headline: String,     // 第一句
    val lines: List<String>,  // 中间几句，有多少数据说多少句
    val facts: String         // 末尾那行状态
)

private fun sg(v: Int): String = if (v > 0) "+$v" else v.toString()

private fun oneDecimal(v: Double): String = String.format("%.1f", v)

fun buildDaySummary(
    day: LocalDate,
    records: List<MoodRecord>,
    events: List<LifeEvent>,
    encounters: List<Encounter>,
    dims: LifeDims?,
    bodyCount: Int
): DaySummary {
    val n = records.size
    val peak = records.map { it.panic }.filter { it >= 0 }.maxOrNull()
    // 「不轻松」只由数据说话：当天真的慌到过 5 分以上才说。
    val hard = peak != null && peak >= 5
    val acted = events.isNotEmpty()
    val sum = events.sumOf { it.delta }
    val up = events.count { it.delta > 0 }
    val cared = bodyCount > 0
    // 开关关着的时候，这一页不该凭空冒出「想念」两个字。
    val emotionWord = if (LossMode.on) "想念和不安" else "不安"

    val headline = when {
        n == 0 && !acted -> "今天这里还是空的。"
        hard && acted -> "今天并不轻松，但你仍然在生活。"
        hard -> "今天并不轻松。"
        acted -> "今天记下了做过的事。"
        n > 0 -> "今天记下了 $n 次情绪。"
        else -> "今天这里还是空的。"
    }

    val lines = mutableListOf<String>()
    if (n > 0) {
        lines += if (peak != null) {
            "${emotionWord}今天出现了 $n 次，最慌的一次到 $peak 分。"
        } else {
            "${emotionWord}今天出现了 $n 次，恐慌那一栏没有填。"
        }
    }
    if (acted) {
        lines += "你记下了 ${events.size} 件自己决定要做的事" +
            (if (up > 0) "，其中 $up 件是充到电的" else "") +
            "，合计 ${sg(sum)}。"
    }
    if (cared) {
        lines += "也照顾了自己的生活（身体那一栏记了 $bodyCount 笔）。"
    }
    if (hard && acted) {
        // 难受和行动不是互相抵消的两件事 —— 这句话只在两者都有记录的时候才出现。
        lines += "这两件事可以同时成立。"
        lines += "今天的难受是真实的，你做出的行动也是真实的。"
    }
    if (encounters.isNotEmpty()) {
        lines += "另外写下了 ${encounters.size} 笔「我遇见」。"
    }

    val emotionState =
        if (n > 0) "$n 次" + (peak?.let { " · 峰值 $it" } ?: " · 没填恐慌") else "没有记录"
    val actionState = if (acted) "${events.size} 件 · 合计 ${sg(sum)}" else "没有记录"
    val wholeState = dims?.let {
        "六维平均 ${oneDecimal(LIFE_DIMS.map { def -> def.get(it) }.average())}"
    } ?: "以实际填写为准"

    return DaySummary(
        headline = headline,
        lines = lines,
        facts = "情绪：$emotionState。行动：$actionState。整体状态：$wholeState。"
    )
}
