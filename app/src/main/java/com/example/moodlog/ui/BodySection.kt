package com.example.moodlog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moodlog.ui.theme.*
import com.example.moodlog.data.MEAL_KINDS
import com.example.moodlog.data.MOVE_PRESETS
import com.example.moodlog.data.Move
import com.example.moodlog.data.Meal
import com.example.moodlog.data.MoodRepository
import com.example.moodlog.data.Weight
import com.example.moodlog.ui.theme.PrimaryPurple
import com.example.moodlog.ui.theme.TextGray
import com.example.moodlog.util.logicalDate
import com.example.moodlog.util.logicalTimeString
import com.example.moodlog.util.logicalToday
import com.example.moodlog.util.toLocalDate
import com.example.moodlog.util.toLocalDateTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val BODY_TABS = listOf("饮食", "体重", "运动")

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val GainColor: Color get() = GainGreen
private val LossColor: Color get() = DangerRed

/**
 * 【我身体】。三个子页：饮食 / 体重 / 运动。
 *
 * 这一格的三条底线，写在这里是因为代码比注释活得久：
 * 1. 不算热量、不定目标体重、不数"漏了几顿"。
 * 2. 体重涨了不弹任何提示 —— 那根线涨上去的时候，最不需要的就是一个再来提醒你一次的角标。
 * 3. 「没吃」是一个中性标签，跟早饭一样是灰的，不是红的。难熬的日子里吃不下是事实，不是错误。
 */
@Composable
fun BodySection(repo: MoodRepository) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxWidth()) {
        SegmentedBar(items = BODY_TABS, selected = tab) { tab = it }
        when (tab) {
            0 -> MealPane(repo)
            1 -> WeightPane(repo)
            else -> MovePane(repo)
        }
    }
}

// ---------------------------------------------------------------- 饮食

