package com.example.moodlog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moodlog.ui.theme.*
import com.example.moodlog.data.ConfirmLabel
import com.example.moodlog.data.LIFE_DIMS
import com.example.moodlog.data.LifeDims
import com.example.moodlog.data.MoodRecord
import com.example.moodlog.data.MoodRepository
import com.example.moodlog.data.MeStore
import com.example.moodlog.data.buildVerdict
import com.example.moodlog.data.answerText
import com.example.moodlog.data.labels
import com.example.moodlog.data.emotionText
import com.example.moodlog.data.buildDayDigest
import com.example.moodlog.data.moveLines
import com.example.moodlog.data.headlineOf
import com.example.moodlog.data.lifeText
import com.example.moodlog.data.chargeLines
import com.example.moodlog.data.meetLines
import com.example.moodlog.data.QuoteStore
import com.example.moodlog.ui.theme.CardBg
import com.example.moodlog.ui.theme.LifeDimColors
import com.example.moodlog.ui.theme.MissColor
import com.example.moodlog.ui.theme.PanicColor
import com.example.moodlog.ui.theme.PrimaryPurple
import com.example.moodlog.ui.theme.TextGray
import com.example.moodlog.util.DATE_CN_FMT
import com.example.moodlog.util.logicalDate
import com.example.moodlog.util.logicalTimeString
import com.example.moodlog.util.logicalToday
import com.example.moodlog.util.LongImage
import java.time.LocalDate

/**
 * 今天的总结。双击【我】页那句、或者从【成为】页顶部进来。
 *
 * 这一页刻意不做的事：不给今日评分、不发徽章、不做连续天数、不写「你今天比昨天好」、
 * 不在情绪很低的时候夸你。数据少的时候它就说数据少，不编一段漂亮话。
 * 它只回答三个问题：今天情绪怎么样、今天生活怎么样、离你想成为的那个人有多远。
 */
