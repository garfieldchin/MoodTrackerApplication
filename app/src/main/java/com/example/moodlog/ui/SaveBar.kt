package com.example.moodlog.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.moodlog.ui.theme.GainGreen
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.moodlog.ui.theme.PrimaryPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SavedColor: Color get() = GainGreen

/**
 * 存东西的按钮。按下去自己矮一下，然后字变成「记下了 ✓」，一会儿再变回去。
 *
 * 为什么要这么啰嗦：按「存下这一句」的时候屏幕上什么都没变，
 * 你会怀疑它到底存没存，于是再按一次 —— 存两次没事，怀疑的那一下有事。
 *
 * onClick 返回「真的存成了吗」。没填东西就返回 false，按钮不给任何反馈 ——
 * 什么都不动比假装成功诚实：假装成功了，人就不看为什么没存上。
 */
@Composable
fun SaveBar(
    label: String,
    savedLabel: String,
    savedCount: Int,
    onClick: () -> Boolean
) {
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var justSaved by remember { mutableStateOf(false) }

    // 每存一次就把序号推进一格，这里收到信号就亮一下再灭掉。
    LaunchedEffect(savedCount) {
        if (savedCount > 0) {
            justSaved = true
            delay(1600)
            justSaved = false
        }
    }

    Button(
        onClick = {
                Haptics.tap()
            if (onClick()) {
                scope.launch {
                    press.animateTo(0.95f, tween(90))
                    press.animateTo(1f, tween(170, easing = LinearOutSlowInEasing))
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer { scaleX = press.value; scaleY = press.value },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (justSaved) SavedColor else PrimaryPurple
        )
    ) {
        Text(
            if (justSaved) "$savedLabel ✓" else label,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}
