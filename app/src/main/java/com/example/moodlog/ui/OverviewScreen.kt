package com.example.moodlog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.moodlog.data.ConfirmLabel
import com.example.moodlog.data.EMOTION_FIELDS
import com.example.moodlog.data.MoodRecord
import com.example.moodlog.data.MoodRepository
import com.example.moodlog.ui.theme.ConfirmColor
import com.example.moodlog.ui.theme.ContactColor
import com.example.moodlog.ui.theme.EmotionColors
import com.example.moodlog.ui.theme.MissColor
import com.example.moodlog.ui.theme.PanicColor
import com.example.moodlog.ui.theme.PrimaryPurple
import com.example.moodlog.ui.theme.TextGray
import com.example.moodlog.util.DATE_CN_FMT
import com.example.moodlog.util.MD_FMT
import com.example.moodlog.util.logicalDate
import com.example.moodlog.util.logicalTimeString
import com.example.moodlog.util.logicalToday

/** 一条可统计的指标：主维度始终有值，二级情绪只在填过才有值。 */
private data class Metric(
    val name: String,
    val color: Color,
    val get: (MoodRecord) -> Int?
)

/**
 * 顺序与录入顺序一致：想念TA → 想联系 → 第三条 → 恐慌。所有页面都要按这个来。
 *
 * 写成函数而不是常量：第三条的名字可以由用户改（默认「想确认」），
 * 每帧读一次才跟得上改完名字的那一屏。
 */
private fun primaryMetrics(): List<Metric> = listOf(
    // 未答（库里是 -1）不参与统计：它不是一个 0 分的答案，不能把平均拉下去。
    Metric("想念TA", MissColor) { it.miss.takeIf { v -> v >= 0 } },
    Metric("想联系", ContactColor) { it.contact.takeIf { v -> v >= 0 } },
    Metric(ConfirmLabel.name, ConfirmColor) { it.confirm.takeIf { v -> v >= 0 } },
    Metric("恐慌", PanicColor) { it.panic.takeIf { v -> v >= 0 } }
)

private val EMOTION_METRICS: List<Metric> = EMOTION_FIELDS.mapIndexed { index, (name, getter) ->
    Metric(name, EmotionColors[index]) { record -> record.emotions?.let { getter(it) } }
}

private data class RangeData(
    val title: String,
    val labels: List<String>,
    val buckets: List<List<MoodRecord>>
)

