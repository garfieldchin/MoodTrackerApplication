package com.example.moodlog.data

import com.example.moodlog.util.logicalTimeString

/**
 * 一天的「大白话版」。
 *
 * 三个地方要用它：【我】页复制出去粘给 ChatGPT、总结的长图、以及必要的导出。
 * 所以它是纯数据 + 纯函数，不碰界面。
 *
 * 规矩和别处一致：不打分、不评级、不发徽章。数据少就少说，不编。
 */
data class DayDigest(
    val dayLabel: String,
    val headline: String,
    val emotion: String,
    val life: String,
    val distance: String,
    val goal: String,
    val timeline: List<String>,      // 「02:26　想念 8 · 恐慌 9…」
    val diaries: List<String>,       // 「02:26　TA把状态改成…」
    val charges: List<String>,       // 「07:40　+3　出门走了一圈　尽管不想动」
    val meets: List<String>,         // 「02:10　看到一张旧照片｜想念、委屈｜有人说句我在」
    val checkins: List<String>,      // 「07:40　我起床了　尽管昨晚几乎没睡」
    val moves: List<String>,         // 「19:10　羽毛球　45 分钟　和老陈」
    val note: String
)

fun buildDayDigest(
    dayLabel: String,
    records: List<MoodRecord>,
    events: List<LifeEvent>,
    stances: List<Stance>,
    dims: LifeDims?,
    goal: String,
    note: String,
    encounters: List<Encounter> = emptyList(),
    moves: List<Move> = emptyList(),
    isToday: Boolean = true
): DayDigest {
    val timeline = records.map {
        "${it.timestamp.logicalTimeString()}　想念 ${it.miss.answerText()} · " +
            "恐慌 ${it.panic.answerText()} · " +
            "想联系 ${it.contact.answerText()} · ${ConfirmLabel.name} ${it.confirm.answerText()}"
    }
    val diaries = records.filter { it.diary.isNotBlank() }
        .map { "${it.timestamp.logicalTimeString()}　${it.diary.trim()}" }
    val checkins = stances
        .filter { it.keys.isNotEmpty() || it.custom.isNotBlank() }
        .sortedBy { it.ts }
        .map { s ->
            val head = s.labels().joinToString("、")
            val at = if (s.ts > 0L) "${s.ts.logicalTimeString()}　" else ""
            if (s.note.isBlank()) "$at$head" else "$at$head　${s.note.trim()}"
        }

    return DayDigest(
        dayLabel = dayLabel,
        headline = headlineOf(records, isToday = isToday),
        emotion = emotionText(records),
        life = lifeText(events, dims, stances, encounters),
        distance = distanceLine(goal, events.size),
        goal = goal,
        timeline = timeline,
        diaries = diaries,
        charges = chargeLines(events),
        meets = meetLines(encounters),
        checkins = checkins,
        moves = moveLines(moves),
        note = note
    )
}

/**
 * 【我话事】逐条列出来。
 *
 * 之前复制出去的那段只说「最补的是 X（+3）、最耗的是 Y（−2）」——
 * 看完了还是不知道这一天到底做了什么。这里一条条写：几点、它给你充了多少或耗了多少、做了什么。
 *
 * 没打分的写「没打分」，不写成 0：没打分是一个空白，不是一个中性的 0 分。
 */
fun chargeLines(events: List<LifeEvent>): List<String> =
    events.sortedBy { it.timestamp }.map { e ->
        val at = e.timestamp.logicalTimeString()
        val amount = when {
            e.delta == 0 -> "没打分"
            e.delta > 0 -> "+${e.delta}"
            else -> e.delta.toString()
        }
        val what = labelOf(e.tags, e.note)
        val extra = if (e.tags.isNotEmpty() && e.note.isNotBlank()) "　${e.note.trim()}" else ""
        "$at　$amount　$what$extra"
    }

/**
 * 【我遇见】那一笔的四句话。
 *
 * 复制出去给别人看的时候，这几句是最有分量的一段 ——
 * 它是那一天你自己的原话。四句不一定都答了，答了的才出现，没答的留白。
 */
fun meetLines(encounters: List<Encounter>): List<String> =
    encounters.sortedBy { it.timestamp }.map { e ->
        val at = e.timestamp.logicalTimeString()
        val parts = listOf(e.happened, e.feeling, e.need, e.doNext)
            .map { it.trim() }
            .filter { it.isNotBlank() }
        "$at　" + parts.joinToString("｜")
    }

/**
 * 【运动】那几笔。
 *
 * 只陈述「动了什么、多久」，不写消耗、不写达标 ——
 * 复制出去给咨询师看的时候，这一句是「那天他出门打了球」，不是一份成绩单。
 */
