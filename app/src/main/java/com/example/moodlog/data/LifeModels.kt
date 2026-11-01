package com.example.moodlog.data

/**
 * B 空间 · 自我与生活的数据模型。
 * 三个入口：做了什么（LifeEvent）、今天的六个问题（LifeDims）、我做得了主的事（Stance）。
 */

/** 做过的某件事。能量前后差是核心：做了 ≠ 有效，差值才说明它是不是真的在给你充电。 */
data class LifeEvent(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList(),
    // 量轴从 0 起：之前是 0，之后是这件事自己的正负。差值 = 它给你充了多少 / 耗了多少。
    val energyBefore: Int = 0,
    val energyAfter: Int = 0,
    val note: String = ""
) {
    val delta: Int get() = energyAfter - energyBefore
}

/**
 * 一次「我遇见」：四句话，一次情绪上来时的完整走一遍。
 *
 * 它和【情绪】那四个刻度不一样：刻度是「几分」，这里是「你自己的话」。
 * 分开记是因为它们回答的不是同一个问题 —— 刻度记录强度，这四句记录发生了什么、然后怎么走。
 *
 * 四问的顺序是有讲究的：先说事情，再说感受，然后才问需要什么、愿意做什么。
 * 人在最难受的时候答不了「我要怎么办」，但答得出「刚才看到了一张旧照片」。
 */
data class Encounter(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val happened: String = "",   // 1. 刚才发生了什么
    val feeling: String = "",    // 2. 我现在正在经历什么
    val need: String = "",       // 3. 此刻的我需要什么
    val doNext: String = ""      // 4. 接下来，我愿意为自己做什么
) {
    /** 这一笔有没有内容。判空用四个字段一起看 —— 只答第四问也算一笔。 */
    val isBlank: Boolean
        get() = happened.isBlank() && feeling.isBlank() &&
            need.isBlank() && doNext.isBlank()

    /** 列表里那一句话：拿第三问（需要什么）兜不住就退回第二问（正在经历什么）。 */
    fun oneLine(): String = when {
        feeling.isNotBlank() -> feeling.trim()
        happened.isNotBlank() -> happened.trim()
        need.isNotBlank() -> need.trim()
        else -> doNext.trim()
    }
}

/** 预设的活动标签。用户可以自己加，加过的会记住。 */
val PRESET_TAGS = listOf("运动", "社交", "求职", "创作", "照顾宠物", "独处修复")

/**
 * 一顿饭（或一次「没吃」）。
 *
 * 记的是内容和时刻，不是热量、不是营养、不是"今天够不够"。
 * 「没吃」和别的标签一样是中性的一项 —— 难熬的日子里吃不下是事实，不是错误，
 * 所以它是灰的，不是红的，也不参与任何"你今天少了一顿"的统计。
 *
 * medicine 跟着每一餐，不是只挂在早上：药是按时吃的，饭不一定吃得下，
 * 这两件事在这一格上是分开的两件事。没勾就不显示任何东西（不写"未服药"）。
 */
data class Meal(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val kind: String = MEAL_KINDS[0],
    val content: String = "",
    val medicine: Boolean = false
)

/** 六个标签。顺序是「一天下来的顺序」，加餐排在夜宵后面是因为它俩常被当成同一件事。 */
val MEAL_KINDS = listOf("早饭", "午餐", "晚餐", "夜宵", "加餐", "没吃")

/** 一次称重。同一天可以记两次，不覆盖 —— 早晚各一次本来就是两件事。 */
data class Weight(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val kg: Double = 0.0
)

/**
 * 一次运动：【我身体 › 运动】。
 *
 * 只记两件事：<b>动了什么</b>（类型，单选）和<b>多久</b>（分钟）。
 * 不记热量、步数、配速、距离、组数、强度 —— 一旦开始算成效，这一格就变成结算页了。
 *
 * minutes = 0 表示「没记时长」：有些日子只记得去打了球，不记得打了多久，那也作数，
 * 列表里显示「—」。它是一个空白，不是 0 分钟。
 */
data class Move(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String = "",
    val minutes: Int = 0,
    val note: String = ""
)

/**
 * 预置的运动类型。全是日常的说法，不分好坏 —— 遛弯不比健身低一等。
 * 用户可以自己加，加过的会记住、常用的往前排。
 */
val MOVE_PRESETS = listOf(
    "羽毛球", "足球", "篮球", "乒乓球", "网球", "跑步",
    "快走", "徒步", "骑行", "游泳", "健身", "瑜伽拉伸",
    "跳绳", "爬楼梯", "遛狗", "跳舞", "攀岩", "遛弯"
)

