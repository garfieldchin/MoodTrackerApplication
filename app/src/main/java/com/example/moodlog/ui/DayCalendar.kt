package com.example.moodlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moodlog.ui.theme.*
import com.example.moodlog.ui.theme.CalHigh
import com.example.moodlog.ui.theme.CalLow
import com.example.moodlog.ui.theme.CalMid
import com.example.moodlog.ui.theme.CalNone
import com.example.moodlog.ui.theme.CardBorder
import com.example.moodlog.ui.theme.DisabledC
import com.example.moodlog.ui.theme.FaintText
import com.example.moodlog.ui.theme.MarkText
import com.example.moodlog.ui.theme.PageBg
import com.example.moodlog.ui.theme.PrimaryPurple
import com.example.moodlog.ui.theme.SubText
import com.example.moodlog.ui.theme.TextGray
import com.example.moodlog.util.logicalDate
import com.example.moodlog.util.logicalToday
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * 日历色条的颜色。
 *
 * 四档，各管一件事：
 *   没记   → 浅灰。空着会让人以为还没加载，所以留一条很淡的灰。
 *   0–4    → 绿。那天记了，只是没到 5 分 —— 这是一件好事，值得一个正色。
 *   5–7    → 琥珀
 *   8 以上 → 红
 *
 * 「没记」和「记了但很平静」以前是同一个灰色，等于把人记过的那一天抹掉了。
 */
private fun calBar(peak: Int?): Color = when {
    peak == null -> CalNone
    peak >= 8 -> CalHigh
    peak >= 5 -> CalMid
    else -> CalLow
}

/**
 * 一个月的格子。
 *
 * 相邻月份的那几天也在里面 —— 它们是<b>真实的日期</b>，不是补白的空格子：
 * 10 月 1 号是周四，行首就摆着 9 月 28、29、30；月末同理。
 * 只是画面权重降下来：数字淡一档、可点、色条照常画 —— 跨月那一段趋势才接得上，
 * 点一下就是翻到那个月（不用先翻月再找）。
 *
 * 未来的日子：数字再淡一档、<b>不画色条</b>、不响应点击。我们不看还没发生的事。
 *
 * 最后一行补到周日为止就停 —— 比如这个月最后一天是周六，尾巴上只多摆一个下月 1 号。
 * 不把下一整周也铺出来：那是还没到的日子，摆在那里只会让人以为那几天已经过去了。
 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    marked: (LocalDate) -> Int?,
    selected: LocalDate?,
    onPick: (LocalDate) -> Unit
) {
    val today = logicalToday()

    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1          // 周一开头：前面补几格
    val daysInMonth = month.lengthOfMonth()
    val head = (lead downTo 1).map { first.minusDays(it.toLong()) }
    val body = (1..daysInMonth).map { month.atDay(it) }
    // 只补齐当前这一行（到周日），不再多填一行。
    val fill = (7 - (head.size + daysInMonth) % 7) % 7
    val tail = if (fill > 0) {
        (1..fill).map { month.atDay(daysInMonth).plusDays(it.toLong()) }
    } else emptyList()

    (head + body + tail).chunked(7).forEach { week ->
        Row(modifier = Modifier.fillMaxWidth()) {
            week.forEach { date ->
                val outside = YearMonth.from(date) != month
                val future = date > today
                val peak = marked(date)
                val isSelected = selected?.let { it == date } ?: false
                val isToday = date == today
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isToday) {
                                    Modifier.border(
                                        1.5.dp, PrimaryPurple, RoundedCornerShape(12.dp)
                                    )
                                } else Modifier
                            )
                            .background(
                                if (isSelected) PrimaryPurple.copy(alpha = 0.16f)
                                else Color.Transparent
                            )
                            .then(if (future) Modifier else Modifier.tap { onPick(date) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "${date.dayOfMonth}",
                            fontSize = 12.5.sp,
                            fontWeight = if (isToday || isSelected) FontWeight.Bold
                            else FontWeight.Normal,
                            color = when {
                                future -> FaintText
                                outside -> DisabledC
                                isSelected || isToday || peak != null -> MarkText
                                else -> SubText
                            }
                        )
                        // 未来的日子连条子都不画：还没发生的事不给它留位置。
                        if (!future) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = 22.dp, height = 3.dp)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(calBar(peak))
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}

/** 周表头：一二三四五六日。 */
@Composable
private fun WeekHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf(1, 2, 3, 4, 5, 6, 7).forEach { dow ->
            Text(
                java.time.DayOfWeek.of(dow)
                    .getDisplayName(TextStyle.NARROW, Locale.CHINA),
                fontSize = 11.sp,
                color = TextGray,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarLegend() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Legend(null, "没记录")
        Legend(2, "0–4 分")
        Legend(6, "5–7 分")
        Legend(9, "8 分以上")
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        "灰条是没记。数字淡的那几天是上个月或下个月，点一下就翻过去。",
        style = MaterialTheme.typography.bodySmall,
        color = TextGray,
        lineHeight = 18.sp
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "点一天看那天的总结。没点的一天不会说你漏了什么 —— 这里不数连续。",
        style = MaterialTheme.typography.bodySmall,
        color = TextGray,
        lineHeight = 18.sp
    )
}