fun moveLines(moves: List<Move>): List<String> =
    moves.sortedBy { it.timestamp }.map { m ->
        val at = m.timestamp.logicalTimeString()
        val howLong = when {
            m.minutes <= 0 -> "没记时长"
            m.minutes < 60 -> "${m.minutes} 分钟"
            m.minutes % 60 == 0 -> "${m.minutes / 60} 小时"
            else -> "${m.minutes / 60} 小时 ${m.minutes % 60} 分"
        }
        val head = listOf(m.tag.trim(), howLong).filter { it.isNotBlank() }.joinToString("　")
        if (m.note.isBlank()) "$at　$head" else "$at　$head　${m.note.trim()}"
    }

/** 复制出去粘给别人看的那一段。粘到聊天框里就能直接开始聊。 */
fun DayDigest.toPlainText(): String {
    val sb = StringBuilder()
    sb.appendLine("$dayLabel。")
    sb.appendLine()
    if (headline.isNotBlank()) {
        sb.appendLine(headline)
        sb.appendLine()
    }
    if (emotion.isNotBlank()) {
        sb.appendLine("情绪")
        sb.appendLine(emotion)
        sb.appendLine()
    }
    if (life.isNotBlank()) {
        sb.appendLine("生活")
        sb.appendLine(life)
        sb.appendLine()
    }
    if (charges.isNotEmpty()) {
        sb.appendLine("我话事 · 这一天做了什么")
        charges.forEach { sb.appendLine(it) }
        sb.appendLine()
    }
    if (moves.isNotEmpty()) {
        sb.appendLine("运动")
        moves.forEach { sb.appendLine(it) }
        sb.appendLine()
    }
    if (meets.isNotEmpty()) {
        sb.appendLine("我遇见 · 那天说的几句话")
        meets.forEach { sb.appendLine(it) }
        sb.appendLine()
    }
    if (distance.isNotBlank()) {
        sb.appendLine("离我想成为的那个人")
        sb.appendLine(distance)
        sb.appendLine()
    }
    if (timeline.isNotEmpty()) {
        sb.appendLine("今天记下的几次")
        timeline.forEach { sb.appendLine(it) }
        sb.appendLine()
    }
    if (diaries.isNotEmpty()) {
        sb.appendLine("当时写的")
        diaries.forEach { sb.appendLine(it) }
        sb.appendLine()
    }
    if (checkins.isNotEmpty()) {
        sb.appendLine("打卡那一栏以前记下的")
        checkins.forEach { sb.appendLine(it) }
        sb.appendLine()
    }
    if (note.isNotBlank()) {
        sb.appendLine("我给这天留的一句")
        sb.appendLine(note.trim())
        sb.appendLine()
    }
    if (goal.isNotBlank()) {
        sb.appendLine("我想成为的那个人：$goal")
    }
    return sb.toString().trimEnd() + "\n"
}

// ------------------------------------------------------------------ 文案

/** 开头那一句。数据少的时候承认少，不编。 */
fun headlineOf(records: List<MoodRecord>, isToday: Boolean): String = when {
    records.isEmpty() -> if (isToday) {
        "今天还没有记过。打开情绪页，把那一下拉出来就好。"
    } else {
        "那一天没有留下记录。空白不等于那天白过。"
    }

    records.size == 1 -> "只记了一次。一次撑不起「今天怎么样」—— 它只是那天你愿意留下的那一个点。"

    else -> {
        val peak = records.filter { it.panic >= 0 }.maxByOrNull { it.panic }
        val base = "记了 ${records.size} 次"
        if (peak == null) base else {
            val time = peak.timestamp.logicalTimeString()
            val minutes = peak.toOutcomeMinutes
            val tail = when {
                minutes == null -> "。"
                minutes < 1 -> "，不到一分钟你就站了回来。"
                else -> "，那一次你在 $minutes 分钟后站了回来。"
            }
            "$base。最慌的一次在 $time，恐慌到 ${peak.panic} 分$tail"
        }
    }
}