@Suppress("LongMethod")
@Composable
fun SummaryScreen(
    repo: MoodRepository,
    padding: PaddingValues,
    day: LocalDate,
    onDayChange: (LocalDate) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dayKey = day.toString()
    val today = logicalToday()
    val isToday = day == today

    var goal by remember { mutableStateOf(MeStore.goal(context)) }
    var noteText by remember(dayKey) { mutableStateOf(MeStore.note(context, dayKey)) }
    var editingGoal by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var showCalendar by remember { mutableStateOf(false) }

    // 长图：把这一天画出来存到相册。纯 Canvas 绘制，不依赖界面当前画到哪儿。
    var imageHint by remember { mutableStateOf("") }
    // 存一次加一：给「存下这一句」那个按钮当信号用。
    var savedCount by remember { mutableIntStateOf(0) }
    var imageJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()

    fun makeImage(
        dayRecords: List<MoodRecord>,
        dayEvents: List<com.example.moodlog.data.LifeEvent>,
        dayStances: List<com.example.moodlog.data.Stance>,
        dayDims: LifeDims?,
        dayMoves: List<com.example.moodlog.data.Move>
    ) {
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                val digest = buildDayDigest(
                    dayLabel = day.format(DATE_CN_FMT),
                    records = dayRecords,
                    events = dayEvents,
                    stances = dayStances,
                    dims = dayDims,
                    goal = goal,
                    note = noteText,
                    encounters = repo.encounters.filter { it.timestamp.logicalDate() == day },
                    moves = dayMoves,
                    isToday = isToday
                )
                LongImage.save(context, LongImage.render(digest), day.toString())
            }
            imageHint = if (name == null) {
                "这台手机存不进相册（需要 Android 10 以上）。导出 CSV 一样能留底。"
            } else {
                "已经存到相册 · 心锚：$name"
            }
        }
    }

    val records = remember(dayKey, tick, repo.records.size) {
        repo.records.filter { it.timestamp.logicalDate() == day }.sortedBy { it.timestamp }
    }
    val events = remember(dayKey, tick, repo.events.size) {
        repo.events.filter { it.timestamp.logicalDate() == day }.sortedBy { it.timestamp }
    }
    val dims = remember(dayKey, tick, repo.dims.size) { repo.dimsOf(day) }
    val stances = remember(dayKey, tick, repo.stances.size) {
        repo.stancesOf(dayKey).filter { it.keys.isNotEmpty() || it.custom.isNotBlank() }
    }
    val dayEncounters = remember(dayKey, tick, repo.encounters.size) {
        repo.encounters.filter { it.timestamp.logicalDate() == day }.sortedBy { it.timestamp }
    }
    val dayMoves = remember(dayKey, tick, repo.moves.size) {
        repo.moves.filter { it.timestamp.logicalDate() == day }.sortedBy { it.timestamp }
    }

    // 同一天抽到同一句：种子是日期，不随页面刷新变。
    val verdict = remember(dayKey, records.size, events.size, dayEncounters.size) {
        buildVerdict(day, records, events, dayEncounters)
    }
    var pendingDelete by remember { mutableStateOf<MoodRecord?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 这一页是盖在【成为】那一屏上的：底色铺满，底下的东西才不会从边上透出来。
            .background(PageBg)
            .blockClicks()
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            // 输入法把底部那一块掀起来：正在写的那一句要留在视野里，不能被压在键盘下面。
            .imePadding()
            .padding(horizontal = 18.dp)
            .padding(top = 12.dp, bottom = 10.dp)
    ) {
        SubPageTopBar(
            title = "今天的总结",
            hint = if (isToday) "今天" else "那一天",
            onBack = onBack
        )

        // 日期：只能往回翻，不看还没有发生的日子
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "‹ 前一天",
                style = MaterialTheme.typography.bodySmall,
                color = PrimaryPurple,
                modifier = Modifier.tap { onDayChange(day.minusDays(1)) }
            )
            Spacer(modifier = Modifier.weight(1f))
            // 日期可以点开月历 —— 翻到某一天不用一格一格往前点。
            Text(
                day.format(DATE_CN_FMT),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryPurple,
                modifier = Modifier.tap { showCalendar = true }
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "后一天 ›",
                style = MaterialTheme.typography.bodySmall,
                color = if (day >= today) FaintText else PrimaryPurple,
                modifier = if (day >= today) Modifier else Modifier.tap { onDayChange(day.plusDays(1)) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 开头一句
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            color = ChipBg,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (isToday) "今天" else "那一天",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGray
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    headlineOf(records, isToday),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 26.sp,
                    color = SubText
                )
                // 和【成为】页顶部是同一句：同一天不换措辞。
                // 上面那句说的是「这天留下了什么」，这一句说的是「这天你是怎么过来的」。
                if (verdict.main.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        verdict.main,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 21.sp,
                        color = SubText
                    )
                }
                if (verdict.extra.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        verdict.extra,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                        color = SuccessGreen
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Block(title = "情绪", body = emotionText(records)) {
            if (records.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                PeakRow("想念", records.map { it.miss }.filter { it >= 0 }.maxOrNull()?.times(10) ?: 0, MissColor)
                PeakRow("恐慌", records.map { it.panic }.filter { it >= 0 }.maxOrNull()?.times(10) ?: 0, PanicColor)
                PeakRow("想联系", records.map { it.contact }.filter { it >= 0 }.maxOrNull()?.times(10) ?: 0, Color(0xFFF2994A))
                PeakRow(ConfirmLabel.name, records.map { it.confirm }.filter { it >= 0 }.maxOrNull()?.times(10) ?: 0, Color(0xFF6C9BF2))
            }
            val aftermath = aftermathText(records)
            if (aftermath.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    aftermath,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Block(title = "生活", body = lifeText(events, dims, stances, dayEncounters)) {
            if (events.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "我话事 · 这一天做了什么",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGray
                )
                Spacer(modifier = Modifier.height(4.dp))
                chargeLines(events).forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
            if (dayMoves.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "运动 · 动了什么、多久",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGray
                )
                Spacer(modifier = Modifier.height(4.dp))
                moveLines(dayMoves).forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
            if (dayEncounters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "我遇见 · 那天说的几句话",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGray
                )
                Spacer(modifier = Modifier.height(4.dp))
                meetLines(dayEncounters).forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
            if (dims != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val items = LIFE_DIMS.mapIndexed { i, def -> def.name to def.get(dims) }
                val high = items.maxByOrNull { it.second }
                val low = items.minByOrNull { it.second }
                if (high != null && low != null && high.second != low.second) {
                    Text(
                        "最高的是${high.first} ${high.second} 分，最低的是${low.first} ${low.second} 分。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // 这一天的每一笔。点进来就是为了看这一天的原貌，所以默认展开、日记全文照抄。
        if (records.isNotEmpty() || dayEncounters.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Block(
                title = "这一天的每一笔",
                body = if (records.isNotEmpty()) "${records.size} 次 · 按时间排" else ""
            ) {
                records.forEach { r ->
                    val time = r.timestamp.logicalTimeString()
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(13.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    time,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextGray,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "删除",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFC2574C),
                                    modifier = Modifier.tap { pendingDelete = r }
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            // 没答的那一栏写「未答」，不写成 0 —— 0 分是个很重的答案。
                            Text(
                                "想念 ${r.miss.answerText()} · 恐慌 ${r.panic.answerText()} · " +
                                    "想联系 ${r.contact.answerText()} · ${ConfirmLabel.name} ${r.confirm.answerText()}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = SubText
                            )
                            if (r.diary.isNotBlank()) {
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    r.diary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SubText,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
                if (dayEncounters.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "我遇见 · 那天说的几句话",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    dayEncounters.forEach { e ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(
                                    e.timestamp.logicalTimeString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextGray
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    listOf(
                                        "发生了什么：" to e.happened,
                                        "正在经历：" to e.feeling,
                                        "我需要：" to e.need,
                                        "愿意为自己做：" to e.doNext
                                    ).filter { it.second.isNotBlank() }
                                        .joinToString("\n") { (k, v) -> "$k$v" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SubText,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        DistanceBlock(
            goal = goal,
            positiveCount = events.count { it.delta > 0 },
            chargeItems = events.filter { it.delta > 0 }.flatMap { it.tags }.distinct(),
            dimsAvg = dims?.let { LIFE_DIMS.map { def -> def.get(it) }.average().toFloat() },
            position = distanceOf(events.count { it.delta > 0 }, dims),
            onEditGoal = { editingGoal = true }
        )

        Spacer(modifier = Modifier.height(12.dp))
        Block(title = "给${if (isToday) "今天" else "那一天"}的自己", body = "") {
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollIntoViewOnFocus(),
                placeholder = { Text("写一句，留到以后翻（可留空）") },
                shape = RoundedCornerShape(12.dp),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 存完要有动静：按钮矮一下、字变成「已存下 ✓」，然后自己变回去。
            // 空着也能存 —— 有时候「今天不想留话」本身就是要留的话。
            SaveBar(
                label = "存下这一句",
                savedLabel = "存好了",
                savedCount = savedCount
            ) {
                MeStore.saveNote(context, dayKey, noteText)
                savedCount++
                tick++
                true
            }
        }

        if (imageHint.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                imageHint,
                style = MaterialTheme.typography.bodySmall,
                color = PrimaryPurple,
                lineHeight = 18.sp
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = { Haptics.tap(); imageJob?.cancel(); imageJob = scope.launch { makeImage(records, events, stances, dims, dayMoves) } },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ChipBg)
        ) {
            Text("把这一天存成一张长图", color = PrimaryPurple, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "存到相册的「心锚」目录。也可以直接用系统的滚动截屏 —— 这些页都是整页渲染的，一路截到底不会断。",
            style = MaterialTheme.typography.bodySmall,
            color = TextGray,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            QuoteStore.today(context),
            style = MaterialTheme.typography.bodySmall,
            color = TextGray,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        )
        Text(
            "这一页不打分、不评级。数据少的时候它就少说两句 —— 不编。",
            style = MaterialTheme.typography.bodySmall,
            color = DisabledC,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showCalendar) {
        val peaks = panicPeakByDay(repo.records)
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
                marked = { peaks[it] },
                selected = day,
                onPick = {
                    showCalendar = false
                    onDayChange(it)
                },
                onClose = {
                    showCalendar = false
                    onDayChange(today)
                }
            )
        }
    }

    // 从这一页删一笔，也要先问一句：那一笔连同当时写的字一起没了。
    pendingDelete?.let { r ->
        ConfirmDeleteDialog(
            preview = r.timestamp.logicalTimeString() +
                (if (r.diary.isNotBlank()) "　" + r.diary.take(60) else "　（当时没写字）"),
            tail = "连这里的日记一起删，无法恢复。",
            onConfirm = { repo.delete(r.id); pendingDelete = null; tick++ },
            onDismiss = { pendingDelete = null }
        )
    }
    }

    if (editingGoal) {
        var draft by remember { mutableStateOf(goal) }
        AlertDialog(
            onDismissRequest = { editingGoal = false },
            title = { Text("我想成为的那个人", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        "只写一句话，只存在这台手机上，随时能改。它不是目标，也不是誓言 —— 是你在最难的时候，想照着做的那个样子。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .scrollIntoViewOnFocus(),
                        placeholder = { Text("比如：一个不用靠确认TA在哪，也能睡着的人。") },
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                        Haptics.tap()
                    MeStore.saveGoal(context, draft)
                    goal = draft.trim()
                    editingGoal = false
                    tick++
                }) { Text("存下", color = PrimaryPurple) }
            },
            dismissButton = { TextButton(onClick = { Haptics.tap(); editingGoal = false }) { Text("关闭") } }
        )
    }
}

// ------------------------------------------------------------------ 文案

private fun aftermathText(records: List<MoodRecord>): String {
    val answered = records.filter { it.outcome != 0 }
    if (answered.isEmpty()) return ""
    val ok = answered.count { it.outcome == 2 }
    val so = answered.count { it.outcome == 1 }
    val help = answered.count { it.outcome == 3 }
    val parts = mutableListOf<String>()
    if (ok > 0) parts.add("还可以 $ok 次")
    if (so > 0) parts.add("勉强行 $so 次")
    if (help > 0) parts.add("要求助 $help 次")
    val minutes = answered.mapNotNull { it.toOutcomeMinutes }.filter { it >= 0 }
    val speed = if (minutes.isEmpty()) "" else "。从慌起来到站稳，最快一次 ${minutes.min()} 分钟"
    return "「后来怎么样了」答了 ${answered.size} 次：${parts.joinToString("、")}$speed"
}

/**
 * 离那个人还有多远：一根不说分数的条，位置由两样东西各占一半凑出来。
 *
 * 一半是【我话事】里**充到电**的那几件（delta 为正的），三件算走到头；
 * 只数正向的那几件，是因为「往上走」和「没被拖下去」不是一回事 ——
 * 把它们混在一起算，耗得很惨的一天也能被同样数量的记录冲成中间位。
 *
 * 另一半是【成为 · 我的感受】那六个问题的平均分。
 *
 * 它不是一个分数，也不和昨天比：只是一个位置，让你看得见今天偏在哪一头。
 */
private fun distanceOf(positiveCount: Int, dims: LifeDims?): Float {
    val s = (positiveCount / 3f).coerceIn(0f, 1f)
    val d = if (dims == null) 0f else {
        val avg = LIFE_DIMS.map { it.get(dims) }.average().toFloat()
        (avg / 10f).coerceIn(0f, 1f)
    }
    val raw = if (dims == null) s else s * 0.5f + d * 0.5f
    // 两端各留一截：任何一天都既不是「完全不是那个人」，也不是「就是那个人」。
    return (0.18f + raw * 0.66f).coerceIn(0.18f, 0.84f)
}

// ------------------------------------------------------------------ 组件

@Composable
private fun Block(
    title: String,
    body: String,
    extra: @Composable () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = TextGray,
                letterSpacing = 1.5.sp
            )
            if (body.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 24.sp
                )
            }
            extra()
        }
    }
}

@Composable
private fun PeakRow(name: String, percent: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.bodySmall, color = TextGray, modifier = Modifier.width(52.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(ChipBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
        Text(
            (percent / 10).toString(),
            style = MaterialTheme.typography.bodySmall,
            color = TextGray,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun DistanceBlock(
    goal: String,
    positiveCount: Int,
    chargeItems: List<String>,
    dimsAvg: Float?,
    position: Float,
    onEditGoal: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "离那个人有多远",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGray,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (goal.isBlank()) "写一句 ›" else "改一句 ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryPurple,
                    modifier = Modifier.tap(onClick = onEditGoal)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (goal.isBlank()) {
                Text(
                    "还没写下你想成为的那个人。没有靶子，这一页只能给你数字。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    lineHeight = 20.sp
                )
            } else {
                Text(
                    goal,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 24.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // 一根不说分数的条：左端是「今天撑住就算」，右端是「就是那个人」
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ChipBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(position)
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(CardBorder, PrimaryPurple)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(position)
                        .height(7.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(PrimaryPurple)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) {
                Text("今天撑住就算", style = MaterialTheme.typography.labelSmall, color = TextGray)
                Spacer(modifier = Modifier.weight(1f))
                Text("就是那个人", style = MaterialTheme.typography.labelSmall, color = TextGray)
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 这根条子是怎么到的这个位置，写在这里 —— 一根不知道自己怎么算出来的条子，看着像判分。
            Text(
                "怎么来的：一半是【我话事】里充到电的那几件（$positiveCount 件，3 件算到头）；" +
                    (
                        dimsAvg?.let { "一半是【成为 · 我的感受】那六个问题的平均分 ${formatOneDecimal(it)} 分。" }
                            ?: "另一半是【成为 · 我的感受】那六个问题的平均分 —— 今天没答，那一半记 0。"
                        ) +
                    "它不是分数，也不拿来和昨天比。",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            val hasData = positiveCount > 0 || dimsAvg != null
            val tail = when {
                !hasData && goal.isNotBlank() ->
                    "今天没有可对照的东西。这一段就空着 —— 不编，不补，不假装。"
                !hasData ->
                    "今天在「我话事」和「我的感受」里都还没有东西，这根条子先停在原地。"
                position < 0.4f ->
                    "偏这一端。今天主要是撑住的，这已经不容易。"
                position < 0.62f ->
                    "不在两端。今天你不是那个人，但你做了那个人会做的事。"
                else ->
                    "往那一头靠了不少。今天有几件事，是那个人会做的。"
            }
            Text(
                tail,
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                lineHeight = 20.sp
            )
            if (chargeItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "今天朝它走过的一步：" + chargeItems.take(3).joinToString("、"),
                    style = MaterialTheme.typography.bodySmall,
                    color = SuccessGreen,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