@Composable
private fun MealPane(repo: MoodRepository) {
    val ctx = LocalContext.current
    val today = logicalToday()
    var editing by remember { mutableStateOf<Meal?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pickedDay by remember { mutableStateOf<LocalDate?>(null) }
    var openDays by remember { mutableStateOf(setOf<String>()) }
    var pendingDelete by remember { mutableStateOf<Meal?>(null) }

    // 一次分组：今天、最近七天、整月日历都从这里取，不把整张表翻好几遍。
    val byDay = repo.meals.groupBy { it.timestamp.logicalDate() }
    val todayList = byDay[today].orEmpty()
    val recent = (1..6).map { today.minusDays(it.toLong()) }
        .mapNotNull { d -> byDay[d]?.let { d to it } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        Text(
            "吃了什么、什么时候。不记热量，也不打分。",
            style = MaterialTheme.typography.bodySmall,
            color = TextGray
        )

        Spacer(modifier = Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row {
                    Text(
                        "今天吃了什么",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (todayList.isEmpty()) "还没记" else "${todayList.size} 次 · 最早 ${
                            todayList.minOf { it.timestamp }.logicalTimeString()
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }
                if (todayList.isEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "今天还没有记下。没记就不记 —— 这里不数漏了几顿。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    todayList.sortedBy { it.timestamp }.forEach { m ->
                        MealRow(m, onClick = { editing = m })
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "点一条能改：改时间、改内容、换标签、改服药。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = { Haptics.tap(); adding = true },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
        ) { Text("+ 记一顿", fontWeight = FontWeight.SemiBold) }

        if (recent.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "最近七天",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextGray
            )
            Spacer(modifier = Modifier.height(8.dp))
            recent.forEach { (day, list) ->
                val key = day.toString()
                val open = key in openDays
                FoldLine(
                    label = "${day.monthValue}/${day.dayOfMonth} " + weekdayOf(day),
                    count = "${list.size} 次",
                    tail = "最早 ${list.minOf { it.timestamp }.logicalTimeString()}",
                    expanded = open,
                    onClick = {
                        openDays = if (open) openDays - key else openDays + key
                    }
                )
                if (open) {
                    Spacer(modifier = Modifier.height(6.dp))
                    list.sortedBy { it.timestamp }.forEach { m ->
                        MealRow(m, onClick = { editing = m })
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        // 更早的日子不藏进「月历 ›」后面 —— 月历就铺在下面，和【我话事】同一个规矩：
        // 以前要两层才到那天，现在一张月历点了就弹。下面的数字是那天记了几顿/几次。
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

    if (adding || editing != null) {
        MealDialog(
            initial = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { m ->
                if (m.id == 0L) repo.insertMeal(m) else repo.updateMeal(m)
                adding = false; editing = null
                buzz(ctx)
                SaveFeedback.fire(PrimaryPurple, if (m.id == 0L) "记下了 ✓" else "改好了 ✓")
            },
            onDelete = { m -> editing = null; pendingDelete = m }
        )
    }

    pendingDelete?.let { m ->
        ConfirmDeleteDialog(
            preview = "${m.timestamp.logicalTimeString()}　${m.kind}" +
                (if (m.content.isNotBlank()) "　${m.content}" else ""),
            onConfirm = { repo.deleteMeal(m.id); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }

    pickedDay?.let { day ->
        val list = repo.meals.filter { it.timestamp.logicalDate() == day }.sortedBy { it.timestamp }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickedDay = null },
            title = {
                Text(
                    "${day.monthValue} 月 ${day.dayOfMonth} 日 · ${weekdayOf(day)}",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    list.forEach { m ->
                        MealRow(m, onClick = { pickedDay = null; editing = m })
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { Haptics.tap(); pickedDay = null }) { Text("关闭", color = PrimaryPurple) }
            }
        )
    }
}

@Composable
private fun MealRow(m: Meal, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().tap(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (m.content.isBlank()) "（没写内容）" else m.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${m.timestamp.logicalTimeString()} · ${m.kind}" +
                        (if (m.medicine) " · 已服药" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (m.medicine) GainColor else TextGray
                )
            }
            Text("›", fontSize = 17.sp, color = DisabledC)
        }
    }
}

/**
 * 记一顿 / 改一顿。
 *
 * 时间用滚轴挑（见 DateTimeField）：量程 06:00–29:59，和逻辑日同一条缝，
 * 补记昨晚的夜宵把日期往后翻一页就行。日期也能改 —— 以前改旧记录日期是锁死的。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealDialog(
    initial: Meal?,
    onDismiss: () -> Unit,
    onSave: (Meal) -> Unit,
    onDelete: ((Meal) -> Unit)? = null
) {
    val start = initial ?: Meal()
    // 新建时落在现在；改旧记录时落在那条记录自己身上。
    var ts by remember { mutableStateOf(initial?.timestamp ?: System.currentTimeMillis()) }
    var content by remember { mutableStateOf(start.content) }
    var kind by remember { mutableStateOf(start.kind.ifBlank { MEAL_KINDS[0] }) }
    var medicine by remember { mutableStateOf(start.medicine) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "记一顿" else "改这一条", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                DateTimeField(ts = ts, onTs = { ts = it })

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth().scrollIntoViewOnFocus(),
                    placeholder = { Text("吃了什么…") },
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MEAL_KINDS.forEach { k ->
                        val on = kind == k
                        Surface(
                            color = if (on) PrimaryPurple else ChipBg,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.tap { kind = k }
                        ) {
                            Text(
                                k,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                color = if (on) Color.White else TextGray,
                                fontSize = 13.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tap { medicine = !medicine }
                        .border(1.dp, CardBorder, RoundedCornerShape(11.dp))
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(17.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (medicine) GainColor else Color.Transparent)
                            .border(1.5.dp, if (medicine) GainColor else FaintLine, RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (medicine) Text("✓", fontSize = 11.sp, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("吃了药", style = MaterialTheme.typography.bodyMedium, color = TextGray)
                }
                Text(
                    "六个标签都能勾。默认不勾 —— 没勾就不显示任何东西，不写「未服药」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    modifier = Modifier.padding(top = 6.dp)
                )

                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "删除这一条",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = DangerRed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tap { onDelete(start) },
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                        Haptics.tap()
                    onSave(
                        start.copy(
                            timestamp = ts,
                            content = content.trim(),
                            kind = kind,
                            medicine = medicine
                        )
                    )
                }
            ) { Text(if (initial == null) "记下" else "存下", color = PrimaryPurple, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun Stepper(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(ChipBg)
            .tap(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(glyph, fontSize = 16.sp, color = PrimaryPurple) }
}

// ---------------------------------------------------------------- 体重

/**
 * 体重这一格的三条底线，写在实现上面，是因为它们看不见：
 * 1. 一天只有一个数 —— 同一天再称一次，改的是同一条，留下最新那个。
 * 2. 曲线不给没称的日子留点，纵轴也不从 0 起 —— 补 0 会画成悬崖，
 *    从 0 起会把 ±1kg 压成一条直线，而±1kg 恰恰是这段时间里全部的动静。
 * 3. 没有目标体重、没有标准体重、没有「比标准重多少」那一行。
 */
@Composable
private fun WeightPane(repo: MoodRepository) {
    val ctx = LocalContext.current
    var range by remember { mutableIntStateOf(0) }   // 0=7 天 1=30 天 2=全部
    var pendingDelete by remember { mutableStateOf<Weight?>(null) }
    var editingW by remember { mutableStateOf<Weight?>(null) }
    var justSaved by remember { mutableStateOf(false) }

    LaunchedEffect(justSaved) {
        if (justSaved) {
            kotlinx.coroutines.delay(2200)
            justSaved = false
        }
    }

    // 一天一条。库里躺着更早的、同一天称过两次的旧数据，这里一并按逻辑日收拢，
    // 取当天最晚的那条 —— 老数据不用你自己去清。
    val allSorted = repo.weights
        .groupBy { it.timestamp.logicalDate() }
        .mapValues { (_, list) -> list.maxBy { it.timestamp } }
        .values
        .sortedBy { it.timestamp }

    val days = when (range) { 0 -> 7; 1 -> 30; else -> 3650 }
    val from = logicalToday().minusDays(days.toLong() - 1)
    val list = allSorted.filter { it.timestamp.logicalDate() >= from }
    val latest = allSorted.lastOrNull()
    val previous = if (allSorted.size >= 2) allSorted[allSorted.size - 2] else null

    // 卷轴停在上一次那个数字上：再称一次，多数只是在那个数附近挪一格。
    var kg by remember { mutableFloatStateOf((latest?.kg ?: 62.0).toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        Text(
            "称了就记，没称就不记。同一天再记一次，留下最新那个。",
            style = MaterialTheme.typography.bodySmall,
            color = TextGray
        )

        if (latest != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row {
                        Text(
                            "最近一次",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            dateLabel(latest.timestamp.logicalDate()),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatOne(latest.kg),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("kg", style = MaterialTheme.typography.bodySmall, color = TextGray)
                    }
                    if (previous != null) {
                        val d = latest.kg - previous.kg
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "比上一次 ${if (d >= 0) "+" else "−"}${formatOne(abs(d))}" +
                                "　（上一次 ${formatOne(previous.kg)} · " +
                                "${dateLabel(previous.timestamp.logicalDate())}）",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "拖着数字，停在今天这一点上",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
                Spacer(modifier = Modifier.height(6.dp))
                WeightRuler(
                    valueKg = kg,
                    color = GainColor,
                    onPick = { kg = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                            Haptics.tap()
                        repo.recordWeight(kg.toDouble())
                        justSaved = true
                        buzz(ctx)
                        SaveFeedback.fire(GainColor, "记好了 ✓")
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) { Text("记下今天的体重", fontWeight = FontWeight.SemiBold) }
                if (justSaved) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "记好了 ✓　${dateLabel(logicalToday())} ${formatOne(kg.toDouble())} kg",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = GainColor,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "刻度是 0.1kg，中间那根紫线指着的就是现在的数字。" +
                        "同一天再记一次会盖掉当天原来那个 —— 一天只留最新一次。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    lineHeight = 18.sp
                )
            }
        }

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
                        "体重走势",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (list.size >= 2) {
                        Text(
                            "${list.size} 次称重",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                SegmentedBar(items = listOf("7 天", "30 天", "全部"), selected = range) { range = it }
                Spacer(modifier = Modifier.height(10.dp))
                if (list.size >= 2) {
                    WeightChart(list)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        Text(dateLabel(list.first().timestamp.logicalDate()), fontSize = 11.sp, color = TextGray)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(dateLabel(list.last().timestamp.logicalDate()), fontSize = 11.sp, color = TextGray)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "只连真实称过的那天。没称的日子不留点、也不补。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                } else if (list.size == 1) {
                    Text(
                        "这才称了一次。两个点才连得出一条线 —— 明天再称一次就有了。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                } else {
                    Text(
                        if (range == 2) "还没有称过。"
                        else "这 $days 天里没有称过。切到「全部」能看更早的。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }
            }
        }

        if (allSorted.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "称过的日子",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextGray,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${allSorted.size} 天",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            allSorted.asReversed().take(15).forEach { w ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tap { editingW = w }
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 只有日期，不带时刻：体重是一天一个数，几点称的不重要。
                    Text(
                        dateLabel(w.timestamp.logicalDate()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${formatOne(w.kg)} kg",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SubText
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "删除",
                        style = MaterialTheme.typography.labelSmall,
                        color = DangerRed,
                        modifier = Modifier.tap { pendingDelete = w }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            if (allSorted.size > 15) {
                Text(
                    "只列最近 15 天。更早的称重还在，走上面的「全部」看曲线。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    pendingDelete?.let { w ->
        ConfirmDeleteDialog(
            preview = "${dateLabel(w.timestamp.logicalDate())}　${formatOne(w.kg)} kg",
            onConfirm = { repo.deleteWeight(w.id); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }

    // 点一行 = 改那一次：数字用同一根卷轴，时刻用时间滚轴。删除退到次要位置。
    editingW?.let { w ->
        WeightEditDialog(
            w = w,
            onSave = {
                repo.updateWeight(it)
                editingW = null
                buzz(ctx)
                SaveFeedback.fire(GainColor, "改好了 ✓")
            },
            onDelete = { editingW = null; pendingDelete = w },
            onDismiss = { editingW = null }
        )
    }
}

@Composable
private fun WeightEditDialog(
    w: Weight,
    onSave: (Weight) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var kg by remember { mutableFloatStateOf(w.kg.toFloat()) }
    var ts by remember { mutableStateOf(w.timestamp) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("改这一次称重", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                WeightRuler(
                    valueKg = kg,
                    color = GainColor,
                    onPick = { kg = it }
                )
                Spacer(modifier = Modifier.height(10.dp))
                DateTimeField(ts = ts, onTs = { ts = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                    Haptics.tap()
                onSave(w.copy(timestamp = ts, kg = kg.toDouble()))
            }) { Text("存下", color = PrimaryPurple, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除", color = DangerRed) }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

/**
 * 横向滚动的称。
 *
 * 中间那根紫线是不动的，动的是下面那把尺 —— 手指停下时，紫线指着的数字就是现在的数。
 * 不用输入框：称重这种事发生在浴室地板上，一只手拿着手机，
 * 拖比打字快，也比在小键盘上戳错一位数字安全（0.1kg vs 10kg 的差别）。
 *
 * 步进 0.1kg，每 1kg 一个长刻度并标数字。
 */
@Composable
private fun WeightRuler(
    valueKg: Float,
    color: Color,
    modifier: Modifier = Modifier,
    minKg: Float = 30f,
    maxKg: Float = 150f,
    onPick: (Float) -> Unit
) {
    val step = 0.1f
    val steps = ((maxKg - minKg) / step).roundToInt() + 1
    val gapDp = 9.dp
    val density = LocalDensity.current
    val gapPx = with(density) { gapDp.toPx() }
    val trackPx = gapPx * (steps - 1)
    val trackDp = with(density) { trackPx.toDp() }
    val textMeasurer = rememberTextMeasurer()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    fun pxFor(v: Float): Int =
        (((v - minKg) / step) * gapPx).roundToInt().coerceIn(0, trackPx.roundToInt())

    fun stepBy(delta: Float) {
        val next = (valueKg + delta).coerceIn(minKg, maxKg)
        val snapped = (next * 10f).roundToInt() / 10f
        onPick(snapped)
        scope.launch { scroll.scrollTo(pxFor(snapped)) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Stepper("−") { stepBy(-0.1f) }
            Spacer(modifier = Modifier.width(20.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatOne(valueKg.toDouble()),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = SubText
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text("kg", style = MaterialTheme.typography.bodySmall, color = TextGray)
            }
            Spacer(modifier = Modifier.width(20.dp))
            Stepper("+") { stepBy(0.1f) }
        }

        Spacer(modifier = Modifier.height(6.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
        ) {
            val halfDp = maxWidth / 2
            // 第一次铺出来就落在上一次那个数上，不用自己从 30kg 推过来。
            LaunchedEffect(Unit) { scroll.scrollTo(pxFor(valueKg)) }

            Row(modifier = Modifier.horizontalScroll(scroll)) {
                Spacer(modifier = Modifier.width(halfDp))
                Canvas(modifier = Modifier.width(trackDp).height(66.dp)) {
                    val h = size.height
                    for (i in 0 until steps) {
                        val x = i * gapPx
                        val whole = i % 10 == 0
                        val half = i % 5 == 0
                        val len = if (whole) 20f else if (half) 13f else 7f
                        drawLine(
                            color = if (whole) DisabledC else CardBorder,
                            start = Offset(x, h - 8f),
                            end = Offset(x, h - 8f - len),
                            strokeWidth = if (whole) 2f else 1.5f
                        )
                        if (whole) {
                            val n = (minKg + i * step).roundToInt().toString()
                            val laid = textMeasurer.measure(
                                n, TextStyle(fontSize = 11.sp, color = TextGray)
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = n,
                                style = TextStyle(fontSize = 11.sp, color = TextGray),
                                topLeft = Offset(x - laid.size.width / 2f, 3f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(halfDp))
            }

            // 游标。不動的那根线：拖的是尺，不是这根线。
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(2.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 4.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }

        // 拖的时候跟着读数；停下来回正到整刻度位，不让线卡在两个刻度中间。
        // 一格只有 9px，回正是直接跳过去的，肉眼看不出它在动。
        LaunchedEffect(scroll.value) {
            if (scroll.isScrollInProgress) {
                val raw = minKg + (scroll.value.toFloat() / gapPx) * step
                onPick((raw * 10f).roundToInt().coerceIn((minKg * 10).roundToInt(), (maxKg * 10).roundToInt()) / 10f)
            }
        }
        LaunchedEffect(scroll.isScrollInProgress) {
            if (!scroll.isScrollInProgress) {
                val target = pxFor(valueKg)
                if (abs(target - scroll.value) > 1) scroll.scrollTo(target)
            }
        }
    }
}

/** 体重曲线。纵轴按这批数据自己的范围走，不按 0 起 —— 否则 ±1kg 会被压成一条直线。 */
@Composable
private fun WeightChart(list: List<Weight>) {
    val pts = list.map { it.timestamp.logicalDate().toEpochDay() to it.kg }
    val min = pts.minOf { it.second }
    val max = pts.maxOf { it.second }
    val span = (max - min).coerceAtLeast(1.0)
    val first = pts.first().first
    val last = pts.last().first
    val xSpan = (last - first).coerceAtLeast(1L)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
    ) {
        val w = size.width
        val h = size.height
        val pad = 10f
        fun px(i: Int, v: Double): Offset {
            val x = pad + (w - 2 * pad) * ((pts[i].first - first).toFloat() / xSpan.toFloat())
            val y = pad + (h - 2 * pad) * (1 - ((v - min) / span).toFloat())
            return Offset(x, y)
        }
        for (i in 0 until pts.size - 1) {
            val a = px(i, pts[i].second)
            val b = px(i + 1, pts[i + 1].second)
            drawLine(
                color = GainColor,
                start = a,
                end = b,
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }
        pts.forEachIndexed { i, p ->
            drawCircle(
                color = GainColor,
                radius = if (i == pts.size - 1) 9f else 6f,
                center = px(i, p.second)
            )
        }
    }
}

/** 10-16 周三 —— 列表和图上这一格只给日期，不给时刻。 */
private fun dateLabel(day: LocalDate): String =
    "${day.monthValue}-${String.format("%02d", day.dayOfMonth)} ${weekdayOf(day)}"

private fun formatOne(v: Double): String = String.format("%.1f", v)

// ---------------------------------------------------------------- 运动

private val MoveColor = LifeDimColors[2]   // 生命力那个色：身体动过，是这个感觉

/** 时长怎么说。0 = 没记时长，写「—」—— 那是空白，不是 0 分钟。 */
private fun moveMinutesText(min: Int): String = when {
    min <= 0 -> "—"
    min < 60 -> "$min 分钟"
    min % 60 == 0 -> "${min / 60} 小时"
    else -> "${min / 60} 小时 ${min % 60} 分"
}

/**
 * 【我身体 › 运动】。选一个类型，记多久。
 *
 * 这一格的三条底线，同样写在代码里：
 * 1. 不记热量、步数、配速、距离、组数 —— 一旦开始算成效，它就变成结算页了。
 * 2. 没有目标、没有进度环、不数连续天数。没动的日子这里什么都不说。
 * 3. 所有类型一个颜色、不分好坏：遛弯不比健身低一等。
 */
@Composable
private fun MovePane(repo: MoodRepository) {
    val ctx = LocalContext.current
    val today = logicalToday()
    var editing by remember { mutableStateOf<Move?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Move?>(null) }
    var openDays by remember { mutableStateOf(setOf<String>()) }
    var pickedDay by remember { mutableStateOf<LocalDate?>(null) }

    // 一次分组：今天、最近七天、整月日历都从这里取。
    val byDay = repo.moves.groupBy { it.timestamp.logicalDate() }
    val todayList = byDay[today].orEmpty().sortedBy { it.timestamp }
    val todayMinutes = todayList.sumOf { it.minutes }
    val recent = (1..6).map { today.minusDays(it.toLong()) }
        .mapNotNull { d -> byDay[d]?.let { d to it } }

    // 最近七天（含今天）各类型合计。只给数字和长短，不排名、不夸、不贬。
    val window = (0..6).map { today.minusDays(it.toLong()) }.toSet()
    val weekly = repo.moves
        .filter { it.timestamp.logicalDate() in window && it.tag.isNotBlank() }
        .groupBy { it.tag }
        .mapValues { (_, list) -> list.sumOf { it.minutes } }
        .toList()
        .sortedByDescending { it.second }
    val weeklyMax = weekly.maxOfOrNull { it.second } ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        Text(
            "动了什么、动多久。不算热量，也不设目标。",
            style = MaterialTheme.typography.bodySmall,
            color = TextGray
        )

        Spacer(modifier = Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row {
                    Text(
                        "今天动了什么",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (todayList.isEmpty()) "还没记"
                        else "${todayList.size} 次" +
                            (if (todayMinutes > 0) " · 合计 ${moveMinutesText(todayMinutes)}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }
                if (todayList.isEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "今天还没有记下。没记就不记 —— 这里不算漏。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    todayList.forEach { m ->
                        MoveRow(m, onClick = { editing = m })
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "点一条能改：换类型、改时长、改时间。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = { Haptics.tap(); adding = true },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MoveColor)
        ) { Text("+ 记一次", fontWeight = FontWeight.SemiBold) }

        if (weekly.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "这七天各多久",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextGray
            )
            Spacer(modifier = Modifier.height(8.dp))
            weekly.forEach { (tag, sum) ->
                MoveBar(
                    name = tag,
                    value = if (sum > 0) moveMinutesText(sum) else "记过",
                    frac = if (weeklyMax > 0) sum.toFloat() / weeklyMax else 0f
                )
            }
        }

        if (recent.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "最近七天",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextGray
            )
            Spacer(modifier = Modifier.height(8.dp))
            recent.forEach { (day, list) ->
                val key = day.toString()
                val open = key in openDays
                val sum = list.sumOf { it.minutes }
                FoldLine(
                    label = "${day.monthValue}/${day.dayOfMonth} " + weekdayOf(day),
                    count = "${list.size} 次",
                    tail = if (sum > 0) "合计 ${moveMinutesText(sum)}" else "没记时长",
                    expanded = open,
                    onClick = { openDays = if (open) openDays - key else openDays + key }
                )
                if (open) {
                    Spacer(modifier = Modifier.height(6.dp))
                    list.sortedBy { it.timestamp }.forEach { m ->
                        MoveRow(m, onClick = { editing = m })
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        // 更早的日子不藏进「月历 ›」后面 —— 月历就铺在下面，和【我话事】同一个规矩：
        // 以前要两层才到那天，现在一张月历点了就弹。下面的数字是那天记了几顿/几次。
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

    if (adding || editing != null) {
        MoveDialog(
            repo = repo,
            initial = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { m ->
                if (m.id == 0L) repo.insertMove(m) else repo.updateMove(m)
                adding = false; editing = null
                buzz(ctx)
                SaveFeedback.fire(MoveColor, if (m.id == 0L) "记下了 ✓" else "改好了 ✓")
            },
            onDelete = { m -> editing = null; pendingDelete = m }
        )
    }

    pendingDelete?.let { m ->
        ConfirmDeleteDialog(
            preview = "${m.timestamp.logicalTimeString()}　${m.tag.ifBlank { "（没写类型）" }}" +
                "　${moveMinutesText(m.minutes)}" +
                (if (m.note.isNotBlank()) "　${m.note}" else ""),
            onConfirm = { repo.deleteMove(m.id); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }

    pickedDay?.let { day ->
        val list = repo.moves.filter { it.timestamp.logicalDate() == day }.sortedBy { it.timestamp }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickedDay = null },
            title = {
                Text(
                    "${day.monthValue} 月 ${day.dayOfMonth} 日 · ${weekdayOf(day)}",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    list.forEach { m ->
                        MoveRow(m, onClick = { pickedDay = null; editing = m })
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { Haptics.tap(); pickedDay = null }) { Text("关闭", color = PrimaryPurple) }
            }
        )
    }
}

@Composable
private fun MoveRow(m: Move, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().tap(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    m.tag.ifBlank { "（没写类型）" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${m.timestamp.logicalTimeString()} · ${moveMinutesText(m.minutes)}" +
                        (if (m.note.isNotBlank()) " · ${m.note}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
            Text("›", fontSize = 17.sp, color = DisabledC)
        }
    }
}

/** 七天分布里那一条。只给长度和数字，不给名次、不给目标线。 */
@Composable
private fun MoveBar(name: String, value: String, frac: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            color = SubText,
            modifier = Modifier.width(72.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ChipBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac.coerceIn(0.02f, 1f))
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MoveColor.copy(alpha = 0.75f))
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = TextGray,
            modifier = Modifier.width(58.dp),
            textAlign = TextAlign.End
        )
    }
}

/**
 * 记一次 / 改一次。
 *
 * 类型单选 —— 打完球又去游泳就是两件事，记两条。
 * 时长可以留空（「没记时长」）：有些日子只记得去打了球，不记得多久，那也作数。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveDialog(
    repo: MoodRepository,
    initial: Move?,
    onDismiss: () -> Unit,
    onSave: (Move) -> Unit,
    onDelete: ((Move) -> Unit)? = null
) {
    val start = initial ?: Move()
    var ts by remember { mutableStateOf(initial?.timestamp ?: System.currentTimeMillis()) }
    var tag by remember { mutableStateOf(start.tag) }
    // 新建默认 30 分钟：多数时候不用拖。想留空就点「没记时长」。
    var minutes by remember { mutableIntStateOf(initial?.minutes ?: 30) }
    var note by remember { mutableStateOf(start.note) }
    var addingTag by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf("") }

    // 预置 + 自己加过的，去重后连成一排；用过的排在前面（moveTags 已按次数排）。
    val custom = repo.moveTags.map { it.name }
    val allTags = (custom + MOVE_PRESETS).distinct()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "记一次运动" else "改这一条", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "类型",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allTags.forEach { t ->
                        MoveChip(label = t, on = t == tag) { tag = if (tag == t) "" else t }
                    }
                    MoveChip(label = "+ 自己的", on = false) { addingTag = !addingTag }
                }
                if (addingTag) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTag,
                            onValueChange = { newTag = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("比如：划船机 / 遛弯") },
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        TextButton(onClick = {
                                Haptics.tap()
                            val t = newTag.trim()
                            if (t.isNotBlank()) {
                                repo.addMoveTag(t)
                                tag = t
                            }
                            newTag = ""
                            addingTag = false
                        }) { Text("加上", color = PrimaryPurple) }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "时长",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray,
                        modifier = Modifier.weight(1f)
                    )
                    MoveChip(label = "没记时长", on = minutes == 0) { minutes = if (minutes == 0) 30 else 0 }
                }
                MinuteRuler(
                    minutes = minutes,
                    enabled = minutes > 0,
                    onPick = { minutes = it }
                )

                Spacer(modifier = Modifier.height(10.dp))
                DateTimeField(ts = ts, onTs = { ts = it })

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth().scrollIntoViewOnFocus(),
                    singleLine = true,
                    placeholder = { Text("备注，可不写：和老陈 / 绕了外环") },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                    Haptics.tap()
                onSave(start.copy(timestamp = ts, tag = tag.trim(), minutes = minutes, note = note.trim()))
            }) { Text("记下", color = PrimaryPurple, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            Row {
                if (initial != null && onDelete != null) {
                    TextButton(onClick = { Haptics.tap(); onDelete(initial) }) { Text("删除", color = DangerRed) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun MoveChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) MoveColor else ChipBg)
            .tap(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (on) CardBg else SubText,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * 时长那把尺。中间那根线不动，动的是下面的刻度 —— 和体重那把同一个手感。
 *
 * 量程 5–300 分钟，5 分钟一格；整点（60/120…）标数字。
 * 「没记时长」时整把尺变灰：它是真的没启用，不是停在零上。
 */
@Composable
private fun MinuteRuler(
    minutes: Int,
    enabled: Boolean,
    onPick: (Int) -> Unit
) {
    val minM = 5
    val maxM = 300
    val step = 5
    val steps = (maxM - minM) / step + 1
    val gapDp = 9.dp
    val density = LocalDensity.current
    val gapPx = with(density) { gapDp.toPx() }
    val trackPx = gapPx * (steps - 1)
    val trackDp = with(density) { trackPx.toDp() }
    val textMeasurer = rememberTextMeasurer()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val color = if (enabled) MoveColor else DisabledC

    fun pxFor(v: Int): Int = (((v - minM) / step) * gapPx).roundToInt().coerceIn(0, trackPx.roundToInt())

    fun setMinute(next: Int) {
        val snapped = (next / step) * step
        val v = snapped.coerceIn(minM, maxM)
        onPick(v)
        scope.launch { scroll.scrollTo(pxFor(v)) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Stepper("−") { if (enabled) setMinute(minutes - step) }
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                moveMinutesText(if (enabled) minutes else 0),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) SubText else DisabledC
            )
            Spacer(modifier = Modifier.width(20.dp))
            Stepper("+") { if (enabled) setMinute(minutes + step) }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
        ) {
            listOf(15, 30, 45, 60, 90, 120).forEach { q ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (enabled && minutes == q) MoveColor.copy(alpha = 0.16f) else ChipBg)
                        .tap { setMinute(q) }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        "$q",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (enabled && minutes == q) MoveColor else TextGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            val halfDp = maxWidth / 2
            LaunchedEffect(Unit) { scroll.scrollTo(pxFor(minutes.coerceIn(minM, maxM))) }

            Row(modifier = Modifier.horizontalScroll(scroll)) {
                Spacer(modifier = Modifier.width(halfDp))
                Canvas(modifier = Modifier.width(trackDp).height(52.dp)) {
                    val h = size.height
                    for (i in 0 until steps) {
                        val x = i * gapPx
                        val v = minM + i * step
                        val hour = v % 60 == 0
                        val half = v % 30 == 0
                        val len = if (hour) 18f else if (half) 12f else 6f
                        drawLine(
                            color = if (hour) DisabledC else CardBorder,
                            start = Offset(x, h - 6f),
                            end = Offset(x, h - 6f - len),
                            strokeWidth = if (hour) 2f else 1.5f
                        )
                        if (hour) {
                            val n = "${v / 60} 小时"
                            val laid = textMeasurer.measure(
                                n, TextStyle(fontSize = 10.sp, color = TextGray)
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = n,
                                style = TextStyle(fontSize = 10.sp, color = TextGray),
                                topLeft = Offset(x - laid.size.width / 2f, 2f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(halfDp))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(2.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }

        LaunchedEffect(scroll.value, enabled) {
            if (enabled && scroll.isScrollInProgress) {
                val raw = minM + (scroll.value.toFloat() / gapPx) * step
                val snapped = (raw / step).roundToInt() * step
                onPick(snapped.coerceIn(minM, maxM))
            }
        }
        LaunchedEffect(scroll.isScrollInProgress, minutes, enabled) {
            if (enabled && !scroll.isScrollInProgress) {
                val target = pxFor(minutes.coerceIn(minM, maxM))
                if (abs(target - scroll.value) > 1) scroll.scrollTo(target)
            }
        }
    }
}

// ---------------------------------------------------------------- 小工具

private fun Long.toMinuteOfDay(): Int {
    val t = java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())
    return t.hour * 60 + t.minute
}

private fun Int.toClock(): String =
    String.format("%02d:%02d", this / 60 % 24, this % 60)

/**
 * 挂钟上的某一天 + 某个时刻 → 毫秒。
 *
 * 这里刻意用<b>自然日</b>（10 月 16 日就是 10 月 16 日），不掺逻辑日：
 * 「几点几分」是肉身的时间，说十点半吃饭就是十点半；至于这一笔算哪一天，
 * 是 logicalDate() 的事 —— 那是归组，写时间戳的时候不要替它做决定。
 */
private fun LocalDate.atMinute(minuteOfDay: Int): Long =
    java.time.LocalDateTime.of(
        this, java.time.LocalTime.of(minuteOfDay / 60 % 24, minuteOfDay % 60)
    ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun weekdayOf(day: LocalDate): String =
    day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.CHINA)

/** 折叠的那一行：哪天、几次、最早几点。点一下摊开那一天。 */
@Composable
private fun FoldLine(
    label: String,
    count: String,
    tail: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tap(onClick = onClick)
            .border(1.dp, CardBorder, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
        Spacer(modifier = Modifier.width(6.dp))
        Text(if (expanded) "⌄" else "›", fontSize = 15.sp, color = DisabledC)
    }
}