fun emotionText(records: List<MoodRecord>): String {
    if (records.isEmpty()) return "这一天没有情绪上的记录。"
    // 四个刻度都允许没填。没填的不进统计 —— 它是一个空白，不是一个 0 分。
    val missList = records.map { it.miss }.filter { it >= 0 }
    val panicList = records.map { it.panic }.filter { it >= 0 }
    val panicPeak = panicList.maxOrNull()
    val panicLast = records.map { it.panic }.lastOrNull { it >= 0 }
    val peak = records.filter { it.panic >= 0 }.maxByOrNull { it.panic }

    val s1 = when {
        missList.isEmpty() -> "想念那一栏一次都没填 —— 没填不是 0。"
        missList.min() >= 5 -> "想念一整天都没有低过 ${missList.min()} 分。"
        else -> "想念在 ${missList.min()} 到 ${missList.max()} 之间来回。"
    }
    val s2 = when {
        panicPeak == null -> "恐慌那一栏一次都没填。"
        panicPeak >= 8 ->
            "恐慌最高到 $panicPeak 分（${peak?.timestamp?.logicalTimeString()}），最后落回 $panicLast 分。"
        panicPeak >= 5 ->
            "恐慌到过 $panicPeak 分，一直在中等的位置晃，最后 $panicLast 分。"
        else ->
            "恐慌不高，最高 $panicPeak 分。"
    }
    val peakContact = records.filter { it.contact >= 0 }.maxByOrNull { it.contact }
    val peakConfirm = records.filter { it.confirm >= 0 }.maxByOrNull { it.confirm }
    val s3 = if (peak != null && peakContact?.id == peak.id && peakConfirm?.id == peak.id) {
        "想联系和${ConfirmLabel.name}的高峰是同一个时刻 —— ${peak.timestamp.logicalTimeString()}。"
    } else ""
    return (s1 + s2 + s3).ifBlank { "这一天情绪上没有可说的。" }
}

fun lifeText(
    events: List<LifeEvent>,
    dims: LifeDims?,
    stances: List<Stance>,
    encounters: List<Encounter> = emptyList()
): String {
    val sb = StringBuilder()
    // 打卡那一栏已经撤了。历史还留着就照旧说一句，没留过就不再提它 —— 不为一个已经删掉的栏催人。
    val checked = stances.filter { it.keys.isNotEmpty() || it.custom.isNotBlank() }
    if (checked.isNotEmpty()) {
        sb.append("打卡那一栏以前记了 ${checked.size} 件：")
        sb.append(checked.flatMap { it.labels() }.distinct().take(6).joinToString("、"))
        sb.append("。")
    }
    if (events.isNotEmpty()) {
        // 说清楚这一天到底做了几件、往哪个方向走了多少。逐条写了什么在下面「我话事」那一段。
        val total = events.sumOf { it.delta }
        val up = events.count { it.delta > 0 }
        val down = events.count { it.delta < 0 }
        val flat = events.count { it.delta == 0 }
        sb.append("我话事记了 ${events.size} 件，合计 ${signed(total)}。")
        sb.append("充到电的 $up 件，被消耗的 $down 件")
        if (flat > 0) sb.append("，还有 $flat 件只记了没打分")
        sb.append("。")
    } else {
        sb.append("我话事这一栏今天空的 —— 没记就是没记，不代表这一天没做事。")
    }
    // 我遇见的笔数也在这句总账里说一句。它是同一天的一部分，
    // 只在下面那段列出来、上面这句提都不提，读的人会以为那天只做了那些事。
    if (encounters.isNotEmpty()) {
        sb.append("我遇见写了 ${encounters.size} 笔。")
    }
    if (dims != null) {
        val items = LIFE_DIMS.map { it.name to it.get(dims) }
        val high = items.maxByOrNull { it.second }
        val low = items.minByOrNull { it.second }
        if (high != null && low != null) {
            sb.append("六维里${high.first}最高（${high.second}），${low.first}最低（${low.second}）。")
        }
    } else {
        sb.append("六个问题没有答。")
    }
    return sb.toString()
}

/** 带符号的整数：+3 / −2 用的是 ASCII 的减号，粘贴到任何地方都不会变成乱码。 */
private fun signed(n: Int): String = if (n > 0) "+$n" else n.toString()

private fun labelOf(tags: List<String>, note: String): String =
    when {
        tags.isNotEmpty() -> tags.joinToString("、")
        note.isNotBlank() -> note
        else -> "那一件"
    }

/**
 * 离那个人还有多远。不给分数，只给一句话 —— 一处 moi 也没写「几分」。
 *
 * 这里看的是【我话事】记了几件：打卡那一栏已经撤了，再拿它当尺子，
 * 结果永远是「没记下什么」，那是栏位的问题，不是那天的问题。
 */
fun distanceLine(goal: String, chargeCount: Int): String = when {
    goal.isBlank() -> ""
    chargeCount == 0 -> "靶子已经写下来了：$goal。这一天在「我话事」里没有记下什么，看不出方向 —— 不猜。"
    else -> "靶子：$goal。这一天在「我话事」里记下了 $chargeCount 件。"
}