/**
 * 挑一天的月历（弹层版）。整个【成为】页和总结页共用。
 *
 * 格子下面那条只回答一件事：<b>那天有多难</b>。
 * 它<b>不打分、不评级、不算连续天数</b> —— 一旦开始数连续，漏记的一天就变成了失败，
 * 那正好是这个 app 要拆掉的东西。
 */
@Composable
fun DayCalendarSheet(
    marked: (LocalDate) -> Int?,   // 返回那天的恐慌峰值；null = 那天没有记录
    selected: LocalDate,
    onPick: (LocalDate) -> Unit,
    onClose: () -> Unit
) {
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val today = logicalToday()
    val thisMonth = YearMonth.from(today)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PageBg)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Arrow("‹", enabled = true) { month = month.minusMonths(1) }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${month.year} 年 ${month.monthValue} 月",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Arrow(
                    "›",
                    enabled = month < thisMonth
                ) { if (month < thisMonth) month = month.plusMonths(1) }
            }

            Spacer(modifier = Modifier.height(10.dp))
            WeekHeader()
            Spacer(modifier = Modifier.height(6.dp))

            MonthGrid(
                month = month,
                marked = marked,
                selected = selected
            ) { date ->
                // 点邻月的日子 = 翻到那个月，顺手选中它。
                if (YearMonth.from(date) != month) month = YearMonth.from(date)
                onPick(date)
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = CardBorder)
            Spacer(modifier = Modifier.height(8.dp))

            CalendarLegend()

            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .tap(onClick = onClose)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("回到今天", color = PrimaryPurple, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * 图例里的一小条。
 *
 * 日历只摆在【成为 · 我的今天】和【全部记录】里 —— 【趋势】那一页刻意不放日历：
 * 那一页说的是「连起来是什么形状」，一格一格的日历会把它的视线抢走。
 */
@Composable
private fun Legend(peak: Int?, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 22.dp, height = 3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(calBar(peak))
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(text, fontSize = 10.5.sp, color = TextGray)
    }
}

@Composable
private fun Arrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .tap(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            fontSize = 17.sp,
            color = if (enabled) PrimaryPurple else FaintText
        )
    }
}

/**
 * 把仓库里的记录折成「某一天的最高峰」。
 * 用逻辑日分组 —— 凌晨三点那一笔属于前一天，这一页不会记错账。
 */
fun panicPeakByDay(records: List<com.example.moodlog.data.MoodRecord>): Map<LocalDate, Int> =
    records.groupBy { it.timestamp.logicalDate() }
        // 没填恐慌的那些次不算数：那天没有「有多难」这个读数，日历上就当作没记，不是「很平稳」。
        .mapNotNull { (day, list) ->
            list.map { it.panic }.filter { it >= 0 }.maxOrNull()?.let { day to it }
        }
        .toMap()
