package com.example.moodlog.data

/**
 * 一次心情记录（A 空间 · 情绪）。
 * miss / contact / confirm / panic 是四个主维度（0-10），录入顺序 likewise。
 * emotions 是恐慌 8 分以上时的六支情绪分支，未填写时为 null。
 * customEmotions 是你自己加的情绪（名字 → 0-10），同样是那只在这一条里。
 * attachments 是日记的附件文件路径（图片 / 视频 / 语音），多条用换行分隔。
 *
 * impulse  0=现在没有 1=有想法 2=很强烈，null 表示没问到
 * outcome  0=还没回答后来怎么样了 1=勉强行 2=还可以 3=要求助
 * episodeAt 这一波恐慌第一次上到 8 分的时刻
 * outcomeAt 按下「后来怎么样了」的时刻；它与 episodeAt 的差即「从慌起来到答出这一天」
 * safeAt   只有 outcome=3 时才有：在安全底座按下「我安全了」的时刻
 */
data class MoodRecord(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val miss: Int = 0,
    val panic: Int = 0,
    val contact: Int = 0,
    val confirm: Int = 0,
    val diary: String = "",
    val emotions: EmotionSet? = null,
    val customEmotions: List<Pair<String, Int>> = emptyList(),
    val attachments: List<String> = emptyList(),
    val impulse: Int? = null,
    val outcome: Int = 0,
    val episodeAt: Long? = null,
    val outcomeAt: Long? = null,
    val safeAt: Long? = null
) {
    /** 从慌起来到答出「后来怎么样了」用了多少分钟。也可以事后在全部记录里补。 */
    val toOutcomeMinutes: Long?
        get() {
            val start = episodeAt ?: return null
            val end = outcomeAt ?: return null
            if (end < start) return null
            return (end - start) / 60000
        }

    val toSafeMinutes: Long?
        get() {
            val out = outcomeAt ?: return null
            val safe = safeAt ?: return null
            if (safe < out) return null
            return (safe - out) / 60000
        }
}

/**
 * 未答。
 *
 * 四个主维度允许「没答」。它和 0 分是两件事：0 分是很重的一个答案（我一点都不想 / 我一点都不慌），
 * 未答是「我还没问自己」。界面上未答是一条短横，不是 0；统计上未答不参与平均，不是拉低数值的 0。
 *
 * 用 -1 记在库里，不加新列：老数据一行不用动。
 */
const val UNANSWERED = -1

fun Int.answered(): Boolean = this >= 0

/** 界面上怎么显示这个数：未答是「—」。 */
fun Int.answerText(): String = if (this >= 0) toString() else "—"

/**
 * 恐慌高企时的六支情绪（0-10）：被抛弃感 / 愤怒 / 羞耻 / 恐惧 / 孤独 / 不甘。
 * 「想念」已移除——它与主维度的 miss 是同一件事，不重复采集。
 * 这一组不进入总览，只在单条记录里呈现，以及总览里手动切换后查看。
 */
/**
 * 六支情绪，每支只有两种状态：<b>有等级（0–10）</b>，或者<b>未答（null）</b>。
 *
 * 以前没动过的那支存成 0，于是「这次愤怒是 0 分」和「愤怒这支我压根没碰」在库里一模一样，
 * 结果全部记录里凭空多出五行 0 分 —— 那是替人说了他没说过的话。
 * 现在不动就不写：null = 这一支当时没问到自己。
 *
 * 注意 <b>0 是一个很实在的答案</b>（「这次一点都不愤怒」），它不是空白，能存进去。
 */
data class EmotionSet(
    val abandoned: Int? = null,
    val anger: Int? = null,
    val shame: Int? = null,
    val fear: Int? = null,
    val loneliness: Int? = null,
    val resentment: Int? = null
) {
    /** 六支一支都没填 = 这一组等于没记。 */
    val isEmpty: Boolean
        get() = listOf(abandoned, anger, shame, fear, loneliness, resentment).all { it == null }
}

/** 顺序与 ui/theme/Color.kt 的 EmotionColors 必须一致。取出来可能是 null（那一支没答）。 */
val EMOTION_FIELDS: List<Pair<String, (EmotionSet) -> Int?>> = listOf(
    "被抛弃感" to { it.abandoned },
    "愤怒" to { it.anger },
    "羞耻" to { it.shame },
    "恐惧" to { it.fear },
    "孤独" to { it.loneliness },
    "不甘" to { it.resentment }
)

/** 自己加的情绪用什么颜色：轮着取，不引入新色系。 */
val CUSTOM_EMOTION_COLORS: List<String> = EMOTION_FIELDS.map { it.first }

/** 冲动等级的三个档位，只存整数，绝不存文字内容。 */
val IMPULSE_LABELS = listOf("现在没有", "有想法", "很强烈")

/**
 * 「后来怎么样了」的三个答案。
 *
 * 这三个不挡在保存前面——记录先存下来，它们问的是过一会儿的你。
 * 词汇刻意不用「我现在还好」：恐慌 8 分以上的人和这句话是矛盾的，谁都会选它，于是这一栏全废。
 */
val OUTCOME_LABELS = mapOf(
    1 to "勉强行",
    2 to "还可以",
    3 to "要求助"
)

val OUTCOME_ORDER = listOf(2, 1, 3) // 还可以 / 勉强行 / 要求助 —— 屏幕自上而下的顺序

/** 「我说出口了吗」——三种都正当，不作评价、不计分。 */
val SPOKEN_LABELS = listOf("说出去了", "先放在这里", "没说")
