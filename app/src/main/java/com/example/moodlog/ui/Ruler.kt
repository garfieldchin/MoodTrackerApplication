package com.example.moodlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moodlog.ui.theme.*
import com.example.moodlog.ui.theme.TextGray
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val THUMB = 20.dp
private val RAIL_H = 6.dp
private val TICK_TOP = 20.dp

/**
 * 一把自己画的尺。
 *
 * 之前是「系统滑块 + 下面一排刻度」两件东西拼在一起：滑块为了给两端的圆点留位置，
 * 轨道比整行短一截；下面那排刻度却把整行十一等分。两套坐标，越往两边偏得越明显 ——
 * 于是「条子和刻度对不上」。
 *
 * 这里的轨道、刻度、数字、圆点全由同一个宽度算出来，点数字和拖圆点落在同一个点。
 * 拖动判定放宽到整行高度：手指落在数字上也能拖，不必精准命中那条 6dp 的轨道。
 */
@Composable
fun ScaleRuler(
    value: Float,
    color: Color,
    enabled: Boolean = true,
    /** 还没答的时候不画圆点：圆点停在 0 位会被读成「我打了 0 分」。 */
    showThumb: Boolean = true,
    onPick: (Float) -> Unit
) {
    RulerBase(
        min = 0,
        max = 10,
        value = value,
        activeColor = if (enabled) color else DisabledC,
        trackColor = color.copy(alpha = 0.18f),
        enabled = enabled,
        showThumb = showThumb,
        marks = (0..10).toList(),
        majorStep = 2,
        zeroLine = false,
        onPick = onPick
    )
}

/** −10 ~ +10 的能量尺。0 在中点，往左是消耗色，往右是充电色。 */
@Composable
fun SignedRuler(
    value: Float,
    gainColor: Color,
    lossColor: Color,
    onPick: (Float) -> Unit
) {
    val cur = value.roundToInt().coerceIn(-10, 10)
    RulerBase(
        min = -10,
        max = 10,
        value = value,
        activeColor = if (cur >= 0) gainColor else lossColor,
        trackColor = CardBorder,
        enabled = true,
        marks = (-10..10).toList(),
        majorStep = 5,
        zeroLine = true,
        onPick = onPick
    )
}

@Suppress("LongMethod")
@Composable
private fun RulerBase(
    min: Int,
    max: Int,
    value: Float,
    activeColor: Color,
    trackColor: Color,
    enabled: Boolean,
    marks: List<Int>,
    majorStep: Int,
    zeroLine: Boolean,
    showThumb: Boolean = true,
    onPick: (Float) -> Unit
) {
    val density = LocalDensity.current
    val cur = value.roundToInt().coerceIn(min, max)
    // 点中一格 / 拖过一格，整把尺子亮一下。看得见的「这一下点到了」。
    val pulse = rememberPulse()
    val lit = pulse.alpha(360)

    fun pick(x: Float, widthPx: Int) {
        onPick(valueAt(x, min, max, density, widthPx))
        pulse.bump()
        Haptics.pick()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(
                color = blend(Color.Transparent, activeColor, 0.22f * lit),
                shape = RoundedCornerShape(8.dp)
            )
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(min, max) {
                            detectTapGestures { offset -> pick(offset.x, size.width) }
                        }
                        .pointerInput(min, max) {
                            detectDragGestures(
                                onDragStart = { pick(it.x, size.width) },
                                onDrag = { change, _ -> pick(change.position.x, size.width) }
                            )
                        }
                } else Modifier
            )
    ) {
        val padPx = with(density) { (THUMB / 2).toPx() }
        val railPx = (with(density) { maxWidth.toPx() } - padPx * 2).coerceAtLeast(1f)

        fun xOf(v: Float): Float {
            val t = ((v - min).toFloat() / (max - min).toFloat()).coerceIn(0f, 1f)
            return padPx + t * railPx
        }
        fun dp(v: Float) = with(density) { v.toDp() }

        // 轨道
        Box(
            modifier = Modifier
                .offset(x = dp(padPx), y = 6.dp)
                .width(dp(railPx))
                .height(RAIL_H)
                .clip(RoundedCornerShape(3.dp))
                .background(trackColor)
        )

        // 已经走过的那一段：0~10 从左端起，−10~+10 从中点起
        val anchor = if (zeroLine) xOf(0f) else padPx
        val end = xOf(cur.toFloat())
        val left = minOf(anchor, end)
        val width = (end - anchor).absoluteValue
        if (width > 0.5f) {
            Box(
                modifier = Modifier
                    .offset(x = dp(left), y = 6.dp)
                    .width(dp(width))
                    .height(RAIL_H)
                    .clip(RoundedCornerShape(3.dp))
                    .background(activeColor)
            )
        }

        // 中点线：只有带正负的尺才有
        if (zeroLine) {
            Box(
                modifier = Modifier
                    .offset(x = dp(xOf(0f)) - 1.dp, y = 2.dp)
                    .width(2.dp)
                    .height(14.dp)
                    .background(DisabledC)
            )
        }

        // 刻度与数字
        marks.forEach { v ->
            val major = (v - min) % majorStep == 0
            val picked = v == cur
            if (major || picked || majorStep <= 2) {
                Box(
                    modifier = Modifier
                        .offset(x = dp(xOf(v.toFloat())) - 0.75.dp, y = TICK_TOP)
                        .width(if (picked) 2.dp else 1.5.dp)
                        .height((if (major) 8.dp else 5.dp) + if (picked) 2.dp else 0.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(
                            if (picked) activeColor
                            else if (major) DisabledC
                            else CardBorder
                        )
                )
            }
            if (major || picked) {
                Text(
                    text = v.toString(),
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    color = if (picked) activeColor else TextGray,
                    fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .offset(x = dp(xOf(v.toFloat())) - 8.dp, y = 29.dp)
                        .width(16.dp)
                )
            }
        }

        // 圆点。未答时整颗不画 —— 一颗停在 0 位的灰圆点，看着就是一个 0 分。
        if (showThumb) {
            val thumb = THUMB + 6.dp * lit
            Box(
                modifier = Modifier
                    .offset(x = dp(xOf(cur.toFloat())) - thumb / 2, y = 0.dp)
                    .size(thumb)
                    .clip(CircleShape)
                    .background(activeColor),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(thumb - 6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

private fun valueAt(
    x: Float,
    min: Int,
    max: Int,
    density: androidx.compose.ui.unit.Density,
    widthPx: Int
): Float {
    val padPx = with(density) { (THUMB / 2).toPx() }
    val railPx = (widthPx.toFloat() - padPx * 2).coerceAtLeast(1f)
    val t = ((x - padPx) / railPx).coerceIn(0f, 1f)
    return (min + t * (max - min)).roundToInt().coerceIn(min, max).toFloat()
}