private fun buildRange(records: List<MoodRecord>, rangeIdx: Int, shift: Int): RangeData {
    val today = logicalToday()
    return when (rangeIdx) {
        0 -> {
            val day = today.plusDays(shift.toLong())
            val items = records.filter { it.timestamp.logicalDate() == day }.sortedBy { it.timestamp }
            RangeData(
                title = day.format(DATE_CN_FMT),
                labels = items.map { it.timestamp.logicalTimeString() },
                buckets = items.map { listOf(it) }
            )
        }
        1 -> {
            val end = today.plusWeeks(shift.toLong())
            val days = (6 downTo 0).map { end.minusDays(it.toLong()) }
            RangeData(
                title = "${days.first().format(MD_FMT)} - ${days.last().format(MD_FMT)}",
                labels = days.map { it.format(MD_FMT) },
                buckets = days.map { d -> records.filter { it.timestamp.logicalDate() == d } }
            )
        }
        else -> {
            val end = today.plusMonths(shift.toLong())
            val days = (29 downTo 0).map { end.minusDays(it.toLong()) }
            RangeData(
                title = "${days.first().format(MD_FMT)} - ${days.last().format(MD_FMT)}",
                labels = days.map { it.format(MD_FMT) },
                buckets = days.map { d -> records.filter { it.timestamp.logicalDate() == d } }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewScreen(
    repo: MoodRepository,
    padding: PaddingValues,
    onOpenHistory: (() -> Unit)? = null
) {
    var rangeIdx by rememberSaveable { mutableIntStateOf(0) }
    var shift by rememberSaveable { mutableIntStateOf(0) }
    // 记录 ⇄ 二级情绪。它不记住 —— 每次进趋势页都回到「记录」。
    // 二级情绪是特意去看的，不该成为默认视野。
    var setIdx by remember { mutableIntStateOf(0) }

    val records = repo.records
    val range = buildRange(records, rangeIdx, shift)
    val flat = range.buckets.flatten()
    val count = flat.size

    // 二级情绪不是每次都记 —— 恐慌 5 分以上才会问到。
    // 所以它单独一块，并且只挑「真的记过」的那些：没记过的日子不画成 0，直接不出现在这块里。
    val hasEmotion: (MoodRecord) -> Boolean = { it.emotions != null && !it.emotions.isEmpty }
    val emotionRecords = flat.filter(hasEmotion)
    val emotionBuckets = range.buckets.mapNotNull { b ->
        val hit = b.filter(hasEmotion)
        if (hit.isEmpty()) null else hit
    }
    val emotionLabels = range.labels.filterIndexed { i, _ -> range.buckets[i].any(hasEmotion) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(
            text = "趋势",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "看它怎么走，不用它判断自己好不好。",
            style = MaterialTheme.typography.bodySmall,
            color = TextGray
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChipBg, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            listOf("记录", "二级情绪").forEachIndexed { index, text ->
                val on = index == setIdx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (on) CardBg else Color.Transparent)
                        .tap { setIdx = index }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) PrimaryPurple else TextGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("日", "周", "月").forEachIndexed { index, text ->
                FilterChip(
                    selected = rangeIdx == index,
                    onClick = { Haptics.tap(); rangeIdx = index; shift = 0 },
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

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { Haptics.tap(); shift -= 1 }) { Text(prevLabel(rangeIdx)) }
            Text(
                text = range.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            TextButton(onClick = { Haptics.tap(); if (shift < 0) shift += 1 }) { Text(nextLabel(rangeIdx)) }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 一个开关管两张卡：图和「平均与峰值」永远看同一套数据，不会上面看主维度、下面看情绪。
        val showEmotion = setIdx == 1
        val metrics = if (showEmotion) EMOTION_METRICS else primaryMetrics()
        val buckets = if (showEmotion) emotionBuckets else range.buckets
        val labels = if (showEmotion) emotionLabels else range.labels
        val sample = if (showEmotion) emotionRecords else flat
        val emptyText = if (showEmotion) {
            "这段时间一次都没记过二级情绪。不用为了这张图去难受一次。"
        } else {
            "这段时间还没有记录"
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (showEmotion) "二级情绪趋势" else "记录趋势",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (showEmotion) "有 ${emotionRecords.size} 次记了" else "共 $count 次记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGray
                    )
                }
                if (showEmotion) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "只在恐慌 5 分以上才会问到。没记过的日子不在横坐标上，也不画成 0。" +
                            "平均和峰值在下面那张卡里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray,
                        lineHeight = 18.sp
                    )
                }
                if (sample.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = TextGray)
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    MoodChart(
                        series = metrics.map { metric ->
                            ChartSeries(
                                name = metric.name,
                                color = metric.color,
                                values = buckets.map { bucket ->
                                    val values = bucket.mapNotNull(metric.get)
                                    if (values.isEmpty()) null else values.average().toFloat()
                                }
                            )
                        },
                        labels = labels
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    // 图例只报名字。数字在下面「平均与峰值」那张卡里 —— 同一个数写两遍，
                    // 六个情绪各带一个小数，这一排就变成了第二张报表。
                    FlowRow {
                        metrics.forEach { metric ->
                            val points = sample.mapNotNull(metric.get)
                            if (!showEmotion || points.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 12.dp, bottom = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(metric.color)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        metric.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextGray
                                    )
                                }
                            }
                        }
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
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (showEmotion) "二级情绪 · 平均与峰值" else "记录 · 平均与峰值",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (showEmotion) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "只算真的填过的那几次，按平均从高到低排。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (sample.isEmpty()) {
                    Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = TextGray)
                } else {
                    val rows = metrics.map { metric ->
                        val points = sample.mapNotNull(metric.get)
                        val avg = if (points.isEmpty()) 0f else points.average().toFloat()
                        val peak = sample.filter { metric.get(it) != null }
                            .maxByOrNull { metric.get(it) ?: 0 }
                        Triple(metric, avg, peak)
                    }
                    // 二级情绪里一次都没出现过的那几支不占行 —— 列六个空行等于说「你这儿缺了六样」。
                    val ordered = if (showEmotion) {
                        rows.filter { it.third != null }.sortedByDescending { it.second }
                    } else rows
                    ordered.forEach { (metric, avg, peakRecord) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    metric.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = metric.color,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(66.dp)
                                )
                                Text(
                                    "平均 ${formatOneDecimal(avg)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    if (peakRecord == null) "峰值 -"
                                    else "峰值 ${metric.get(peakRecord)} · ${peakRecord.timestamp.logicalTimeString()}" +
                                        if (showEmotion) " · ${sample.count { metric.get(it) != null }} 次" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextGray
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (avg / 10f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = metric.color,
                                trackColor = metric.color.copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (onOpenHistory != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                onClick = onOpenHistory,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "查看全部记录",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryPurple
                        )
                        Text(
                            "点开任意一条，可以补日记、图片、视频或语音",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = PrimaryPurple)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun prevLabel(rangeIdx: Int) = when (rangeIdx) {
    0 -> "前一天"
    1 -> "上一周"
    else -> "上个月"
}

private fun nextLabel(rangeIdx: Int) = when (rangeIdx) {
    0 -> "后一天"
    1 -> "下一周"
    else -> "下个月"
}
