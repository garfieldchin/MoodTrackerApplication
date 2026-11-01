package com.example.moodlog.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moodlog.ui.theme.*
import com.example.moodlog.data.DaySummary
import com.example.moodlog.data.LIFE_DIMS
import com.example.moodlog.data.LifeDims
import com.example.moodlog.data.MoodRepository
import com.example.moodlog.data.buildDaySummary
import com.example.moodlog.data.buildVerdict
import com.example.moodlog.data.verdictFacts
import com.example.moodlog.ui.theme.LifeDimColors
import com.example.moodlog.ui.theme.PrimaryPurple
import com.example.moodlog.ui.theme.TextGray
import com.example.moodlog.util.DATE_CN_FMT
import com.example.moodlog.util.MD_FMT
import com.example.moodlog.util.logicalDate
import com.example.moodlog.util.logicalTimeString
import com.example.moodlog.util.logicalToday
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

private val RANGE_TABS = listOf("本周", "上周", "本月")
private val BECOME_TABS = listOf("我的感受", "我的今天")

/**
 * 屏 5 · 成为。两格，各管一件事，不再混在一起。
 *
 *   我的感受 = 那六个问题（六维）。一天收一次尾的东西：
 *             今天直接可答，翻到以前的日子默认只读 —— 那天已经过去了，看见它比改它对更重要。
 *   我的今天 = 这一天的一句话，以及这段时间。
 *             那句话只由当天真实存在的记录拼出来（见 data/DaySummary.kt）：
 *             【我话事】空着的时候，它不会说「你完成了自己决定要做的事」。
 *
 * 情绪不在这里汇总 —— 它有自己的趋势页，混进来反而把两边的边界弄模糊。
 * 这一页不评分、不算完成度，也不替你宣布今天好不好。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BecomeScreen(
    repo: MoodRepository,
    padding: PaddingValues,
    onOpenSummary: (LocalDate) -> Unit,
    /** 六维正在看哪一天（null = 今天）。从二级页跳过来时用。 */
    viewDay: LocalDate? = null,
    onViewDay: (LocalDate?) -> Unit = {},
    onOpenAll: () -> Unit = {}
) {
    val pager = rememberPagerState(pageCount = { BECOME_TABS.size })
    val scope = rememberCoroutineScope()
    var showCalendar by remember { mutableStateOf(false) }

    // 从二级页跳过来要看某一天的六维：先把这一页翻到【我的感受】那一格。
    LaunchedEffect(viewDay) {
        if (viewDay != null && pager.currentPage != 0) pager.animateScrollToPage(0)
    }

    val today = logicalToday()
    val peakByDay = panicPeakByDay(repo.records)
    val todayRecords = repo.records.filter { it.timestamp.logicalDate() == today }
        .sortedBy { it.timestamp }
    val todayEvents = repo.events.filter { it.timestamp.logicalDate() == today }
    val todayEncounters = repo.encounters.filter { it.timestamp.logicalDate() == today }
    val todayDims = repo.dimsOf(today)
    // 身体那一栏当天有没有动过（吃 / 体重 / 运动）—— 只有真的记过，才说得出「照顾了自己的生活」。
    val bodyCount = repo.meals.count { it.timestamp.logicalDate() == today } +
        repo.weights.count { it.timestamp.logicalDate() == today } +
        repo.moves.count { it.timestamp.logicalDate() == today }
    val todaySummary = remember(
        today, todayRecords.size, todayEvents.size, todayEncounters.size, todayDims?.id, bodyCount
    ) {
        buildDaySummary(today, todayRecords, todayEvents, todayEncounters, todayDims, bodyCount)
    }
    // 同一天永远是同一句（日期做种子），换一天才换。
    val todayVerdict = remember(today, todayRecords.size, todayEvents.size, todayEncounters.size) {
        buildVerdict(today, todayRecords, todayEvents, todayEncounters)
    }
    val todayFacts = remember(today, todayRecords.size, todayEvents.size, todayEncounters.size) {
        verdictFacts(todayRecords, todayEvents, todayEncounters, todayDims)
    }

    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SegmentedBar(
                items = BECOME_TABS,
                selected = pager.currentPage,
                onSelect = { scope.launch { pager.animateScrollToPage(it) } }
            )
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                if (page == 0) {
                    FeelPane(
                        repo = repo,
                        viewDay = viewDay,
                        onViewDay = onViewDay,
                        onOpenAll = onOpenAll
                    )
                } else {
                    TodayPane(
                        repo = repo,
                        verdictMain = todayVerdict.main,
                        verdictExtra = todayVerdict.extra,
                        facts = todayFacts,
                        summary = todaySummary,
                        today = today,
                        onOpenSummary = onOpenSummary,
                        onOpenCalendar = { showCalendar = true }
                    )
                }
            }
        }

        if (showCalendar) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x4A000000))
                    .tap { showCalendar = false }
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                DayCalendarSheet(
                    marked = { day -> peakByDay[day] },
                    selected = today,
                    onPick = { day ->
                        showCalendar = false
                        onOpenSummary(day)
                    },
                    onClose = { showCalendar = false }
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 我的感受

/**
 * 那六个问题。一天收一次尾的地方。
 *
 * 它原本在【生活】里占一格，现在归到这里 —— 它问的是「我怎么样」，
 * 和「我做了什么」放在一起时，人总会下意识用后者去解释前者，那六根尺子就不准了。
 */
@Composable
private fun FeelPane(
    repo: MoodRepository,
    viewDay: LocalDate?,
    onViewDay: (LocalDate?) -> Unit,
    onOpenAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            "一天收一次尾的六个问题。今天可以直接答；" +
                "以前的日子从下面的【全部记录】进去，在那一天里按「这天的六维」。",
            style = MaterialTheme.typography.bodySmall,
            color = TextGray
        )
        Spacer(modifier = Modifier.height(10.dp))
        DimsPane(
            repo = repo,
            viewDay = viewDay,
            onViewDay = onViewDay,
            onOpenAll = onOpenAll
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- 我的今天

/**
 * 这一天：上面是「今天的总结」那句判断，中间是当天摆出来的事实，下面是这段时间。
 *
 * 和【我的感受】分开的理由很简单 —— 一句是判断，一组是分数，
 * 挤在一屏里，看的人会把那六个分数当成那句话的注解，而它们不是。
 */
@Composable
private fun TodayPane(
    repo: MoodRepository,
    verdictMain: String,
    verdictExtra: String,
    facts: String,
    summary: DaySummary,
    today: LocalDate,
    onOpenSummary: (LocalDate) -> Unit,
    onOpenCalendar: () -> Unit
) {
    var rangeIdx by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            color = ChipBg,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .tap { onOpenSummary(today) }
                    .padding(horizontal = 15.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "今天的总结",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SubText,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${today.format(DATE_CN_FMT)} ›",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryPurple,
                        modifier = Modifier.tap { onOpenCalendar() }
                    )
                }
                Spacer(modifier = Modifier.height(9.dp))
                // 那一句判断。不是数据复述，是下判断 —— 但它必须落在今天具体的一笔上
                // （几点、几分、中间那几分钟），否则就是空话。
                Text(
                    verdictMain,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 21.sp,
                    color = SubText
                )
                if (verdictExtra.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        verdictExtra,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                        color = SuccessGreen
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    facts,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        // 当天这一句。它只说当天真的有的东西 —— 没记的那一项不会出现在话里。
        TodayCard(summary = summary)
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            "这段时间",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RANGE_TABS.forEachIndexed { index, text ->
                FilterChip(
                    selected = rangeIdx == index,
                    onClick = { Haptics.tap(); rangeIdx = index },
                    label = { Text(text) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryPurple.copy(alpha = 0.16f),
                        selectedLabelColor = PrimaryPurple
                    ),
                    border = null
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        RangeBody(repo = repo, rangeIdx = rangeIdx)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 当天这一段话。
 *
 * 它和上面那张「今天的总结」不是一回事：那张是下判断，这一段只是把今天摆出来 ——
 * 有过什么就说有什么，没有的就不提，末尾那行状态也照实写「没有记录」。
 */
@Composable
private fun TodayCard(summary: DaySummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "今天",
                style = MaterialTheme.typography.labelSmall,
                color = TextGray
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                summary.headline,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 21.sp
            )
            summary.lines.forEach { line ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    color = SubText
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                summary.facts,
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                lineHeight = 18.sp
            )
        }
    }
}

// ---------------------------------------------------------------- 这段时间

@Composable
private fun RangeBody(repo: MoodRepository, rangeIdx: Int) {
    val today = logicalToday()
    val (start, end, label) = rangeOf(rangeIdx, today)

    val events = repo.events.filter { it.timestamp.logicalDate() in start..end }
    val encounters = repo.encounters.filter { it.timestamp.logicalDate() in start..end }

    // 不把点数累加起来。加了三天 +3 再减一天 −5，合计 −2 说明不了任何一件事 ——
    // 那个数字既不是「今天怎么样」，也不是「你做了多少」。只数件数：充电几件、耗电几件。
    val chargeUp = events.count { it.delta > 0 }
    val chargeDown = events.count { it.delta < 0 }
    val tagCount = events.flatMap { it.tags }.groupingBy { it }.eachCount().toList()
        .sortedByDescending { it.second }

    Column(modifier = Modifier.fillMaxWidth()) {
        SummaryCard(
            label = label,
            range = "${start.format(MD_FMT)} - ${end.format(MD_FMT)}",
            text = summaryText(events.size, chargeUp, chargeDown, encounters.size, start, end)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Block(
            title = "我话事",
            subtitle = "能量",
            peek = if (events.isEmpty()) "这段时间没记" else "${events.size} 件"
        ) {
            if (events.isEmpty()) {
                EmptyLine("这段时间没有记过能量。")
            } else {
                StatRow("记下", "${events.size} 件")
                StatRow("其中充到电的", "$chargeUp 件")
                StatRow("其中被消耗的", "$chargeDown 件")
                Spacer(modifier = Modifier.height(10.dp))
                Text("记得最多的：", style = MaterialTheme.typography.bodySmall, color = TextGray)
                Spacer(modifier = Modifier.height(6.dp))
                // 前六件里最多的那件顶到头，其余按它来量 —— 有比较，但没有刻度。
                val longest = tagCount.take(6).maxOfOrNull { it.second } ?: 0
                tagCount.take(6).forEach { (tag, n) ->
                    BarRow(tag, n, longest, LifeDimColors[1])
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "这里不合计点数。加加减减凑出来的一个数，说明不了这些天。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val meetDays = encounters.map { it.timestamp.logicalDate() }.distinct().size
        Block(
            title = "我遇见",
            subtitle = "四句话",
            peek = if (encounters.isEmpty()) "这段时间没写" else "$meetDays 天 · ${encounters.size} 笔"
        ) {
            if (encounters.isEmpty()) {
                EmptyLine("这段时间还没有在「我遇见」里写过一笔。它不像打卡，不催人 —— 想说的时候再去。")
            } else {
                val withNext = encounters.count { it.doNext.isNotBlank() }
                StatRow("记下", "${encounters.size} 笔")
                StatRow("分布在", "$meetDays 天")
                StatRow("写下「愿意为自己做什么」的", "$withNext 笔")
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "最近写的几笔：",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
                Spacer(modifier = Modifier.height(6.dp))
                encounters.sortedByDescending { it.timestamp }.take(5).forEach { e ->
                    Column(modifier = Modifier.padding(vertical = 5.dp)) {
                        Text(
                            e.oneLine(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2
                        )
                        Text(
                            "${e.timestamp.logicalDate().format(MD_FMT)} ${e.timestamp.logicalTimeString()}" +
                                if (e.doNext.isNotBlank()) " · 愿意做的：${e.doNext.trim()}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray,
                            maxLines = 1
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "这里不给这些话排序、也不打标签。它们不是指标，是那一天你自己的原话。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Block(title = "最近八周", subtitle = "", peek = "能量和那些话的走向") {
            val weeks = (7 downTo 0).map { mondayOf(logicalToday()).minusWeeks(it.toLong()) }
            val didCounts = weeks.map { w ->
                repo.events.count { it.timestamp.logicalDate() in w..w.plusDays(6) }
            }
            val meetCounts = weeks.map { w ->
                repo.encounters.count { it.timestamp.logicalDate() in w..w.plusDays(6) }
            }
            TrendRow("我话事（件）", didCounts, LifeDimColors[1])
            Spacer(modifier = Modifier.height(10.dp))
            TrendRow("我遇见（笔）", meetCounts, LifeDimColors[0])
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                weeks.forEach { w ->
                    Text(
                        w.format(MD_FMT).substringBefore("/"),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGray,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 计算

private fun rangeOf(idx: Int, today: LocalDate): Triple<LocalDate, LocalDate, String> = when (idx) {
    0 -> Triple(mondayOf(today), today, "本周")
    1 -> {
        val s = mondayOf(today).minusWeeks(1)
        Triple(s, s.plusDays(6), "上周")
    }
    else -> Triple(today.withDayOfMonth(1), today, "本月")
}

private fun mondayOf(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value - 1).toLong())

/**
 * 顶部那句。它不报任何合计数 ——
 * 「合计 +7」这种数是从几十件具体的事上压出来的一个点，丢掉的正是你记它的原因。
 */
private fun summaryText(
    events: Int,
    chargeUp: Int,
    chargeDown: Int,
    meets: Int,
    start: LocalDate,
    end: LocalDate
): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
    val head = "这 $days 天里，"
    val meetPart = if (meets > 0) "另外有 $meets 笔是你写给自己的四句话。" else ""
    if (events == 0) {
        return head + if (meets > 0) {
            "能量这一栏是空的，但${meetPart}能撑着过完这些天，本身就已经是事。"
        } else {
            "这里还几乎是空的。没关系 —— 能撑着过完这些天，本身就已经是事。"
        }
    }
    val up = if (chargeUp > 0) "$chargeUp 件是充到电的" else ""
    val down = if (chargeDown > 0) "$chargeDown 件是耗你的" else ""
    val both = listOf(up, down).filter { it.isNotBlank() }.joinToString("，")
    return head + "你记下了 $events 件事" +
        (if (both.isBlank()) "。" else "：$both。") +
        meetPart
}

// ---------------------------------------------------------------- 组件

@Composable
private fun SummaryCard(label: String, range: String, text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryPurple.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.20f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = PrimaryPurple
            )
            Text(range, style = MaterialTheme.typography.labelSmall, color = TextGray)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun Block(
    title: String,
    subtitle: String,
    peek: String,
    content: @Composable () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .tap { open = !open }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGray,
                        modifier = Modifier
                            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(peek, style = MaterialTheme.typography.bodySmall, color = TextGray)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (open) "收起" else "展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryPurple
                )
            }
            if (open) {
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextGray)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 「记得最多的」那几根条子。
 *
 * 它不是进度条，是**一把尺子**：记得最多的那件事顶到头，其余的按它来量。
 * 没有刻度、没有满格、没有那条「做到多少就够了」的线 ——
 * 一说出来就成了目标，而这个 app 不设目标。
 *
 * 分母曾经是这段时间记的总件数，那就糟了：条子会跟着你一起长，
 * 同一件事做了两次，在第 3 件时它到三分之二，等你记到第 20 件，它只剩十分之一 ——
 * 做得多，条子反而短。拿记录惩罚记录。
 *
 * 最短给 6%：记过一件就看得见一截，别让人怀疑自己到底记上没有。
 */
@Composable
private fun BarRow(label: String, n: Int, longest: Int, color: Color) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(
                "$n",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (longest <= 0) 0f else (n.toFloat() / longest).coerceIn(0.06f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.14f)
        )
    }
}

@Composable
private fun TrendRow(title: String, values: List<Int>, color: Color) {
    val max = values.maxOrNull() ?: 0
    Text(title, style = MaterialTheme.typography.bodySmall, color = TextGray)
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        values.forEach { v ->
            val ratio = if (max == 0) 0f else v.toFloat() / max
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                if (v > 0) {
                    Text(
                        v.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((6 + ratio * 30).dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (v > 0) color else CardBorder)
                )
            }
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = TextGray,
        lineHeight = 18.sp
    )
}

// ---------------------------------------------------------------- 六维

/**
 * 六维。原来在【生活】的最后一格，挪到这里 ——
 * 它是「一天收一次尾」的东西，和这一天做过的事、记过的情绪放在一起看才有意义。
 *
 * 今天直接可答；翻到以前的日子默认<b>只读</b>——
 * 那天已经过去了，看见它比改它对更重要；真要改，按一下【补记这一天的答案】。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DimsPane(
    repo: MoodRepository,
    /**
     * 正在看哪一天（null = 今天）。
     * 它由外面拿着，因为给它赋值的口子在二级页里 —— 翻日历看情绪的时候，
     * 看见那天六维答过（或没答），要能一步跳到这里来看／补。
     */
    viewDay: LocalDate?,
    onViewDay: (LocalDate?) -> Unit,
    onOpenAll: () -> Unit
) {
    val ctx = LocalContext.current
    val today = logicalToday()
    var editing by remember { mutableStateOf(false) }

    val day = viewDay ?: today
    val isToday = viewDay == null
    val existing = remember(day, repo.dims.size) { repo.dimsOf(day) }
    val readOnly = !isToday && !editing

    val values = remember { mutableStateListOf<Float>().apply { repeat(6) { add(0f) } } }
    val touched = remember { mutableStateListOf<Boolean>().apply { repeat(6) { add(false) } } }

    // 两个按钮按住时各缩一点：这一下是按到了还是没按到，眼睛也要看得见。
    val editInteraction = remember { MutableInteractionSource() }
    val editPressed by editInteraction.collectIsPressedAsState()
    val saveInteraction = remember { MutableInteractionSource() }
    val savePressed by saveInteraction.collectIsPressedAsState()

    LaunchedEffect(day, existing?.id) {
        for (i in 0..5) { values[i] = 0f; touched[i] = false }
        if (existing != null) {
            LIFE_DIMS.forEachIndexed { index, def ->
                values[index] = def.get(existing).toFloat()
                touched[index] = true
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 翻到别的日子时，顶上挂一条：现在看的是哪天、怎么回去。
        // 没有这条，很容易在旧日子里改了半天，以为改的是今天。
        if (!isToday) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (editing) MissColor.copy(alpha = 0.16f) else ChipBg)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (editing) "正在补记 ${day.format(DATE_CN_FMT)}"
                    else "正在看 ${day.format(DATE_CN_FMT)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (editing) MissColor else PrimaryPurple,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "返回今天",
                    style = MaterialTheme.typography.labelMedium,
                    color = PrimaryPurple,
                    modifier = Modifier.tap { onViewDay(null); editing = false }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Text(
            if (readOnly) "这是那天的答案，先看。要改再按下面的按钮。"
            else "每天最多答一次就够。0 分不丢人——有些日子就是这样。",
            style = MaterialTheme.typography.bodySmall,
            color = TextGray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "没碰过的那一栏会写着「未答」。它和「答了 0 分」是两件事——0 分是个很重的答案，不能被当成空白。",
            style = MaterialTheme.typography.bodySmall,
            color = TextGray
        )
        Spacer(modifier = Modifier.height(10.dp))

        LIFE_DIMS.forEachIndexed { index, def ->
            val answered = touched[index]
            val dimColor = LifeDimColors[index]
            val color = if (answered) dimColor else DisabledC
            // 按下去那一下的回音：整块染一层自己的颜色、缩一点点，约 0.4 秒退干净。
            // 六张各用一个 —— 共用一个会变成整屏一起亮，那是通知，不是反馈。
            val pulse = rememberPulse()
            val lit = pulse.alpha()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .zoom(1f - 0.012f * lit),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = blend(CardBg, dimColor, 0.20f * lit)
                ),
                border = if (lit > 0.01f) {
                    BorderStroke(1.5.dp, dimColor.copy(alpha = 0.55f * lit))
                } else null,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            def.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (answered) {
                            Surface(
                                color = blend(color.copy(alpha = 0.14f), color, 0.45f * lit),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    values[index].roundToInt().toString(),
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                        .zoom(1f + 0.22f * lit),
                                    color = color,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        } else {
                            Text(
                                "未答",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextGray,
                                modifier = Modifier
                                    .background(ChipBg, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        def.question,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SubText
                    )
                    if (def.hints.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        HintChips(def.hints)
                    }
                    if (readOnly) {
                        // 只读态画成实心条，不是一根可以拖的尺子：
                        // 手会习惯性地去拖它，拖不动才发现那是过去，那一下很难受。
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (values[index] / 10f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = color,
                            trackColor = color.copy(alpha = 0.15f)
                        )
                    } else {
                        // 刻度与条子共用一套坐标（见 Ruler.kt）：不会再出现「停在第 7 格、看着像 6 格半」。
                        ScaleRuler(
                            value = values[index],
                            color = color,
                            onPick = {
                                values[index] = it
                                touched[index] = true
                                pulse.bump()
                                Haptics.pick()
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (readOnly) {
            Button(
                onClick = { editing = true; Haptics.tap() },
                interactionSource = editInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .pressScale(editPressed, 0.97f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ChipBg)
            ) {
                Text("补记这一天的答案", color = PrimaryPurple, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "补记会覆盖 ${day.format(MD_FMT)} 原本的答案 —— 那天的旧答案不留备份。",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        } else {
            Button(
                onClick = {
                        Haptics.tap()
                    if (touched.none { it }) {
                        Toast.makeText(ctx, "至少动一根再保存", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    repo.upsertDims(
                        LifeDims(
                            timestamp = day.toNoonMillis(),
                            agency = values[0].roundToInt(),
                            achievement = values[1].roundToInt(),
                            vitality = values[2].roundToInt(),
                            connection = values[3].roundToInt(),
                            value = values[4].roundToInt(),
                            joy = values[5].roundToInt()
                        )
                    )
                    // 存好不弹 Toast：整屏那一下已经在说了，两处都说就成噪音了。
                    buzz(ctx)
                    SaveFeedback.fire(
                        LifeDimColors[4],
                        if (existing == null) "答案存好了 ✓" else "答案改好了 ✓"
                    )
                    editing = false
                },
                interactionSource = saveInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .pressScale(savePressed, 0.97f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text(
                    if (isToday) "保存今天的答案" else "存下这一天的答案",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "同一天再保存一次是覆盖，不会变成两条把走势拉歪。",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }

        // 最近 14 天的形状。它永远看的是最近 14 天，不跟着上面选的那天走 ——
        // 走势是走势，某一天是某一天。
        val days = (13 downTo 0).map { logicalToday().minusDays(it.toLong()) }
        val byDay = repo.dims.groupBy { it.timestamp.logicalDate() }
        val answered = days.count { byDay[it] != null }

        if (answered > 0) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "最近 14 天",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "答过 $answered 天",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextGray
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    MoodChart(
                        series = LIFE_DIMS.mapIndexed { index, def ->
                            ChartSeries(
                                name = def.name,
                                color = LifeDimColors[index],
                                values = days.map { d ->
                                    byDay[d]?.map { def.get(it) }?.average()?.toFloat()
                                }
                            )
                        },
                        labels = days.map { it.format(MD_FMT) }
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LIFE_DIMS.forEachIndexed { index, def ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(LifeDimColors[index])
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    def.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextGray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "平均与峰值",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LIFE_DIMS.forEachIndexed { index, def ->
                        val vals = days.mapNotNull { d ->
                            byDay[d]?.map { def.get(it) }?.average()?.toFloat()
                        }
                        val avg = if (vals.isEmpty()) 0f else vals.average().toFloat()
                        val peak = vals.maxOrNull() ?: 0f
                        Column(modifier = Modifier.padding(vertical = 5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    def.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LifeDimColors[index],
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(66.dp)
                                )
                                Text(
                                    "平均 ${(avg * 10).roundToInt() / 10f}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "峰值 ${peak.roundToInt()} · ${vals.size} 天",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextGray
                                )
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            LinearProgressIndicator(
                                progress = { (avg / 10f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = LifeDimColors[index],
                                trackColor = LifeDimColors[index].copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }
        }

        // 翻旧账的口子放在最下面。
        //
        // 以前是就地铺一张月历，点一天把上面六根尺子换成那天的答案 ——
        // 于是这一屏能翻的只有六维，**翻不到情绪**，而情绪才是那天真正发生过的事。
        // 现在它是一扇门：点开是独立的一页，顶部有日历，往下是每一天的情绪，
        // 跟【思念】那份是同一套。六维的旧答案还在这一屏上面翻（点一天，再看）。
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .tap(onClick = onOpenAll)
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "全部记录",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "情绪的每一天：顶上是日历，下面是当天记下的那几笔，点开才看见当时的四个数。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray,
                            lineHeight = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "›",
                        style = MaterialTheme.typography.titleSmall,
                        color = PrimaryPurple
                    )
                }
            }
        }
    }
}

/** 补记往日时用它当时间戳：那天中午 12 点，落在那天的逻辑日里，不会被算到隔壁一天去。 */
private fun java.time.LocalDate.toNoonMillis(): Long =
    java.time.LocalDateTime.of(this, java.time.LocalTime.NOON)
        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
