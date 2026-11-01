package com.example.moodlog.ui

import android.Manifest
import android.net.Uri
import java.time.LocalDate
import java.time.YearMonth
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moodlog.ui.theme.*
import com.example.moodlog.data.ConfirmLabel
import com.example.moodlog.data.EMOTION_FIELDS
import com.example.moodlog.data.IMPULSE_LABELS
import com.example.moodlog.data.MoodRecord
import com.example.moodlog.data.MoodRepository
import com.example.moodlog.data.answerText
import com.example.moodlog.data.OUTCOME_LABELS
import com.example.moodlog.media.AttachmentStore
import com.example.moodlog.media.AudioPlayer
import com.example.moodlog.media.AudioRecorder
import com.example.moodlog.media.openAttachment
import com.example.moodlog.ui.theme.ConfirmColor
import com.example.moodlog.ui.theme.ContactColor
import com.example.moodlog.ui.theme.EmotionColors
import com.example.moodlog.ui.theme.MissColor
import com.example.moodlog.ui.theme.PanicColor
import com.example.moodlog.ui.theme.PrimaryPurple
import com.example.moodlog.ui.theme.TextGray
import com.example.moodlog.util.DATE_CN_FMT
import com.example.moodlog.util.DAY_START_HOUR
import com.example.moodlog.util.exportCsv
import com.example.moodlog.util.logicalDate
import com.example.moodlog.util.logicalTimeString
import com.example.moodlog.util.logicalToday
import com.example.moodlog.util.toLocalDateTime
import com.example.moodlog.util.toTimeString

