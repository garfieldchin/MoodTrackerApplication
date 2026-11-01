package com.example.moodlog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moodlog.ui.theme.GridColor
import com.example.moodlog.ui.theme.TextGray
import kotlin.math.roundToInt

data class ChartSeries(
    val name: String,
    val color: Color,
    val values: List<Float?> // 与横坐标一一对应，null 表示该点没有数据
)

@Composable
fun MoodChart(
    series: List<ChartSeries>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = TextGray)
    val gridStyle = TextStyle(fontSize = 10.sp, color = TextGray)
    val labelLayouts = remember(labels) { labels.map { measurer.measure(it, labelStyle) } }
    val gridLayouts = remember { (0..10 step 2).map { measurer.measure(it.toString(), gridStyle) } }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        val leftPad = 26.dp.toPx()
        val rightPad = 10.dp.toPx()
        val topPad = 14.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad
        if (chartW <= 0f || chartH <= 0f) return@Canvas

        // 横向网格与纵坐标
        (0..10 step 2).forEachIndexed { index, v ->
            val y = topPad + chartH * (1f - v / 10f)
            drawLine(
                color = GridColor,
                start = Offset(leftPad, y),
                end = Offset(leftPad + chartW, y),
                strokeWidth = 1f
            )
            drawText(
                textLayoutResult = gridLayouts[index],
                topLeft = Offset(leftPad - gridLayouts[index].size.width - 6.dp.toPx(), y - gridLayouts[index].size.height / 2f)
            )
        }

        val n = labels.size
        if (n == 0) return@Canvas
        val xAt: (Int) -> Float = { i ->
            if (n == 1) leftPad + chartW / 2f else leftPad + chartW * i / (n - 1)
        }

        series.forEach { s ->
            var prevX: Float? = null
            var prevY: Float? = null
            s.values.forEachIndexed { i, v ->
                if (v == null) {
                    prevX = null
                    prevY = null
                } else {
                    val x = xAt(i)
                    val y = topPad + chartH * (1f - (v / 10f).coerceIn(0f, 1f))
                    val px = prevX
                    val py = prevY
                    if (px != null && py != null) {
                        drawLine(
                            color = s.color,
                            start = Offset(px, py),
                            end = Offset(x, y),
                            strokeWidth = 4f,
                            cap = StrokeCap.Round
                        )
                    }
                    drawCircle(color = s.color, radius = 5f, center = Offset(x, y))
                    drawCircle(color = Color.White, radius = 2.2f, center = Offset(x, y), style = Stroke(width = 2f))
                    prevX = x
                    prevY = y
                }
            }
        }

        // 只画首、中、尾三个横坐标标签，避免拥挤
        val idxToDraw = listOf(0, n / 2, n - 1).distinct()
        idxToDraw.forEach { i ->
            val layout = labelLayouts.getOrNull(i) ?: return@forEach
            val x = (xAt(i) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(textLayoutResult = layout, topLeft = Offset(x, size.height - bottomPad + 6.dp.toPx()))
        }
    }
}

fun formatOneDecimal(v: Float): String {
    val scaled = (v * 10).roundToInt()
    return "${scaled / 10}.${scaled % 10}"
}
