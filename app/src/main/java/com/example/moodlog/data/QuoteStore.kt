package com.example.moodlog.data

import android.content.Context
import com.example.moodlog.util.DAY_START_HOUR
import com.example.moodlog.util.logicalToday

/**
 * 【我】页最上面那句，以及总结页顶上那句。
 *
 * 写这些句子有一条死规矩：只陈述事实，不打分，不鼓劲。
 * 不写「加油」、不写「你可以的」、不写「明天会更好」、不许诺任何未来 ——
 * 那些话在最难的时候听起来是反话。它只能说一句你已经知道、但这一天忘了的事。
 *
 * 一天一句太少：凌晨三点看到的那句，应该和傍晚六点看到的那句不一样。
 * 所以按一天四段分开备句子，每 6 小时换一次 —— 换句的时刻和「一天从六点开始」是同一套时间：
 * 06:00 早上 · 12:00 白天 · 18:00 傍晚 · 24:00 深夜。
 *
 * 句子全部打包在应用里，不联网，永远看得到。你自己加的那几句会一起轮。
 */
object QuoteStore {

    private const val FILE = "moodlog_quotes"
    private const val KEY_MINE = "mine"

    private const val HOUR_MS = 60 * 60 * 1000L
    private const val SLOT_MS = 6 * HOUR_MS

    /** 早上：06:00–12:00。昨晚过去了，今天还不用证明什么。 */
    private val DAWN = listOf(
        "天亮了。昨晚那几个小时也算这一天的一部分。",
        "你熬过来的这个夜晚，没有人替你熬。",
        "先喝一口水，再决定今天怎么过。",
        "不用马上振作。先让身体醒过来。",
        "早晨不需要结论。它只需要你起来。",
        "昨晚很重，但昨晚已经过去了。",
        "今天先只做一件小事就够。",
        "你不必今天就原谅任何人，包括你自己。",
        "太阳出来不代表要开心。它只是出来了。",
        "醒来本身就是一次继续。",
        "今天不用证明什么。你在，就够了。"
    )

    /** 白天：12:00–18:00。手头有事的时候，念头会小一点。 */
    private val DAY = listOf(
        "现在这个时刻，你不需要知道TA在哪里。",
        "手头这件事做完，就算今天的证据。",
        "想联系TA不是软弱。要不要联系，是现在能由你决定的事。",
        "脑子里的对话不是事实。它只是很吵。",
        "中午的太阳不会问你过得好不好。它照着。",
        "做一点能让身体动起来的事，比想明白更要紧。",
        "你可以一边做事一边难受，这两件事不冲突。",
        "有人回复不代表你被爱，没人回复也不代表你被丢下。",
        "今天记下来的这一笔，是给以后的你留的证据。",
        "现在不需要答案，只需要下一分钟。",
        "今天最难的那几个小时已经过去了 —— 或者还没来。都行。"
    )

    /** 傍晚：18:00–24:00。一天里最容易塌的时候。 */
    private val DUSK = listOf(
        "傍晚是一天里最容易塌的时候。塌了也不奇怪。",
        "天黑下来的时候，念头会变大声。那是生理，不是你变弱了。",
        "想发消息就先记在这里。记完再看还想不想发。",
        "你不需要今天过得有意义。过完就算。",
        "别人都在回家的路上，而你回的是一间空屋子 —— 这确实很难。",
        "今天的难受有名字，是有名有姓的那种难。",
        "先处理身体，再处理情绪。顺序反了会白费力气。",
        "你可以现在就躺下，什么都不做，这也是一种安排。",
        "没有联系TA，不代表你不想TA。只是今天你选了别的。",
        "晚上会过去。它每次都过去了。",
        "把灯打开。屋子亮一点，念头就小一点。"
    )

    /** 深夜：24:00–06:00。最难熬的那几个小时在这里。 */
    private val NIGHT = listOf(
        "这个点还醒着，不是你的错。",
        "深夜会把白天压住的东西全部放出来。给它一个出口：写下来。",
        "别在现在做任何决定。它们会在早上变成另一个样子。",
        "手机放下，手是空的，但你是完整的。",
        "睡不着也没关系。躺着也算在休息。",
        "现在这一刻很难熬。它不会是永远，虽然现在很像。",
        "想TA想得受不了的时候，就记一笔。写下来比发出去安全。",
        "你已经熬过了很多个这样的晚上。这一个也不会例外。",
        "如果今晚只能做一件事：呼吸慢一点，再慢一点。",
        "现在不用想明天。明天有明天的你。",
        "这不是退步。这是在很深的夜里，你还在。"
    )

    /** 顺序和 slot 的小余数一致：0 天亮 / 1 白天 / 2 傍晚 / 3 深夜。 */
    private val POOLS = listOf(DAWN, DAY, DUSK, NIGHT)
    private val POOL_NAMES = listOf("天亮", "白天", "傍晚", "深夜")

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun mine(context: Context): List<String> =
        prefs(context).getString(KEY_MINE, "")!!.lineSequence()
            .map { it.trim() }.filter { it.isNotBlank() }.toList()

    fun add(context: Context, text: String) {
        val line = text.trim().replace("\n", " ").replace("\r", " ")
        if (line.isBlank()) return
        val all = (mine(context) + line).distinct()
        prefs(context).edit().putString(KEY_MINE, all.joinToString("\n")).apply()
    }

    fun remove(context: Context, text: String) {
        val all = mine(context).filter { it != text }
        prefs(context).edit().putString(KEY_MINE, all.joinToString("\n")).apply()
    }

    /**
     * 现在是第几个六小时。以逻辑日的起点（早上六点）为原点切段，
     * 所以换句的时刻正好是 06:00 / 12:00 / 18:00 / 24:00。
     */
    private fun slot(now: Long): Long {
        val shifted = now - DAY_START_HOUR * HOUR_MS
        return Math.floorDiv(shifted, SLOT_MS)
    }

    private fun poolIndex(now: Long): Int {
        val r = (slot(now) % 4).toInt()
        return if (r < 0) r + 4 else r
    }

    /** 这一段的句子池，你自己加的那几句并进每一个池，保证轮得到。 */
    private fun pool(context: Context, now: Long): List<String> =
        POOLS[poolIndex(now)] + mine(context)

    /** 现在这一句。同一个时段反复打开都是同一句，到下一段才换。 */
    fun today(context: Context, now: Long = System.currentTimeMillis()): String {
        val all = pool(context, now)
        val day = logicalToday().toEpochDay()
        val idx = Math.floorMod(day, all.size.toLong()).toInt()
        return all[idx]
    }

    /** 这一句属于哪一段，卡角上那行小字。 */
    fun slotLabel(now: Long = System.currentTimeMillis()): String {
        val i = poolIndex(now)
        val hour = (i * 6 + DAY_START_HOUR) % 24
        return "${String.format("%02d", hour)}:00 换的 · ${POOL_NAMES[i]}"
    }

    /** 距下一次换句还有多久。只是告诉你它会换，不是催你来看。 */
    fun nextChangeText(now: Long = System.currentTimeMillis()): String {
        val remain = (slot(now) + 1) * SLOT_MS + DAY_START_HOUR * HOUR_MS - now
        val minutes = (remain / 60000).coerceAtLeast(0)
        return when {
            minutes < 60 -> "再过 $minutes 分钟换下一句"
            else -> "再过 ${minutes / 60} 小时 ${minutes % 60} 分换下一句"
        }
    }
}