private const val DIARY_MAX = 2000

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    repo: MoodRepository,
    padding: PaddingValues,
    onBack: (() -> Unit)? = null,
    /**
     * 底部那颗「导出 CSV」要不要。
     * 【思念】那份要；【成为 · 我的感受】这份不要 —— 导出是「把数据带走」的动作，
     * 全 app 只留【我】一个口子，两个地方都能按反而不知道该按哪个。
     */
    showCsv: Boolean = true,
    /** 间距收一档。一天里往往只有两三笔，铺太开就变成「翻很久」而不是「看一眼」。 */
    compact: Boolean = false,
    /** 那天六维答过没有。null = 这一页不管六维（【思念】那份）。 */
    dimsAnswered: ((LocalDate) -> Boolean)? = null,
    /** 展开某一天时挂的那行「这天的六维」。点了回到【我的感受】并切到那一天。 */
    onOpenDims: ((LocalDate) -> Unit)? = null
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<MoodRecord?>(null) }
    val audioPlayer = remember { AudioPlayer() }
    DisposableEffect(Unit) {
        onDispose { audioPlayer.stop() }
    }
    var query by remember { mutableStateOf("") }
    // 月历显示哪一个月；选中的那天在下面展开，其余的日子收成一行。
    var monthAnchor by remember { mutableStateOf(YearMonth.from(logicalToday())) }
    var selectedDay by remember { mutableStateOf(logicalToday()) }
    // 「后来怎么样了」没答的那几次：今天之内还能补，过一天自己收起来，不再追问。
    var showingPending by remember { mutableStateOf(false) }

    // 侧滑 / 返回键 = 回到上一页，而不是直接退出 app。
    // 详情弹窗开着的时候先关弹窗。
    BackHandler(enabled = onBack != null && editing == null) { onBack?.invoke() }

    val all = repo.records
    val today = logicalToday()
    val pendingToday = all.filter {
        it.panic >= 8 && it.outcome == 0 && it.timestamp.logicalDate() == today
    }
    val searching = query.isNotBlank()
    // 折叠只影响「显示多少」，不影响「能不能被找到」：搜索永远翻的是全部日记。
    val records = if (searching) all.filter { it.diary.contains(query.trim(), true) } else all
    val grouped = records
        .groupBy { it.timestamp.logicalDate() }
        .toSortedMap(compareByDescending { it })
    val peakByDay = all
        .groupBy { it.timestamp.logicalDate() }
        .mapNotNull { (day, list) ->
            list.map { r -> r.panic }.filter { it >= 0 }.maxOrNull()?.let { day to it }
        }
        .toMap()

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 二级页是盖在原来那一屏上面的，不是换掉它 —— 所以底色必须自己铺满。
            // 少了这一行，底下的【趋势】会从没盖住的地方透出来（状态栏那一条也是它）。
            .background(PageBg)
            .blockClicks()
            .padding(padding)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(top = 12.dp, bottom = 10.dp)
    ) {
        SubPageTopBar(
            title = "全部记录",
            hint = "情绪 · 每一天",
            onBack = { onBack?.invoke() },
            modifier = Modifier.padding(horizontal = 18.dp)
        )

        // 今天还没答的那几句「后来怎么样了」。过了这一天它自己消失，不会变成待办。
        if (pendingToday.isNotEmpty() && !showingPending) {
            Surface(
                onClick = { showingPending = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                color = CareBg,
                border = BorderStroke(1.dp, CareBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "有 ${pendingToday.size} 次还没标注后来怎么样了",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = CareTitle
                        )
                        Text(
                            "现在补，也可以继续划走。过了今天它不会再问。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                    }
                    Text("›", color = CareTitle)
                }
            }
        }
        if (showingPending && pendingToday.isNotEmpty()) {
            pendingToday.forEach { record ->
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    Text(
                        "${record.timestamp.logicalTimeString()} · 恐慌 ${record.panic.answerText()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AftermathCard(
                        caption = "这一条已经存好了。这三个只是补一句，现在答不出来也可以先跳过。",
                        onAnswer = { repo.markOutcome(record.id, it) },
                        onSkip = { showingPending = false }
                    )
                }
            }
        }

        if (all.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                placeholder = { Text("在日记里找一句话…") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            MonthGrid(
                month = monthAnchor,
                selected = selectedDay,
                today = today,
                peakOf = { peakByDay[it] },
                // 点邻月那几天 = 翻到那个月，顺手选中它。跨月那一段才连得上。
                onPick = { selectedDay = it; monthAnchor = YearMonth.from(it) },
                onShift = { monthAnchor = monthAnchor.plusMonths(it.toLong()) },
                modifier = Modifier.padding(horizontal = 18.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (records.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (all.isEmpty()) "还没有记录，去「情绪」页记一笔吧" else "没有匹配的日记",
                    color = TextGray
                )
            }
        } else {
            // 这里刻意不用 LazyColumn：它「只画看得见的那几项」，
            // 系统的滚动截屏就会只截到当前这一屏。全量渲染，长截图才截得全。
            //
            // 默认只铺开一天（今天），更早的日子收成一行 —— 但搜索翻的是全部记录，
            // 命中的那天会被直接摊开，不会因为收着就找不到。
            // 默认只铺开一天（今天），更早的日子收成一行 —— 但搜索翻的是全部记录，
            // 命中的那天会被直接摊开，不会因为收着就找不到。
            //
            // 「最近七天」和「更早」分开摆：前七天的日子你多半还认得出是星期几，
            // 再往前就是一堆数字了，得有个台阶。
            val cutoff = today.minusDays(6)
            // 紧凑档：日期标题和每一笔之间的空都收一档。字号、行距、圆角一概不动 ——
            // 那些是全局排版管着的，这里动一处，别的地方就对不齐了。
            val gap = if (compact) 6.dp else 10.dp
            val dayBlock: @Composable (LocalDate, List<MoodRecord>) -> Unit = { day, list ->
                if (searching || day == selectedDay) {
                    Text(
                        text = "${day.format(DATE_CN_FMT)} · ${list.size} 次 · 从早上六点开始算",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextGray,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(
                            start = 18.dp,
                            top = if (compact) 4.dp else 6.dp,
                            end = 18.dp,
                            bottom = 2.dp
                        )
                    )
                    // 六维挂在这一天的情绪上面：那天是「发生了什么」和「我怎么样」，
                    // 原来这两样分在两页，翻得到情绪就翻不到六维。
                    if (onOpenDims != null) {
                        val answered = dimsAnswered?.invoke(day) == true
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 18.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ChipBg)
                                .tap { onOpenDims(day) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (answered) "这天的六维 · 答过" else "这天的六维 · 没答",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGray,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (answered) "去看看 ›" else "去补 ›",
                                style = MaterialTheme.typography.labelSmall,
                                color = PrimaryPurple
                            )
                        }
                        Spacer(modifier = Modifier.height(gap))
                    }
                    list.sortedByDescending { it.timestamp }.forEach { record ->
                        Row(modifier = Modifier.padding(horizontal = 18.dp)) {
                            RecordRow(record = record, onClick = { editing = record })
                        }
                        Spacer(modifier = Modifier.height(gap))
                    }
                } else {
                    DayFoldRow(
                        day = day,
                        count = list.size,
                        peak = peakByDay[day],
                        modifier = Modifier.padding(
                            horizontal = 18.dp,
                            vertical = if (compact) 2.dp else 3.dp
                        ),
                        onClick = { selectedDay = day }
                    )
                }
            }

            val recentDays = grouped.filter { it.key >= cutoff }
            val olderDays = grouped.filter { it.key < cutoff }
            if (recentDays.isNotEmpty()) {
                SectionLabel("最近七天")
                recentDays.forEach { (day, list) -> dayBlock(day, list) }
            }
            if (olderDays.isNotEmpty()) {
                SectionLabel("更早 · ${olderDays.size} 天有记录")
                olderDays.forEach { (day, list) -> dayBlock(day, list) }
            }
            // 导出放最底下：原来在右上角，太靠近通知栏，容易误触
            if (showCsv) {
                TextButton(
                    onClick = {
                        Haptics.tap()
                        val name = exportCsv(context, all)
                        Toast.makeText(
                            context,
                            if (name == null) "导出失败（需要 Android 10 以上）"
                            else "已导出到下载目录：$name",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("导出 CSV（情绪）", color = PrimaryPurple)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    editing?.let { record ->
        var attachments by remember(record.id) { mutableStateOf(record.attachments) }
        var isRecording by remember(record.id) { mutableStateOf(false) }
        val recorder = remember(record.id) { AudioRecorder(context) }

        val pickMedia = rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(maxItems = 6)
        ) { uris: List<Uri> ->
            val saved = uris.mapNotNull { uri ->
                val mime = context.contentResolver.getType(uri)
                AttachmentStore.save(context, uri, AttachmentStore.extFor(mime))
            }
            if (saved.isNotEmpty()) attachments = attachments + saved
        }

        val requestMic = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                isRecording = recorder.start()
                if (!isRecording) Toast.makeText(context, "录音启动失败", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "需要麦克风权限才能录语音", Toast.LENGTH_SHORT).show()
            }
        }

        RecordDetailDialog(
            record = record,
            attachments = attachments,
            isRecording = isRecording,
            playingPath = audioPlayer.playingPath,
            onTogglePlay = { audioPlayer.toggle(it) },
            onPickMedia = {
                pickMedia.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            },
            onToggleRecording = {
                if (isRecording) {
                    val path = recorder.stop()
                    isRecording = false
                    if (path != null) attachments = attachments + path
                    else Toast.makeText(context, "录音太短，没能保存", Toast.LENGTH_SHORT).show()
                } else {
                    requestMic.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onRemoveAttachment = { path ->
                attachments = attachments - path
                AttachmentStore.delete(path)
            },
            onOpenAttachment = { openAttachment(context, it) },
            onDismiss = { editing = null },
            onSave = { text, files ->
                repo.update(record.copy(diary = text, attachments = files))
                editing = null
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                attachments.forEach { AttachmentStore.delete(it) }
                repo.delete(record.id)
                editing = null
                Toast.makeText(context, "已删除这条记录", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

/** 一周七天，周一在前。日历里没有「日」之外的花样 —— 它只是让你一天点进去。 */
private val WEEK_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 一天有多难。只用那天恐慌到过的最高分，不给总分、不评级 ——
 * 它回答的是「那天值不值得再点开看一眼」，不是「那天好不好」。
 */
private fun severityColor(peak: Int?): Color = when {
    // 没记的日子是一根<b>灰条</b>。以前是蓝的 —— 蓝色是「有内容」的颜色，
    // 拿它表示「那天什么都没记」，等于凭空造出一个不存在的读数。
    // 灰色说的是「那天没有这个数」，不是「那天不难」，也不是「那天没过好」。
    peak == null -> DisabledC
    peak >= 9 -> Color(0xFFC0272D)      // 非常严重：大红
    peak >= 8 -> Color(0xFFE24B4A)
    peak >= 6 -> Color(0xFFEF8A4A)
    peak >= 4 -> Color(0xFFEFB54A)
    else -> Color(0xFF7FC4A8)           // 平稳
}

/**
 * 整月的日历。
 *
 * 以前这里是一周七个格：「上周比这周难」这种话，得先翻一次再回到记忆里比。
 * 一个月铺在一屏里，起落是眼睛一次看完的事，不需要算术。
 *
 * 未来的日子置灰不可点 —— 明天还没发生，那里没有东西可看。
 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    peakOf: (LocalDate) -> Int?,
    onPick: (LocalDate) -> Unit,
    onShift: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val thisMonth = YearMonth.from(today)
    val firstOfMonth = month.atDay(1)
    // 周一开头：(dayOfWeek 1..7) → 前面补几格
    val lead = firstOfMonth.dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    // 前后那几天是<b>真实的日期</b>，不是补白的空格子：10 月 1 号是周四，
    // 行首就摆着 9 月 28、29、30；月末同理。只是画面权重降下来（数字淡一档）。
    // 它们可点 —— 点一下就翻到那个月，跨月那一段才接得上。
    val head = (lead downTo 1).map { firstOfMonth.minusDays(it.toLong()) }
    val body = (1..daysInMonth).map { month.atDay(it) }
    // 只补到这一行的周日为止：这个月最后一天是周六，尾巴上就只多一个下月 1 号。
    // 不把下一整周也铺出来 —— 那几天还没到，摆在那儿像是已经过完了。
    val fill = (7 - (head.size + daysInMonth) % 7) % 7
    val tail = if (fill > 0) {
        (1..fill).map { month.atDay(daysInMonth).plusDays(it.toLong()) }
    } else emptyList()
    val all = head + body + tail

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { Haptics.tap(); onShift(-1) },
                    modifier = Modifier.width(34.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("‹", style = MaterialTheme.typography.titleMedium, color = TextGray) }
                Text(
                    "${month.year} 年 ${month.monthValue} 月",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                // 不能翻到未来的那个月去
                TextButton(
                    onClick = { Haptics.tap(); onShift(1) },
                    enabled = month < thisMonth,
                    modifier = Modifier.width(34.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        "›",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (month < thisMonth) TextGray else FaintText
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                WEEK_LABELS.forEach { label ->
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = TextGray,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            all.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        MonthCell(
                            date = date,
                            outside = YearMonth.from(date) != month,
                            selected = selected,
                            today = today,
                            peak = peakOf(date),
                            onPick = onPick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "颜色是那天恐慌到过的最高分。灰色是那天没记 —— 不是那天不难。" +
                    "数字淡的那几天是上个月或下个月，点一下就翻过去。还没到的日子不画条。",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = TextGray,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun MonthCell(
    date: LocalDate,
    /** 邻月（上个月的尾巴 / 下个月的开头）的那几天。数字淡一档，其它照旧。 */
    outside: Boolean,
    selected: LocalDate,
    today: LocalDate,
    peak: Int?,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.height(40.dp),
        contentAlignment = Alignment.Center
    ) {
        val future = date > today
        val has = peak != null
        val isPicked = date == selected
        val isToday = date == today
        Column(
            modifier = Modifier
                .width(34.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (isPicked) ChipBg else Color.Transparent)
                .then(
                    if (isToday) {
                        Modifier.border(1.5.dp, PrimaryPurple, RoundedCornerShape(9.dp))
                    } else Modifier
                )
                // 只有记过事的日子点得动：点空白的一天，下面什么都不会出现，那像是坏了。
                // 未来的日子连点击都不给。
                .then(
                    if (future || !has) Modifier else Modifier.tap { onPick(date) }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "${date.dayOfMonth}",
                fontSize = 12.sp,
                fontWeight = if (isToday || isPicked) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    future -> FaintText
                    isToday -> PrimaryPurple
                    isPicked -> PrimaryPurple
                    outside -> DisabledC
                    // 没记的那天是<b>灰</b>的：它说的是「那天没有这个读数」，不是「那天不难」。
                    has -> SubText
                    else -> TextGray
                }
            )
            Spacer(modifier = Modifier.height(3.dp))
            // 还没到的日子连条子都不画：不给还没发生的事留位置。
            if (!future) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(severityColor(peak))
                )
            }
        }
    }
}

/** 一段的天数标题。七天以内的是「还记得的日子」，再往前是「更早」。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MarkText,
        modifier = Modifier.padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 4.dp)
    )
}
/** 收起来的那一天。点一下摊开，再点别的日子它就自己收回去。 */
@Composable
private fun DayFoldRow(
    day: LocalDate,
    count: Int,
    peak: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ChipBg,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(severityColor(peak))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                day.format(DATE_CN_FMT),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$count 次" + if (peak != null) " · 最高恐慌 $peak" else "",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("›", style = MaterialTheme.typography.titleMedium, color = DisabledC)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordRow(record: MoodRecord, onClick: () -> Unit) {
    val hasEmotions = record.emotions != null && !record.emotions.isEmpty
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tap(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.timestamp.logicalTimeString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (record.timestamp.toLocalDateTime().hour < DAY_START_HOUR) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "实际 ${record.timestamp.toTimeString()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGray
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (hasEmotions) {
                    SmallTag("二级情绪")
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (record.outcome in 1..3) {
                    SmallTag(OUTCOME_LABELS[record.outcome] ?: "")
                } else if (record.panic >= 8) {
                    SmallTag("还没答后来怎么样了", tint = DangerRed)
                }
                if (record.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    SmallTag("附件 ${record.attachments.size}")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ValueChip("想念TA", record.miss, MissColor)
                ValueChip("想联系", record.contact, ContactColor)
                ValueChip(ConfirmLabel.name, record.confirm, ConfirmColor)
                ValueChip("恐慌", record.panic, PanicColor)
            }
            if (record.diary.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = record.diary.take(60) + if (record.diary.length > 60) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun SmallTag(text: String, tint: Color = PrimaryPurple) {
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun ValueChip(name: String, value: Int, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, style = MaterialTheme.typography.labelSmall, color = color)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                value.answerText(),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordDetailDialog(
    record: MoodRecord,
    attachments: List<String>,
    isRecording: Boolean,
    playingPath: String?,
    onTogglePlay: (String) -> Unit,
    onPickMedia: () -> Unit,
    onToggleRecording: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onOpenAttachment: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit,
    onDelete: () -> Unit
) {
    var text by remember(record.id) { mutableStateOf(record.diary) }
    var confirmDelete by remember(record.id) { mutableStateOf(false) }
    val emotions = record.emotions

    if (confirmDelete) {
        // 带一行预览：看不见内容的确认，等于闭着眼签字。
        ConfirmDeleteDialog(
            preview = record.timestamp.logicalTimeString() +
                (if (record.diary.isNotBlank()) "　" + record.diary.take(60) else "　（当时没写字）"),
            tail = "连这里的日记和附件一起删，无法恢复。",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    record.timestamp.logicalDate().format(DATE_CN_FMT) + " " +
                        record.timestamp.logicalTimeString()
                )
                if (record.timestamp.toLocalDateTime().hour < DAY_START_HOUR) {
                    Text(
                        "实际 ${record.timestamp.toLocalDateTime().format(DATE_CN_FMT)} " +
                            record.timestamp.toTimeString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGray
                    )
                }
            }
        },
        text = {
            Column {
                // 竖排四行，顺序与录入时一致。两行两列读起来顺序是错的。
                listOf(
                    "想念TA" to (record.miss to MissColor),
                    "想联系" to (record.contact to ContactColor),
                    ConfirmLabel.name to (record.confirm to ConfirmColor),
                    "恐慌" to (record.panic to PanicColor)
                ).forEach { (name, pair) ->
                    val (value, color) = pair
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(60.dp)
                        )
                        if (value < 0) {
                            Text(
                                "未答 · 这一栏当时没填",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGray,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { (value / 10f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = color,
                                trackColor = color.copy(alpha = 0.15f)
                            )
                        }
                        Text(
                            value.answerText(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (value < 0) TextGray else color,
                            modifier = Modifier.width(24.dp)
                        )
                    }
                }

                if (emotions != null && !emotions.isEmpty) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "当时的二级情绪",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EMOTION_FIELDS.forEachIndexed { index, (name, getter) ->
                            // 没答的那一支不列：把它列出来，等于替他说了一句「当时这一支是 0 分」。
                            // 只列这次真的有的那几支。
                            getter(emotions)?.let { ValueChip(name, it, EmotionColors[index]) }
                        }
                    }
                }

                if (record.customEmotions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        record.customEmotions.forEachIndexed { index, (name, value) ->
                            ValueChip(
                                name,
                                value,
                                EmotionColors[(EMOTION_FIELDS.size + index) % EmotionColors.size]
                            )
                        }
                    }
                }

                if (record.outcome in 1..3) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "这一次的出口",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SmallTag(OUTCOME_LABELS[record.outcome] ?: "")
                        record.toOutcomeMinutes?.let { minutes ->
                            SmallTag(if (minutes < 1) "不到 1 分钟" else "从慌起来到这里 $minutes 分钟")
                        }
                        record.toSafeMinutes?.let { minutes ->
                            SmallTag(if (minutes < 1) "到安全不到 1 分钟" else "从求助到安全 $minutes 分钟")
                        }
                    }
                }
                if (record.impulse != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "冲动等级：${IMPULSE_LABELS.getOrNull(record.impulse) ?: "未填"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "日记（可选，最多 2000 字）",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= DIARY_MAX) text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    placeholder = { Text("现在心里在想什么…") },
                    supportingText = { Text("${text.length} / $DIARY_MAX") },
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "附件（图片 / 视频 / 语音，存在本机）",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onPickMedia) { Text("添加图片/视频") }
                    TextButton(onClick = onToggleRecording) {
                        Text(if (isRecording) "停止录音" else "录制语音")
                    }
                }
                if (isRecording) {
                    Text(
                        "正在录音…说完点上面的「停止录音」",
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed
                    )
                }
                if (attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        attachments.forEach { path ->
                            AttachmentTile(
                                path = path,
                                playing = playingPath == path,
                                onClick = { onOpenAttachment(path) },
                                onPlay = { onTogglePlay(path) },
                                onRemove = { onRemoveAttachment(path) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { Haptics.tap(); onSave(text, attachments) }) { Text("保存", color = PrimaryPurple) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { Haptics.tap(); confirmDelete = true }) { Text("删除", color = TextGray) }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
private fun AttachmentTile(
    path: String,
    playing: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    val bitmap = remember(path) { AttachmentStore.thumbnail(path) }
    val isAudio = AttachmentStore.isAudio(path)

    Box(modifier = Modifier.size(76.dp)) {
        Card(
            modifier = Modifier
                .size(70.dp)
                .tap { if (isAudio) onPlay() else onClick() },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = ChipBg)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (isAudio) {
                    Text(
                        text = if (playing) "停止" else "语音",
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimaryPurple,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "视频",
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimaryPurple,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .tap(onClick = onRemove),
            color = Color(0x992B2733),
            shape = CircleShape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("×", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
/**
 * 保存之后那句「要写点什么吗」。
 *
 * 两个出口分工不同：
 *  ·【取消…】是撤销 —— 刚那一笔连记录一起拿掉（保存后立刻弹出的那个场景用它）。
 *  · 点外面 / 返回键只是关掉，记录留着，只是不写字。
 * 删一条记录是破坏性的，不能因为手指滑了一下就发生。
 */
fun DiaryDialog(
    title: String,
    subtitle: String,
    initial: String,
    cancelLabel: String = "取消",
    confirmLabel: String = "保存",
    onCancel: () -> Unit = {},
    onDismiss: () -> Unit = onCancel,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextGray)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= DIARY_MAX) text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    placeholder = { Text("想写点什么就写下来，也可以直接跳过…") },
                    supportingText = { Text("${text.length} / $DIARY_MAX") },
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "图片、视频、语音可以在「情绪 · 全部记录」里点开这条记录后补上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { Haptics.tap(); onSave(text) }) { Text(confirmLabel, color = PrimaryPurple) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(cancelLabel, color = TextGray) }
        }
    )
}
