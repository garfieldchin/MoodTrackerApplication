package com.example.moodlog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moodlog.ui.theme.ChipBg
import com.example.moodlog.ui.theme.DisabledC
import com.example.moodlog.ui.theme.GridColor
import com.example.moodlog.ui.theme.PrimaryPurple
import com.example.moodlog.ui.theme.SubText
import com.example.moodlog.ui.theme.TextGray
import com.example.moodlog.util.DAY_START_HOUR
import com.example.moodlog.util.logicalDate
import com.example.moodlog.util.logicalToday
import com.example.moodlog.util.toLocalDateTime
import com.example.moodlog.util.toTimeString
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** 逻辑日一天里的累进分钟数下限：06:00。 */
private const val DAY_MIN = DAY_START_HOUR * 60        // 360
/** 上限：29:59。 */
private const val DAY_MAX = 29 * 60 + 59               // 1799

/** 任一毫秒时刻 → 逻辑日里 06:00–29:59 的累进分钟。 */
private fun Long.toLogicalMinute(): Int {
    val dt = toLocalDateTime()
    val h = if (dt.hour < DAY_START_HOUR) dt.hour + 24 else dt.hour
    return h * 60 + dt.minute
}

/**
 * 逻辑日 + 累进分钟 → 毫秒。
 * day 是「这一笔算在哪一天」的那个逻辑日：360 分就是那天的 06:00，
 * 1799 分是第二天早上 05:59。
 */
private fun dayMinuteToTs(day: LocalDate, minute: Int): Long =
    LocalDateTime.of(day, LocalTime.MIN).plusMinutes(minute.toLong())
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun Int.toClockLabel(): String = String.format("%02d:%02d", this / 60, this % 60)

private fun weekdayOf(day: LocalDate): String =
    day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.CHINA)

/**
 * 日期行 + 大字时间 + 一根矮滚轴。
 *
 * 量程是 06:00 – 29:59 —— 和逻辑日同一条缝：06:00 以前属于昨天，
 * 显示成 24:00 以上的累进时间。
 *
 * 能去的地方是有限的：
 *   · 往前（回）最多一天 —— 补记是补昨夜、补前一顿，不是翻旧账；
 *   · 往后只到今天，而且今天的右端就是「现在这一分钟」—— 未来的位置不预留。
 * 改一条更旧的记录时，它原来那一天仍然到得了（不然改旧记录一打开就被拽到今天），
 * 但也只到那一天为止。
 *
 * 日子一律按逻辑日算：凌晨两点的那一笔属于前一天，按自然日写会整整差一天。
 *
 * ts 由外部持有；拖动中连续回吐，松手回正到 5 分钟整刻度。
 */
