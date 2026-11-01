package com.example.moodlog.data

import com.example.moodlog.util.logicalTimeString
import java.time.LocalDate

/**
 * 今天的那「一句判断」。
 *
 * 它是<b>本地规则算出来的</b>：先判定今天命中哪一类，再从那一类的五句里抽一句。
 * 不调模型、不上网 —— 模型会给同一天写出不同的话，今天说「你已经很努力了」，
 * 明天说「再坚持一下」，那是情绪不稳定的安慰，不是判断。
 *
 * 同一天永远是同一句（用日期做种子），换一天才换。
 * 抽到昨天那句就顺移一位，免得连着两天读到一模一样的一句话。
 *
 * 禁用词：你很棒 / 你真勇敢 / 会好的 / 加油 / 你应该 / 明天会更好 / 你今天不错。
 * 鼓励必须落在当天具体的那一笔上（几点、几分、几分钟、那件小事），否则就是空话。
 */

data class Verdict(val main: String, val extra: String)

// A · 今天一次都没记
private val POOL_A = listOf(
    "今天这儿还是空的。空白不等于这天白过。",
    "今天什么都没记。没关系——不想记的时候，不记也是一种照顾自己。",
    "今天没有留下任何一笔。这一栏不会催你。",
    "空着的一天也是一天。它不需要被证明过得好不好。",
    "今天这里是空的。等你想记的时候再来，它一直在。"
)

// B · 只记了一次
private val POOL_B = listOf(
    "只记了一次。一次撑不起「今天怎么样」，但那是你愿意留下的那一下。",
    "今天只有一笔。它说明不了全天，但它说明那时候你还想把它记下来。",
    "记了一次。一次就够了——不是为了统计，是为了那一下有地方放。",
    "今天只留下一次。别的时刻没进来，也不用补。",
    "只有一笔的一天。它不是「没记够」，它就是今天。"
)

// C · 记了很多次（≥5）
private val POOL_C = listOf(
    "今天记了 {n} 次。记得多不是退步——说明它今天一直没走开，而你一直在接。",
    "{n} 次。每一次都是你把手伸向这里，而不是伸向那个对话框。",
    "今天记了 {n} 次，最慌到 {peak} 分。频繁不是失控，是你在一直跟它打交道。",
    "一天 {n} 笔。这些不是「又来了」，是「又撑过去了一次」，记了 {n} 回。",
    "记了 {n} 次的一天。它没少来找你，你也没少把它记下来。"
)

// D · 峰值 ≥8，后来落回来了
private val POOL_D = listOf(
    "最慌的那次在 {time}，到 {peak} 分，{min} 分钟后你站了回来。这不是「好转」，这是你今天做到的一件事。",
    "{time} 那一次慌到 {peak} 分。后来它下来了——不是它自己走的，是你熬过去的。",
    "今天最难受的是 {time}，{peak} 分；最后那次只剩 {last} 分。中间那 {min} 分钟，是你自己走出来的。",
    "慌到 {peak} 分，然后回来了。今天记了 {n} 次，你一次都没让它留在最高处。",
    "{time} 的 {peak} 分没有留在原地。今天结束在 {last} 分——这是你做到的，不是运气。"
)

// E · 峰值 ≥8，最后那次还高
private val POOL_E = listOf(
    "记到 {last_time} 那一次还是 {last} 分。今天没有变得更好——把它记下来就够了，明天再说明天的。",
    "最慌到 {peak} 分，最后还是 {last} 分。今天它没走。你把它写下来了，这是今天能做的事里最实在的一件。",
    "今天结束在 {last} 分。不是每一次难受都当天散掉，这次就是没散。记下来，让它有地方待着。",
    "{peak} 分的那一下还没过去。这不是你的问题——有些东西本来就需要不止一天。",
    "记到 {last_time} 还是 {last} 分。今天你没打赢它，但你也没假装它不在。"
)

// F · 峰值 5–7
private val POOL_F = listOf(
    "今天一直在 {min} 到 {max} 之间晃，没掉下去，也没上来。这种日子最难熬，因为它没有形状。",
    "最高 {peak} 分，一直没过 8。不是没事，是它今天没那么凶。",
    "今天恐慌在中间那一段来回。不难受是假的，撑住了是真的。",
    "没到顶，也没到底。今天就是这样的一天，不需要被解释成别的什么。",
    "晃动了一整天。晃着晃着，一天也过完了。"
)

// G · 全天峰值 ≤4
private val POOL_G = listOf(
    "恐慌一整天没过 {peak} 分。这不是「没事了」，是今天它没来找你。",
    "今天最高 {peak} 分。可以松一口气，但不用宣布什么——明天的事明天再说。",
    "一整天都挺平的。这不代表过去了，只代表今天没被它抓住。",
    "今天它没怎么出现。你可以把这一天留着，难的时候翻回来看一眼。",
    "最高 {peak} 分的一天。别急着给它下结论，让它就这么过去。"
)

// 记了几笔但恐慌一栏都没答：不替它填一个数，也不假装看得出今天怎么样
private val POOL_UNANSWERED = listOf(
    "今天记了 {n} 次，恐慌那栏一次都没答。没答就是没答 —— 这里不会替你填一个数。",
    "记了 {n} 次，恐慌都空着。空着也是一种记法，它说明那时候你不想给那个数字。"
)