/** 今天的六个维度（0-10）。每个都配一句问句——抽象滑块不知道给几分，问句是可回答的。 */
data class LifeDims(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val agency: Int = 0,      // 掌控感
    val achievement: Int = 0, // 成就感
    val vitality: Int = 0,    // 生命力
    val connection: Int = 0,  // 连接感
    val value: Int = 0,       // 价值感
    val joy: Int = 0          // 愉悦感
)

data class LifeDimDef(
    val key: String,
    val name: String,
    val question: String,
    val hints: List<String> = emptyList(),
    val get: (LifeDims) -> Int
)

/** 顺序与 ui/theme/Color.kt 的 LifeDimColors 必须一致。 */
val LIFE_DIMS: List<LifeDimDef> = listOf(
    LifeDimDef("agency", "掌控感", "今天有什么事情，是我自己决定的？") { it.agency },
    LifeDimDef("achievement", "成就感", "今天有什么事情证明“我做得到”？") { it.achievement },
    LifeDimDef("vitality", "生命力", "我活着，而且我的身体和世界还有联系。") { it.vitality },
    LifeDimDef("connection", "连接感", "今天我和这个世界产生了什么连接？") { it.connection },
    LifeDimDef(
        "value", "价值感", "今天我的行为有没有符合我认为重要的东西？",
        hints = listOf("善良", "勇敢", "正直", "责任", "边界", "创造")
    ) { it.value },
    LifeDimDef("joy", "愉悦感", "今天有没有什么东西让我单纯觉得“爽”？") { it.joy }
)

/**
 * 一条打卡。
 *
 * 它原来叫「我做得了主的事」，问的六句话都太重，人要现编一次判断才能勾一个，于是它一直是空的。
 * 现在它只问最小的一件事：<b>不管今天怎样，你做了什么</b> —— 我吃饭了、我干活了、我睡觉了。
 *
 * 只记「我做了」的。记「我没做到」没有意义：那天已经够难了，不该再多一张缺勤表。
 *
 * keys 里放标签本身；老数据是 s1…s6 这样的 key，靠 STANCE_ITEMS 翻成中文，记录一条都不丢。
 * ts 是这一条的时刻：按次记，一天可以有很多条，时间线靠它排。
 */
data class Stance(
    val id: Long = 0,
    val date: String = "",          // yyyy-MM-dd
    val keys: List<String> = emptyList(),
    val custom: String = "",
    val note: String = "",          // 「尽管…」——这条打卡真正要留下来的那句话
    val spoken: Int? = null,        // 0=说出去了 1=先放在这里 2=没说（新版本不再问，老数据还在）
    val ts: Long = 0                // 毫秒；0 = 这一条没有时刻
)

/** 把 s1…s6 翻成中文；新的记录存的就是标签本身，原样返回。 */
fun Stance.labels(): List<String> =
    (keys.map { k -> STANCE_ITEMS.firstOrNull { it.first == k }?.second ?: k } +
        listOfNotNull(custom.takeIf { it.isNotBlank() }))
        .map { STANCE_RENAME[it] ?: it }

/** 老措辞 → 新措辞。同一件事，换成正向的说法。 */
private val STANCE_RENAME = mapOf(
    "没有去翻TA的聊天记录或动态" to "我忍住了，没去看TA的动态"
)

val STANCE_ITEMS: List<Pair<String, String>> = listOf(
    "s1" to "完成了一件自己说要做的事",
    "s2" to "照顾了自己的身体：吃了饭 / 睡了 / 动了",
    "s3" to "表达了一个真实的想法或不同意",
    "s4" to "拒绝了一件我不想做的事",
    "s5" to "最难的时候，先处理身体再行动",
    "s6" to "我忍住了，没去看TA的动态"
)

/**
 * 打卡页先铺出来的那几个。全是日常、全是正向 —— 难熬的日子里，
 * 「我吃饭了」也是一件值得看见的事。
 *
 * 用户在这一 bloc 里删掉任何一个，都只是记账本里划掉一行，不影响已记的。
 */
val CHECKIN_PRESETS = listOf(
    "我起床了", "我吃饭了", "我睡觉了", "我干活了", "我出门了",
    "我洗了澡", "我动了动", "我和人说了话", "我吃了药",
    "我忍住了，没去看TA的动态"
)

/** 备注框里的一句话提示。它不是装饰 —— 「尽管…」这三个字就是这个板块要记的东西。 */
val CHECKIN_NOTE_HINTS = listOf("尽管今天很糟", "尽管我一点都不想", "尽管没人知道", "尽管我还是想TA")

/** 用户自己加的活动标签，按用过的次数排序。 */
data class CustomTag(val name: String = "", val usedCount: Int = 0)

/** 安全底座的紧急联系人，最多两个。号码由用户自己填，不预置任何热线。 */
data class Contact(val slot: Int = 1, val name: String = "", val phone: String = "")