@Composable
fun DateTimeField(
    ts: Long,
    modifier: Modifier = Modifier,
    onTs: (Long) -> Unit
) {
    val today = logicalToday()
    // 打开时这一笔所属的那一天。
    val firstDay = remember { ts.logicalDate() }
    // 最早能回到哪一天：今天的前一天；改更旧的记录时，最早就是它自己的那天。
    val minDay = remember {
        val back = today.minusDays(1)
        if (firstDay.isBefore(back)) firstDay else back
    }
    var day by remember { mutableStateOf(if (firstDay.isAfter(today)) today else firstDay) }
    var minute by remember { mutableIntStateOf(ts.toLogicalMinute().coerceIn(DAY_MIN, DAY_MAX)) }
    var lastEmitted by remember { mutableStateOf<Long?>(null) }

    // —— 滚轴几何：5 分钟一格。尺子做矮，大字才是主角。 ——
    val stepMin = 5
    val steps = (DAY_MAX - DAY_MIN) / stepMin + 1        // 288 格
    val gapDp = 7.dp
    val trackHeight = 38.dp
    val density = LocalDensity.current
    val gapPx = with(density) { gapDp.toPx() }
    val trackPx = gapPx * (steps - 1)
    val trackDp = with(density) { trackPx.toDp() }
    val textMeasurer = rememberTextMeasurer()
    val scroll = rememberScrollState()
    // 要把尺子挪到某个位置的请求（位置 + 序号）。scrollTo 是挂起函数，
    // 点击回调里调不动，所以先在状态里记一笔，交给下面那个 LaunchedEffect 去做。
    var scrollAsk by remember { mutableStateOf(0 to 0) }

    fun askScroll(px: Int) {
        scrollAsk = px to (scrollAsk.second + 1)
    }

    fun pxFor(m: Int): Int =
        (((m - DAY_MIN).toFloat() / stepMin) * gapPx).roundToInt()
            .coerceIn(0, trackPx.roundToInt())

    fun clampDay(d: LocalDate): LocalDate =
        if (d.isAfter(today)) today else if (d.isBefore(minDay)) minDay else d

    /** 今天的右端是现在这一分钟；过去的日子整段都能选。 */
    fun maxMinute(): Int =
        if (day == today) (DAY_MAX.coerceAtMost(System.currentTimeMillis().toLogicalMinute()))
            .coerceAtLeast(DAY_MIN)
        else DAY_MAX

    fun setMinute(m: Int) {
        val clamped = m.coerceIn(DAY_MIN, maxMinute())
        if (clamped != minute) {
            minute = clamped
            val newTs = dayMinuteToTs(day, clamped)
            if (newTs != ts) {
                lastEmitted = newTs
                onTs(newTs)
            }
        }
    }

    fun goDay(d: LocalDate) {
        val nd = clampDay(d)
        if (nd == day) return
        day = nd
        // 换了一天，右端可能跟着变（今天拖不过现在）：先收进新范围，再把尺子挪过去。
        val m = minute.coerceIn(DAY_MIN, maxMinute())
        minute = m
        val newTs = dayMinuteToTs(nd, m)
        lastEmitted = newTs
        onTs(newTs)
        askScroll(pxFor(m))
    }

    // 首次铺出来就停在传入的时刻上。
    LaunchedEffect(Unit) { scroll.scrollTo(pxFor(minute)) }
    // 翻日期、按「现在」之后：把尺子挪到新的位置。
    LaunchedEffect(scrollAsk) {
        if (scrollAsk.second > 0) scroll.scrollTo(scrollAsk.first)
    }

    // 拖动中跟着读数（和体重卷轴同一套：只读 scroll.value，不和滚动手势抢事件）。
    LaunchedEffect(scroll.value) {
        if (scroll.isScrollInProgress) {
            setMinute(DAY_MIN + ((scroll.value / gapPx).roundToInt() * stepMin))
        }
    }
    // 松手回正到整刻度位；一格 7dp，回正是跳过去的，肉眼看不出它在动。
    LaunchedEffect(scroll.isScrollInProgress) {
        if (!scroll.isScrollInProgress) {
            val target = pxFor(minute)
            if (abs(target - scroll.value) > 1) scroll.scrollTo(target)
        }
    }

    // 外部把 ts 换了（「现在」按钮等）：把滚轴跟过去。
    // 自己拖动回吐的 ts 不跟 —— 不然尺子会跟手指打架。
    LaunchedEffect(ts) {
        if (lastEmitted != ts) {
            day = clampDay(ts.logicalDate())
            minute = ts.toLogicalMinute().coerceIn(DAY_MIN, maxMinute())
            scroll.scrollTo(pxFor(minute))
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // —— 日期行 ——
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ChipBg)
                    .tap(enabled = day > minDay) { goDay(day.minusDays(1)) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "‹",
                    fontSize = 15.sp,
                    color = if (day > minDay) PrimaryPurple else DisabledC
                )
            }
            Text(
                "${day.monthValue} 月 ${day.dayOfMonth} 日 ${weekdayOf(day)}" +
                    if (day == today) " · 今天" else "",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ChipBg)
                    .tap(enabled = day < today) { goDay(day.plusDays(1)) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "›",
                    fontSize = 15.sp,
                    color = if (day < today) PrimaryPurple else DisabledC
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // —— 大字时间 ——
        val targetTs = dayMinuteToTs(day, minute)
        val clock = targetTs.toTimeString()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                minute.toClockLabel(),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryPurple
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.padding(bottom = 5.dp)) {
                Text(
                    if (minute >= 24 * 60) "夜里 · 归在前一天" else "这一天里",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGray
                )
                Text(
                    "挂钟 $clock",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGray
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // —— 矮滚轴 ——
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
        ) {
            val halfDp = maxWidth / 2

            Row(modifier = Modifier.horizontalScroll(scroll)) {
                Spacer(modifier = Modifier.width(halfDp))
                Canvas(modifier = Modifier.width(trackDp).height(trackHeight)) {
                    val h = size.height
                    for (i in 0 until steps) {
                        val x = i * gapPx
                        val m = DAY_MIN + i * stepMin
                        val hourTick = m % 60 == 0
                        val halfTick = m % 30 == 0
                        val len = if (hourTick) 13f else if (halfTick) 8f else 4f
                        drawLine(
                            color = if (hourTick) DisabledC else GridColor,
                            start = Offset(x, h - 5f),
                            end = Offset(x, h - 5f - len),
                            strokeWidth = if (hourTick) 1.8f else 1.2f
                        )
                        if (hourTick) {
                            drawText(
                                textMeasurer = textMeasurer,
                                text = String.format("%02d", m / 60),
                                style = TextStyle(fontSize = 9.sp, color = TextGray),
                                topLeft = Offset(x - 11f, 1f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(halfDp))
            }

            // 游标：不动的那根紫线。拖的是尺，不是这根线。
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(2.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(PrimaryPurple)
            )
        }

        // —— 底下一行：±5 分钟、现在 ——
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "−5 分",
                style = MaterialTheme.typography.labelMedium,
                color = PrimaryPurple,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .tap { setMinute(minute - 5) }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            )
            Text(
                "+5 分",
                style = MaterialTheme.typography.labelMedium,
                color = PrimaryPurple,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .tap { setMinute(minute + 5) }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "现在",
                style = MaterialTheme.typography.labelMedium,
                color = PrimaryPurple,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .tap {
                        val now = System.currentTimeMillis()
                        day = now.logicalDate()
                        minute = now.toLogicalMinute()
                        lastEmitted = now
                        onTs(now)
                        askScroll(pxFor(minute))
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            )
        }

        val logicalDay = targetTs.logicalDate()
        Text(
            "记在 ${logicalDay.monthValue}/${logicalDay.dayOfMonth} ${weekdayOf(logicalDay)}" +
                if (day == today) " · 拖不过现在" else "",
            style = MaterialTheme.typography.bodySmall,
            color = SubText
        )
    }
}
