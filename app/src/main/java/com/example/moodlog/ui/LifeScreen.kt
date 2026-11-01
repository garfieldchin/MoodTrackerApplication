package com.example.moodlog.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.example.moodlog.ui.theme.*
import com.example.moodlog.data.Encounter
import com.example.moodlog.data.LIFE_DIMS
import com.example.moodlog.data.LifeDims
import com.example.moodlog.data.LifeEvent
import com.example.moodlog.data.MoodRepository
import com.example.moodlog.data.PRESET_TAGS
import com.example.moodlog.ui.theme.LifeDimColors
import com.example.moodlog.ui.theme.PrimaryPurple
import com.example.moodlog.ui.theme.TextGray
import com.example.moodlog.util.MD_FMT
import com.example.moodlog.util.logicalDate
import com.example.moodlog.util.logicalTimeString
import com.example.moodlog.util.logicalToday
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 能量的起点：0。
 *
 * 以前是 50 —— 那条线的意思是「你本来就该有 50 分」，于是没做事的一天停在 50，
 * 做事的一天从 50 往上爬，数字里永远带着一个不存在的底。
 * 现在从 0 走：今天没记就是 0，充电往上、消耗往下，看见的就是这一天真实的收支。
 */
private const val ENERGY_BASE = 0

/** 「更早」整块摊开的暗号。它是一个不可能和「2026-09-28」撞上的字符串。 */
private const val ALL_DAYS = "*"

/** 「09-28 周一」—— 光有日期分不清是哪天，加上星期就不用数。 */
private fun dayLabel(day: java.time.LocalDate): String =
    day.format(MD_FMT) + " " +
        day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.CHINA)

/** 折叠行上那一行标题：「10/6 周二」。带斜杠的短写法，一行放得下日子、星期和件数。 */
private fun shortDayLabel(day: java.time.LocalDate): String =
    "${day.monthValue}/${day.dayOfMonth} " +
        day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.CHINA)

/**
 * 生活四个格子。
 *
 * 我身体 = 吃 / 体重 / 运动：最容易答的那一格 —— 答不了「我今天有没有掌控感」的时候，
 *   还答得出「我 07:20 吃了两个鸡蛋」。
 * 我话事 = 能量：这件事是充电还是耗电，你说了算。
 * 我遇见 = 一次情绪从头走到尾的四句话：发生了什么、正在经历什么、需要什么、愿意为自己做什么。
 *
 * 顺序讲道理：先身体，再记账，再走一遍情绪。
 * 六维不在这一页了 —— 它是「一天收一次尾」的东西，归【成为 · 我的感受】。
 * 落点讲手：打开【生活】停在第 2 格【我话事】（initialPage = 1）——
 *   这一天要开十几次，十次有九次是来记「刚才做了什么」，
 *   让最高频的那一格每次都要多滑一下，等于一天多滑十次。
 *
 * 「我做过」（打卡）拿掉了。它一天要点十几次、每次都要先想一个词，
 * 而它给不出任何【我话事】给不出的东西 —— 同样的标签、同样的一刻，
 * 只是少了那句「这让我 +几」。两处都记，最后两处都空着。
 */
private val LIFE_TABS = listOf("我身体", "我话事", "我遇见")
private const val LIFE_ENTRY_PAGE = 1   // 默认落点：我话事

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LifeScreen(repo: MoodRepository, padding: PaddingValues) {
    val pager = rememberPagerState(initialPage = LIFE_ENTRY_PAGE, pageCount = { LIFE_TABS.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        SegmentedBar(
            items = LIFE_TABS,
            selected = pager.currentPage,
            onSelect = { scope.launch { pager.animateScrollToPage(it) } }
        )
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> BodySection(repo)
                1 -> DidSection(repo)
                else -> MeetSection(repo)
            }
        }
    }
}