// H · 附加：当天写了「愿意为自己做什么」
private val POOL_H = listOf(
    "而且你已经替明天留了一件小事：{doNext}。",
    "你还写下了明天要做的一件小事：{doNext}。",
    "那件小事你已经想好了：{doNext}。",
    "明天有一件事在等你：{doNext}。",
    "你给自己留了一句往前走的话：{doNext}。"
)

// I · 附加：当天有充到电的事（没有 H 时才用）
private val POOL_I = listOf(
    "另外，今天有 {up} 件是充到电的。",
    "还记下 {up} 件是给你充电的——那天不全是消耗。",
    "有 {up} 件事你记成了「补」。不是全部都在往下走。",
    "今天有 {up} 件让自己好一点的事，别把它们和难受的那几笔混在一起算。",
    "还留下 {up} 件充到电的事。"
)

fun buildVerdict(
    day: LocalDate,
    records: List<MoodRecord>,
    events: List<LifeEvent>,
    encounters: List<Encounter>
): Verdict {
    val seed = day.toEpochDay()
    val n = records.size
    val answered = records.map { it.panic }.filter { it >= 0 }
    val peak = answered.maxOrNull()
    val low = answered.minOrNull()
    val peakRecord = if (peak == null) null else records.lastOrNull { it.panic == peak }
    val lastRecord = records.lastOrNull { it.panic >= 0 }
    val last = lastRecord?.panic
    val gapMin = if (peakRecord != null && lastRecord != null) {
        ((lastRecord.timestamp - peakRecord.timestamp) / 60000L).toInt().coerceAtLeast(1)
    } else 1

    val base = mutableMapOf<String, String>(
        "n" to n.toString(),
        "time" to (peakRecord?.timestamp?.logicalTimeString() ?: ""),
        "peak" to (peak?.toString() ?: ""),
        "last" to (last?.toString() ?: ""),
        "last_time" to (lastRecord?.timestamp?.logicalTimeString() ?: ""),
        "min" to gapMin.toString(),
        "max" to (peak?.toString() ?: ""),
        "low" to (low?.toString() ?: "")
    )

    val (pool, salt) = when {
        n == 0 -> POOL_A to 1
        n == 1 -> POOL_B to 2
        peak == null -> POOL_UNANSWERED to 3
        n >= 5 -> POOL_C to 4
        peak >= 8 && last != null && peak - last >= 3 -> POOL_D to 5
        peak >= 8 -> POOL_E to 6
        peak >= 5 -> {
            base["min"] = low?.toString() ?: peak.toString()
            POOL_F to 7
        }
        else -> POOL_G to 8
    }

    val main = fill(pool[pick(seed, salt, pool.size)], base)

    // 附加句最多一句：H（给明天留了件小事）优先于 I（有充到电的事）。
    val doNext = encounters.map { it.doNext.trim() }.lastOrNull { it.isNotBlank() }
    val up = events.count { it.delta > 0 }
    val extra = when {
        !doNext.isNullOrBlank() ->
            fill(POOL_H[pick(seed, 11, POOL_H.size)], base + ("doNext" to doNext))
        up > 0 ->
            fill(POOL_I[pick(seed, 13, POOL_I.size)], base + ("up" to up.toString()))
        else -> ""
    }

    return Verdict(main = main, extra = extra)
}

/**
 * 卡片下面那一行事实。没有的项不留空槽 —— 摆一排「0 件」看着像在数你缺了什么。
 */
fun verdictFacts(
    records: List<MoodRecord>,
    events: List<LifeEvent>,
    encounters: List<Encounter>,
    dims: LifeDims?
): String {
    val parts = mutableListOf<String>()
    if (records.isNotEmpty()) parts.add("情绪 ${records.size} 次")
    val peak = records.map { it.panic }.filter { it >= 0 }.maxOrNull()
    if (peak != null) parts.add("峰值 $peak")
    if (events.isNotEmpty()) parts.add("我话事 ${events.size} 件")
    if (encounters.isNotEmpty()) parts.add("我遇见 ${encounters.size} 笔")
    if (dims != null) {
        val avg = LIFE_DIMS.map { it.get(dims) }.average()
        parts.add("六维 ${String.format("%.1f", avg)}")
    }
    records.lastOrNull()?.let { parts.add("记到 ${it.timestamp.logicalTimeString()}") }
    return parts.joinToString(" · ")
}

private fun pick(seed: Long, salt: Int, size: Int): Int {
    fun idxFor(s: Long): Int {
        val v = (s * 31L + salt) % size
        return (if (v < 0) v + size else v).toInt()
    }
    val today = idxFor(seed)
    val yesterday = idxFor(seed - 1)
    // 撞上昨天那一句就顺移一位：连着两天读到一模一样的话，会变成背书。
    return if (size > 1 && today == yesterday) (today + 1) % size else today
}

private fun fill(text: String, vars: Map<String, String>): String {
    var out = text
    vars.forEach { (k, v) -> out = out.replace("{$k}", v) }
    return out
}