// ---------------------------------------------------------------- 我话事（能量）

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DidSection(repo: MoodRepository) {
    val ctx = LocalContext.current
    val selected = remember { mutableStateListOf<String>() }
    var change by rememberSaveable { mutableFloatStateOf(0f) }
    var note by rememberSaveable { mutableStateOf("") }
    var newTag by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var managing by rememberSaveable { mutableStateOf(false) }
    var openDay by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<LifeEvent?>(null) }
    // 标签按下去那一下的回音。记着「哪一个被按了」——
    // 整排一起亮就成了通知，只有被按的那一个该亮。
    var tagFlash by remember { mutableStateOf("" to 0) }
    // 「记下这件事」按住时缩一点：按到了没有，眼睛也要看得见。
    val saveInteraction = remember { MutableInteractionSource() }
    val savePressed by saveInteraction.collectIsPressedAsState()
    var pickedDay by remember { mutableStateOf<java.time.LocalDate?>(null) }
    // 删除先问一句。删掉的一条找不回来，所以确认框里带着那一条的原文。
    var pendingDelete by remember { mutableStateOf<LifeEvent?>(null) }
    // 能量条那一下的回音：整块染自己的颜色，0.4 秒退干净。
    val rulerPulse = rememberPulse()
    val rulerLit = rulerPulse.alpha(360)
    val energyColor = if (change.roundToInt() < 0) LossColor else GainColor

    val today = logicalToday()
    val hidden = repo.hiddenTags
    val customTags = repo.tags.map { it.name }.filter { it !in hidden }
    // 倒序：自己加的排在前面（最新加的排最前），预设垫在后面。
    // 标签变多以后，常用的那几个不该沉在最底下要划半天。
    val allTags = (customTags.asReversed() + PRESET_TAGS).filter { it !in hidden }.distinct()

    // 一次分组：今天、最近六天、日历都从这里取，不用按天把整张表翻七遍。
    val byDay = repo.events.groupBy { it.timestamp.logicalDate() }
    val todayEvents = byDay[today].orEmpty()
    val todayTotal = ENERGY_BASE + todayEvents.sumOf { it.delta }   // 起点 0，所以它就是今天的净收支

    // 历史只铺今天；往前 6 天收成一行，点一下才摊开；整个月头的日子交给下面那张日历。
    // 和「全部记录」同一个规矩：铺多少是显示问题，不是删除 —— 折叠不会让任何一条找不到。
    val recentDays = (1..6).map { today.minusDays(it.toLong()) }
        .mapNotNull { d -> byDay[d]?.let { d to it } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 键盘掀起来的时候，底部留出输入法那一块：正在填的那个框不会被压在下面。
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        Text(
            "这件事是在给你充电，还是在耗你。你心里那个数就是这个，不用回想做之前是几分。",
            style = MaterialTheme.typography.bodySmall,
            color = TextGray
        )
        Spacer(modifier = Modifier.height(10.dp))

        EnergyBar(total = todayTotal, count = todayEvents.size)

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "这件事",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (managing) "完成" else "管理",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryPurple,
                        modifier = Modifier.tap { managing = !managing }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(if (managing) 13.dp else 4.dp),
                    verticalArrangement = Arrangement.spacedBy(if (managing) 15.dp else 2.dp)
                ) {
                    allTags.forEach { tag ->
                        TagChip(
                            text = tag,
                            selected = tag in selected,
                            deletable = managing,
                            // 选中和取消都闪一下：手指落下去了，屏幕上要有回音。
                            flashKey = if (tagFlash.first == tag) tagFlash.second else null,
                            flashColor = PrimaryPurple,
                            onClick = {
                                tagFlash = tag to (tagFlash.second + 1)
                                if (managing) {
                                    repo.deleteTag(tag)
                                    if (tag in selected) selected.remove(tag)
                                } else if (tag in selected) selected.remove(tag)
                                else selected.add(tag)
                            }
                        )
                    }
                    if (!managing) TagChip(text = "+ 新标签", selected = false, onClick = { adding = true })
                }
                if (managing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "点 × 把这个标签从这一排里拿掉。已经记过的事不受影响 —— 它照旧写着原来那个词。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "恢复预设标签",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryPurple,
                        modifier = Modifier
                            .tap { repo.restorePresetTags() }
                            .padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                // 动一下这根条子，这一整块跟着亮一下：手指落下去了，屏幕上有回音。
                // 尺子自己会闪（圆点涨大、轨道染色），这里再补一层 —— 两端的字顶一下、整块描一道边。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EnergyEndLabel("消耗", LossColor, rulerLit)
                    EnergyEndLabel("充电", GainColor, rulerLit)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(blend(Color.Transparent, energyColor, 0.16f * rulerLit))
                        .border(
                            1.5.dp,
                            energyColor.copy(alpha = 0.55f * rulerLit),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 2.dp)
                ) {
                    SignedRuler(
                        value = change,
                        gainColor = GainColor,
                        lossColor = LossColor,
                        onPick = { change = it; rulerPulse.bump() }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "不用回想「做之前是几分」。你心里那个数就是这个。点中间的 0 表示没变化。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .scrollIntoViewOnFocus(),
                    placeholder = { Text("一句备注（可选）") },
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                            Haptics.tap()
                        if (selected.isEmpty()) {
                            Toast.makeText(ctx, "先选一个标签吧", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        repo.insertEvent(
                            LifeEvent(
                                tags = selected.toList(),
                                // 量轴从 0 起：这一件之前是 0，之后就是它自己的正负。
                                energyBefore = ENERGY_BASE,
                                energyAfter = ENERGY_BASE + change.roundToInt(),
                                note = note.trim()
                            )
                        )
                        selected.clear()
                        note = ""
                        change = 0f
                        // 震一下 + 整屏染一层很淡的绿：记下了这件事要有个落地的手感。
                        // 存好不弹 Toast：整屏那一下已经在说了，两处都说就成噪音了。
                        buzz(ctx)
                        SaveFeedback.fire(LifeDimColors[0], "记下了 ✓")
                    },
                    interactionSource = saveInteraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .pressScale(savePressed, 0.97f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) {
                    Text("记下这件事", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (todayEvents.isNotEmpty()) {
            Row {
                Text(
                    "今天",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${todayEvents.size} 件 · 合计 ${signed(todayEvents.sumOf { it.delta })}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            todayEvents.forEach { e ->
                EventRow(
                    e = e,
                    onClick = { editing = e },
                    onDelete = { pendingDelete = e }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            Text(
                "今天还没有记下的事。没关系。",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }

        if (recentDays.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "最近七天",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextGray,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (openDay == ALL_DAYS) "全部收起" else "全部摊开",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryPurple,
                    modifier = Modifier.tap {
                        openDay = if (openDay == ALL_DAYS) null else ALL_DAYS
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            recentDays.forEach { (day, list) ->
                val key = day.toString()
                val open = openDay == key || openDay == ALL_DAYS
                // 收着的时候只留「哪天 · 几件 · 合计多少」，够一眼看出那天是往上还是往下；
                // 要看具体的那几件，再点开 —— 不点开不占地方。
                FoldedDayRow(
                    label = shortDayLabel(day),
                    count = "${list.size} 件",
                    tail = "合计 ${signed(list.sumOf { it.delta })}",
                    expanded = open,
                    onClick = { openDay = if (open && openDay == key) null else key }
                )
                if (open) {
                    Spacer(modifier = Modifier.height(6.dp))
                    list.forEach { e ->
                        EventRow(
                            e = e,
                            onClick = { editing = e },
                            onDelete = { pendingDelete = e }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // 更早的日子不排成长条、也不再藏进「月历 ›」里 —— 日历就铺在下面，
        // 以前那是三层：点「月历」出一层、点一天再出一层。每层都在问「还要不要往下翻」，
        // 而多数时候你只想知道那天做了什么。现在七十几行换成一张月历，点了就弹。
        if (byDay.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            PastMonthBoard(
                energyOf = { day -> byDay[day]?.sumOf { it.delta } },
                onPick = { day -> pickedDay = day },
                asEnergy = true
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    pendingDelete?.let { e ->
        ConfirmDeleteDialog(
            preview = e.timestamp.logicalTimeString() + "　" +
                e.tags.joinToString("、").ifBlank { "（没写标签）" } +
                (if (e.note.isNotBlank()) "　" + e.note else "") +
                "　" + signed(e.delta),
            onConfirm = { repo.deleteEvent(e.id); pendingDelete = null; editing = null },
            onDismiss = { pendingDelete = null }
        )
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("加一个自己的标签") },
            text = {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    singleLine = true,
                    placeholder = { Text("比如：遛狗 / 修东西 / 做饭") },
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                        Haptics.tap()
                    val t = newTag.trim()
                    if (t.isNotBlank()) {
                        repo.addTag(t)
                        if (t !in selected) selected.add(t)
                    }
                    newTag = ""
                    adding = false
                }) { Text("加上", color = PrimaryPurple) }
            },
            dismissButton = { TextButton(onClick = { Haptics.tap(); adding = false }) { Text("取消") } }
        )
    }

    // 点一条记录 → 改那句话。写的时候手抖、词不对、数值后来想改，都从这里进。
    editing?.let { e ->
        EditEventDialog(
            e = e,
            onSave = { updated ->
                repo.updateEvent(updated)
                editing = null
                buzz(ctx)
                SaveFeedback.fire(LifeDimColors[0], "改好了 ✓")
            },
            onDelete = {
                pendingDelete = e
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    // 月历上点出来的那一天。
    pickedDay?.let { day ->
        val list = repo.events.filter { it.timestamp.logicalDate() == day }
        if (list.isEmpty()) {
            pickedDay = null
        } else {
            DayEventsDialog(
                day = day,
                list = list,
                onEdit = { e -> editing = e },
                onDelete = { e -> pendingDelete = e },
                onDismiss = { pickedDay = null }
            )
        }
    }
}

// ---------------------------------------------------------------- 我遇见

/**
 * 一次情绪，问自己四句话。
 *
 * 为什么不用一个大文本框让人随便写：最难的时候人写不出一段话，
 * 但答得出「刚才看到了一张旧照片」。四问把那一大段拆成了四个能张口就答的小问题，
 * 顺序上也是先事实、再感受、然后才轮到「怎么办」—— 人在水里的时候答不了怎么办。
 *
 * 也不打分：这四句的价值不在强度，在于它们是**你自己说的话**。
 * 以后翻回来，看到的是那一天的原话，不是一串数字。
 */
@Composable
private fun MeetSection(repo: MoodRepository) {
    val ctx = LocalContext.current
    var happened by rememberSaveable { mutableStateOf("") }
    var feeling by rememberSaveable { mutableStateOf("") }
    var need by rememberSaveable { mutableStateOf("") }
    var doNext by rememberSaveable { mutableStateOf("") }
    val savedTick = remember { mutableStateOf(0) }

    var openDay by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Encounter?>(null) }
    var pickedDay by remember { mutableStateOf<java.time.LocalDate?>(null) }
    // 我遇见的一笔：那四句是你自己的话，删了就没有了，所以也要先问一句。
    var pendingDelete by remember { mutableStateOf<Encounter?>(null) }

    val today = logicalToday()
    val byDay = repo.encounters.groupBy { it.timestamp.logicalDate() }
    val todayList = byDay[today].orEmpty()
    val recentDays = (1..6).map { today.minusDays(it.toLong()) }
        .mapNotNull { d -> byDay[d]?.let { d to it } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        Text(
            "心里那一股上来的时候，不用先想明白。照着这四个问题说一遍就行 —— 想答几个答几个，空着的不会催你。",
            style = MaterialTheme.typography.bodySmall,
            color = TextGray,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                MeetField(
                    title = "1 · 刚才发生了什么",
                    hint = "比如：看到一张旧照片，想起以前一起生活的日子。",
                    value = happened,
                    onChange = { happened = it }
                )
                MeetField(
                    title = "2 · 我现在正在经历什么",
                    hint = "比如：想念、委屈、恐慌、自责，或者几种感受同时出现。",
                    value = feeling,
                    onChange = { feeling = it }
                )
                MeetField(
                    title = "3 · 此刻的我需要什么",
                    hint = "比如：有人说一句「我在」、一个小时的安静、先别做决定。",
                    value = need,
                    onChange = { need = it }
                )
                MeetField(
                    title = "4 · 接下来，我愿意为自己做什么",
                    hint = "只需要一件小事，不必解决整个人生。",
                    value = doNext,
                    onChange = { doNext = it }
                )

                Spacer(modifier = Modifier.height(12.dp))
                SaveBar(
                    label = "记下这一笔",
                    savedLabel = "记下了",
                    savedCount = savedTick.value,
                    onClick = {
                        val draft = Encounter(
                            happened = happened.trim(),
                            feeling = feeling.trim(),
                            need = need.trim(),
                            doNext = doNext.trim()
                        )
                        if (draft.isBlank) {
                            Toast.makeText(ctx, "四句里写一句就好", Toast.LENGTH_SHORT).show()
                            false
                        } else {
                            repo.insertEncounter(draft)
                            happened = ""; feeling = ""; need = ""; doNext = ""
                            savedTick.value++
                            buzz(ctx)
                            SaveFeedback.fire(LifeDimColors[3], "记下了 ✓")
                            true
                        }
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "写完就清空。它不是要留给你改错别字的稿子 —— 写完那一刻，它已经起了作用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (todayList.isNotEmpty()) {
            Row {
                Text(
                    "今天",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text("${todayList.size} 笔", style = MaterialTheme.typography.bodySmall, color = TextGray)
            }
            Spacer(modifier = Modifier.height(8.dp))
            todayList.forEach { e ->
                EncounterRow(e, onClick = { editing = e }, onDelete = { pendingDelete = e })
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            Text(
                "今天还没有记下的这一笔。什么时候想说，再来。",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }

        if (recentDays.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "最近七天",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextGray,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (openDay == ALL_DAYS) "全部收起" else "全部摊开",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryPurple,
                    modifier = Modifier.tap {
                        openDay = if (openDay == ALL_DAYS) null else ALL_DAYS
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            recentDays.forEach { (day, list) ->
                val key = day.toString()
                val open = openDay == key || openDay == ALL_DAYS
                FoldedDayRow(
                    label = shortDayLabel(day),
                    count = "${list.size} 笔",
                    tail = "",
                    expanded = open,
                    onClick = { openDay = if (open && openDay == key) null else key }
                )
                if (open) {
                    Spacer(modifier = Modifier.height(6.dp))
                    list.forEach { e ->
                        EncounterRow(
                            e,
                            onClick = { editing = e },
                            onDelete = { pendingDelete = e }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        if (byDay.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            PastMonthBoard(
                energyOf = { day -> byDay[day]?.size },
                onPick = { day -> pickedDay = day },
                asEnergy = false
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    pendingDelete?.let { e ->
        ConfirmDeleteDialog(
            preview = e.timestamp.logicalTimeString() + "　" +
                listOf(e.happened, e.feeling, e.need, e.doNext)
                    .filter { it.isNotBlank() }.joinToString("｜").ifBlank { "（这一笔是空的）" },
            onConfirm = { repo.deleteEncounter(e.id); pendingDelete = null; editing = null },
            onDismiss = { pendingDelete = null }
        )
    }

    editing?.let { e ->
        EditEncounterDialog(
            e = e,
            onSave = { updated ->
                repo.updateEncounter(updated)
                editing = null
                buzz(ctx)
                SaveFeedback.fire(LifeDimColors[3], "改好了 ✓")
            },
            onDelete = { pendingDelete = e; editing = null },
            onDismiss = { editing = null }
        )
    }

    pickedDay?.let { day ->
        val list = repo.encounters.filter { it.timestamp.logicalDate() == day }
        if (list.isEmpty()) {
            pickedDay = null
        } else {
            DayEncountersDialog(
                day = day,
                list = list,
                onEdit = { editing = it },
                onDelete = { pendingDelete = it },
                onDismiss = { pickedDay = null }
            )
        }
    }
}

/** 我遇见的一问。标题 + 例子 + 一个能长大的输入框。 */
@Composable
private fun MeetField(
    title: String,
    hint: String,
    value: String,
    onChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 7.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = SubText
        )
        // 第二次登场的四个框（改的时候）不需要再把例子说一遍，所以 hint 可以是空的。
        if (hint.isNotBlank()) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                lineHeight = 18.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                // 键盘上来的时候把这个框顶到视野里 —— 不然打字是在盲打。
                .scrollIntoViewOnFocus(),
            shape = RoundedCornerShape(12.dp),
            minLines = 2
        )
    }
}

/**
 * 我遇见的一条记录。列表上只露第二问那句话 ——
 * 它是四句里最能代表「这一笔在说什么」的一句；点开才看到全部。
 */
@Composable
private fun EncounterRow(e: Encounter, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().tap(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    e.oneLine(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Text(
                    e.timestamp.logicalTimeString() +
                        if (e.doNext.isNotBlank()) " · 愿意做的：${e.doNext.trim()}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    maxLines = 1
                )
            }
            Text(
                "删除",
                style = MaterialTheme.typography.labelSmall,
                color = TextGray,
                modifier = Modifier.tap(onClick = onDelete)
            )
        }
    }
}

@Composable
private fun EditEncounterDialog(
    e: Encounter,
    onSave: (Encounter) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var happened by remember { mutableStateOf(e.happened) }
    var feeling by remember { mutableStateOf(e.feeling) }
    var need by remember { mutableStateOf(e.need) }
    var doNext by remember { mutableStateOf(e.doNext) }
    var ts by remember { mutableStateOf(e.timestamp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("改这一笔", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // 时刻也能改 —— 记录可能不及时；量程 06:00–29:59，拖不过现在。
                DateTimeField(ts = ts, onTs = { ts = it })
                Spacer(modifier = Modifier.height(10.dp))
                MeetField("1 · 刚才发生了什么", "", happened) { happened = it }
                MeetField("2 · 我现在正在经历什么", "", feeling) { feeling = it }
                MeetField("3 · 此刻的我需要什么", "", need) { need = it }
                MeetField("4 · 接下来，我愿意为自己做什么", "", doNext) { doNext = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                    Haptics.tap()
                onSave(
                    e.copy(
                        timestamp = ts,
                        happened = happened.trim(),
                        feeling = feeling.trim(),
                        need = need.trim(),
                        doNext = doNext.trim()
                    )
                )
            }) { Text("存下", color = PrimaryPurple) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除", color = LossColor) }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
private fun DayEncountersDialog(
    day: java.time.LocalDate,
    list: List<Encounter>,
    onEdit: (Encounter) -> Unit,
    onDelete: (Encounter) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${dayLabel(day)} · ${list.size} 笔", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                list.forEach { e ->
                    EncounterRow(e, onClick = { onEdit(e) }, onDelete = { onDelete(e) })
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭", color = PrimaryPurple) } }
    )
}

// ---------------------------------------------------------------- 共用组件

private val GainColor: Color get() = GainGreen
private val LossColor: Color get() = DangerRed

/**
 * 改一条已经记下的事。
 *
 * 标签那一行是纯文本的：你看到的就是它存的样子，想加一个词、换一个词，直接在这行改。
 * 数值给了加减两个键 —— 当天来回拖一根尺子改一个数，比重新想数字容易。
 */
@Composable
private fun EditEventDialog(
    e: LifeEvent,
    onSave: (LifeEvent) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var tagsText by remember { mutableStateOf(e.tags.joinToString(" · ")) }
    var note by remember { mutableStateOf(e.note) }
    var delta by remember { mutableIntStateOf(e.delta) }
    var ts by remember { mutableStateOf(e.timestamp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("改这一条", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // 时刻也能改 —— 记录可能不及时；量程 06:00–29:59，拖不过现在。
                DateTimeField(ts = ts, onTs = { ts = it })
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "标签",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    modifier = Modifier.fillMaxWidth().scrollIntoViewOnFocus(),
                    placeholder = { Text("运动 · 和朋友吃饭") },
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "备注",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth().scrollIntoViewOnFocus(),
                    placeholder = { Text("一句备注（可选）") },
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "这件事让我",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Stepper("−") { if (delta > -10) delta-- }
                    Text(
                        signed(delta),
                        fontWeight = FontWeight.Bold,
                        color = if (delta >= 0) GainColor else LossColor,
                        modifier = Modifier.width(36.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Stepper("+") { if (delta < 10) delta++ }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "改掉的是当时那句话，不是那件事本身。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                    Haptics.tap()
                val tags = tagsText.split("·", "、", ",", "，", "\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                onSave(
                    e.copy(
                        timestamp = ts,
                        tags = tags,
                        energyBefore = 0,
                        energyAfter = delta,
                        note = note.trim()
                    )
                )
            }) { Text("存下", color = PrimaryPurple) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除", color = LossColor) }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
private fun Stepper(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(ChipBg)
            .tap(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, fontSize = 18.sp, color = PrimaryPurple, fontWeight = FontWeight.Bold)
    }
}

/** 月历里点出来的那一天，把那天记的都列出来。 */
@Composable
private fun DayEventsDialog(
    day: java.time.LocalDate,
    list: List<LifeEvent>,
    onEdit: (LifeEvent) -> Unit,
    onDelete: (LifeEvent) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "${dayLabel(day)} · ${list.size} 件 · 合计 ${signed(list.sumOf { it.delta })}",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                list.forEach { e ->
                    EventRow(e, onClick = { onEdit(e) }, onDelete = { onDelete(e) })
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭", color = PrimaryPurple) } }
    )
}

/**
 * 当月日历。直接铺在页面上，不藏在二级的东西后面。
 *
 * 上一版是「更早 · N 天有记录 ›」，点了才出月历，再点一天才出那天的事 —— 三层。
 * 三层里每一层都在问你要不要继续往下翻，而多数时候你只想知道「那天做了什么」。
 *
 * 记过事的那天是紫的，日期下面那一小条是当天合计的能量：
 * 绿的是充到电了，红的是被耗了，越长代表那天起伏越大 ——
 * 单向的，都从左边起，不用先想明白「0 在哪儿」。
 *
 * 以前那一条是件数。「3 件」说明不了任何事 —— 三件小事加起来可能是 −8，一件也可能是 +6。
 *
 * 前后那几天是<b>真实的日期</b>，不是补白的空格：跨月那一段连着看才接得上。数字淡一档、可点。
 * 补到周日为止就收 —— 不把下一整周也摆出来，那几天还没到。
 *
 * 没记的那天是一根<b>短灰条</b>。它说的是「那天没有这个数」，不是「那天缺了什么」——
 * 一格全空会让人以为还没加载，一根灰条只是把那天摆在那儿。未来的日子不画条。
 *
 * 这里不数连续：空白就是空白，不说你漏了什么。
 */
@Composable
internal fun PastMonthBoard(
    energyOf: (java.time.LocalDate) -> Int?,   // null = 那天没记
    onPick: (java.time.LocalDate) -> Unit,
    // 我话事用能量条；我遇见没有正负，还是老样子显示件数。
    asEnergy: Boolean = true
) {
    val today = logicalToday()
    val thisMonth = java.time.YearMonth.from(today)
    var month by remember { mutableStateOf(thisMonth) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "这个月",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .tap { month = month.minusMonths(1) },
                    contentAlignment = Alignment.Center
                ) { Text("‹", fontSize = 17.sp, color = PrimaryPurple) }
                Text(
                    "${month.monthValue} 月",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryPurple,
                    modifier = Modifier.width(46.dp),
                    textAlign = TextAlign.Center
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .tap { if (month < thisMonth) month = month.plusMonths(1) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "›",
                        fontSize = 17.sp,
                        color = if (month < thisMonth) PrimaryPurple else FaintText
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(1, 2, 3, 4, 5, 6, 7).forEach { dow ->
                    Text(
                        java.time.DayOfWeek.of(dow)
                            .getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.CHINA),
                        fontSize = 11.sp,
                        color = TextGray,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val first = month.atDay(1)
            val lead = first.dayOfWeek.value - 1
            // 前后那几天是<b>真实的日期</b>，不是补白的空格子：10 月 1 号是周四，
            // 行首就摆着 9 月 28、29、30；月末同理，尾巴接下月初。
            // 只是画面权重降下来（数字淡一档）—— 跨月那一段是这样才接得上的。
            val head = (lead downTo 1).map { first.minusDays(it.toLong()) }
            val body = (1..month.lengthOfMonth()).map { month.atDay(it) }
            val cells = head + body
            // 只补到这一行的周日为止：这个月最后一天是周六，尾巴上就只多一个下月 1 号。
            // 不把下一整周也铺出来 —— 那几天还没到，摆在那儿像是已经过完了。
            val tail = (1..((7 - cells.size % 7) % 7)).map {
                month.plusMonths(1).atDay(1).plusDays(it.toLong() - 1)
            }
            // 当月合计：点开一个月就知道这个月是净充还是净耗，不用自己在心里加一遍。
            val monthTotal = (1..month.lengthOfMonth())
                .map { month.atDay(it) }
                .filter { it <= today }
                .sumOf { energyOf(it) ?: 0 }

            (cells + tail).chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val future = date > today
                            val inMonth = java.time.YearMonth.from(date) == month
                            val sum = energyOf(date)
                            val marked = sum != null
                            val isToday = date == today
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            isToday -> PrimaryPurple.copy(alpha = 0.10f)
                                            marked && inMonth -> PrimaryPurple.copy(alpha = 0.16f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .then(
                                        if (isToday) Modifier.border(
                                            1.dp, PrimaryPurple.copy(alpha = 0.45f),
                                            RoundedCornerShape(12.dp)
                                        ) else Modifier
                                    )
                                    .then(
                                        when {
                                            // 还没到的日子不画条也不弹东西 —— 那里没有东西可看。
                                            future -> Modifier
                                            // 邻月的日子可点：先翻到那个月，再把那天的事弹出来。
                                            // 跨月那一段（比如月底接下月初）是这样才连得上的。
                                            !inMonth -> Modifier.tap {
                                                month = java.time.YearMonth.from(date)
                                                if (marked) onPick(date)
                                            }
                                            marked -> Modifier.tap { onPick(date) }
                                            // 当月没记的那天不给反应：弹出来是空的，说明不了什么。
                                            else -> Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${date.dayOfMonth}",
                                        fontSize = 12.5.sp,
                                        lineHeight = 14.sp,
                                        fontWeight = if (marked || isToday) FontWeight.Bold
                                        else FontWeight.Normal,
                                        color = when {
                                            future -> FaintText
                                            !inMonth -> DisabledC      // 邻月：淡一档
                                            marked -> MarkText
                                            else -> SubText
                                        }
                                    )
                                    // 三种情况都占同一个 5dp 的位置，行与行之间不会一高一低。
                                    when {
                                        future -> Spacer(modifier = Modifier.height(5.dp))
                                        marked && !asEnergy -> {
                                            Text(
                                                "$sum",
                                                fontSize = 8.5.sp,
                                                lineHeight = 10.sp,
                                                color = PrimaryPurple,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        else -> {
                                            // 单向：一律从左端往右长，长度是绝对值（满格 ±15）。
                                            val v = sum ?: 0
                                            val abs = if (v < 0) -v else v
                                            val frac = (abs / 15f).coerceIn(0.12f, 1f)
                                            val bar = when {
                                                !marked -> DisabledC   // 没记：灰条，不是一条空的槽
                                                v > 0 -> GainColor
                                                v < 0 -> LossColor
                                                else -> DisabledC
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .width(26.dp)
                                                    .height(5.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(if (marked) 26.dp * frac else 26.dp * 0.18f)
                                                        .height(5.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(bar)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                (if (asEnergy) {
                    "紫的一天是那天记过事。条子越长，那天充到电（绿）或被耗（红）得越多。" +
                        "点一下，那天的事就弹出来。" +
                        if (monthTotal != 0) "　这个月合计 ${signed(monthTotal)}。" else ""
                } else {
                    "紫的一天是那天写过，下面的数字是件数。点一下，那天写的就弹出来。"
                }) + "　灰条是那天没记；数字淡的那几天是上个月或下个月，点一下就翻过去。" +
                    "还没到的日子不画条。",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * 活动标签。比通用的 ChoiceChip 高一档、宽一档 ——
 * 原来那版字贴着边框，六个挤在一起的时候分不清是一排还是两排。
 *
 * flashKey 每点一次就变一次：它会让这个标签亮一下，然后在 1.2 秒里慢慢褪回去。
 * 打卡这种地方一天点十几次，没有反馈就等于不确定有没有记上，人会忍不住再点一次。
 * 褪回去是必须的 —— 常亮就成了「选中态」，而这里没有选中态，点一下就是记一条。
 */
@Composable
private fun TagChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    deletable: Boolean = false,
    flashKey: Any? = null,
    flashColor: Color = GainColor
) {
    val glow = remember { Animatable(0f) }
    LaunchedEffect(flashKey) {
        if (flashKey != null) {
            glow.snapTo(1f)
            glow.animateTo(0f, tween(durationMillis = 1200, easing = LinearOutSlowInEasing))
        }
    }
    val base = if (selected) PrimaryPurple.copy(alpha = 0.14f) else ChipBg
    val lit = flashColor.copy(alpha = 0.30f)
    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = 1f + 0.09f * glow.value
            scaleY = 1f + 0.09f * glow.value
        }
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(9.dp),
            color = lerp(base, lit, glow.value),
            border = when {
                selected -> BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.45f))
                deletable -> BorderStroke(1.dp, CardBorder)
                glow.value > 0.01f -> BorderStroke(1.dp, flashColor.copy(alpha = 0.35f + 0.3f * glow.value))
                else -> null
            }
        ) {
            Text(
                text = text,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                color = if (selected) PrimaryPurple else TextGray,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
        // 删除态的那个角标。管理模式下每个都有，包括预设的六个。
        if (deletable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(LossColor)
                    // 角标多半露在卡片外面，得自己接一下点击，不能指望底下的 Surface 收到。
                    .tap(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Text("×", color = Color.White, fontSize = 12.sp, lineHeight = 12.sp)
            }
        }
    }
}

/**
 * 今天的能量。从 0 走起，中间那道竖线就是 0。
 *
 * 往右是充电、往左是消耗 —— 一头一尾的量轴自己会跟着当天最大的那个数长，
 * 不封顶：一旦把右边钉死在 100，撑过傍晚之后就再也看不见起伏。
 * 第二天回到 0，今天不用替昨天还债。
 */
/**
 * 能量条两端的字：左边「消耗」，右边「充电」。
 *
 * 就是两个正常的词 —— 不做药丸、不放大、不写 −10 / +10：
 * 那两个数字尺子下面自己刻着，再说一遍是重复。
 * 颜色是它自己的（消耗红、充电绿），动条子的时候跟着顶一下。
 */
@Composable
private fun EnergyEndLabel(text: String, color: Color, lit: Float) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.zoom(1f + 0.06f * lit)
    )
}

@Composable
private fun EnergyBar(total: Int, count: Int) {
    val abs = kotlin.math.abs(total)
    // 量轴：至少 ±20，超出就按 10 的整倍数往上长。数字永远是整的，轴也跟着它走。
    val axis = maxOf(20, ((abs + 9) / 10) * 10)
    val fill = 0.5f * (abs.toFloat() / axis).coerceIn(0f, 1f)
    val color = if (total > 0) GainColor else if (total < 0) LossColor else DisabledC

    // 数字一变，整条闪一下：刚才那一笔落进去了，这件事要看得见。
    // 第一下不算 —— 刚打开这一页时它不是「刚记了一件」，只是把旧账读出来了。
    val pulse = rememberPulse()
    val lit = pulse.alpha()
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(total) {
        if (first) first = false else pulse.bump()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .zoom(1f - 0.012f * lit),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = blend(CardBg, color, 0.18f * lit)
        ),
        border = if (lit > 0.01f) {
            BorderStroke(1.5.dp, color.copy(alpha = 0.5f * lit))
        } else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "今天的能量",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text("$count 件", style = MaterialTheme.typography.bodySmall, color = TextGray)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    signed(total),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    when {
                        total > 0 -> "今天净充电 $total"
                        total < 0 -> "今天净消耗 ${-total}"
                        else -> "今天还是 0"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(11.dp)) {
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ChipBg)
                ) {
                    if (total >= 0) {
                        Spacer(modifier = Modifier.weight(0.5f))
                        if (fill > 0f) Box(
                            modifier = Modifier
                                .weight(fill)
                                .height(11.dp)
                                .background(color)
                        )
                        val rest = 0.5f - fill
                        if (rest > 0f) Spacer(modifier = Modifier.weight(rest))
                    } else {
                        val rest = 0.5f - fill
                        if (rest > 0f) Spacer(modifier = Modifier.weight(rest))
                        if (fill > 0f) Box(
                            modifier = Modifier
                                .weight(fill)
                                .height(11.dp)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.weight(0.5f))
                    }
                }
                // 中间那道竖线就是 0。往右充电、往左消耗，它得一直在那儿。
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(1.5.dp)
                        .height(11.dp)
                        .background(FaintLine)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("−$axis", style = MaterialTheme.typography.labelSmall, color = TextGray)
                Text("0", style = MaterialTheme.typography.labelSmall, color = TextGray)
                Text("+$axis", style = MaterialTheme.typography.labelSmall, color = TextGray)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "从 0 起算：一件事都没记，它就是 0。往左走的那几天它是负的，" +
                    "那也是真的 —— 不看这条线，等于那天没发生过。",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun FoldedDayRow(
    label: String,
    count: String,
    tail: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(13.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryPurple,
                modifier = Modifier.width(88.dp)
            )
            Text(count, style = MaterialTheme.typography.bodySmall, color = TextGray)
            Spacer(modifier = Modifier.weight(1f))
            Text(tail, style = MaterialTheme.typography.bodySmall, color = TextGray)
            Text(if (expanded) "⌄" else "›", color = DisabledC)
        }
    }
}

/**
 * 一条已记下的事。整块可点 —— 点进去改当时那句话。
 *
 * 「删除」在右边单独一个词，不当整块的默认动作：
 * 一条手指滑过去就删掉的东西，迟早会滑掉一条你本来想留的。
 */
@Composable
private fun EventRow(e: LifeEvent, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tap(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    e.tags.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    e.timestamp.logicalTimeString() +
                        if (e.note.isNotBlank()) " · ${e.note}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
            Surface(
                color = (if (e.delta >= 0) GainColor else LossColor).copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    signed(e.delta),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold,
                    color = if (e.delta >= 0) GainColor else LossColor
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "删除",
                style = MaterialTheme.typography.labelSmall,
                color = TextGray,
                modifier = Modifier.tap(onClick = onDelete)
            )
        }
    }
}

private fun signed(n: Int) = if (n > 0) "+$n" else n.toString()
