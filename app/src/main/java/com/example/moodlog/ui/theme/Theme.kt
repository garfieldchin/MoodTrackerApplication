package com.example.moodlog.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.example.moodlog.data.NightPrefs
import com.example.moodlog.util.isDarkTime

/**
 * 行距在这里一次定死，页面上不再各写各的。
 *
 * 以前同一句话（bodySmall）有的地方 16、有的 18、有的干脆不写 ——
 * 三行说明文字摆在一起，行距不一样，看着就像三种东西。
 * 现在每级字号配一个固定行距：正文字号 ÷ 行距大约是 1.5，
 * 中文挤一点会糊，松一点会散，这个比例是看得最久的那个。
 */
private val AppTypography = Typography(
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp)
)

/**
 * 两套色板都在组合期现做 —— 里面的语义色（PageBg / TextMain …）本身是随 NightMode
 * 翻转的 getter，写在顶层 val 里会把白天的颜色焊死。夜间 scheme 不另起炉灶：
 * 容器类颜色白天给什么夜间也给什么，真正的深浅由语义色自己决定。
 */
private fun lightScheme() = lightColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF1EAFA),
    onPrimaryContainer = TextMain,
    secondary = SoftPurple,
    onSecondary = Color.White,
    background = PageBg,
    onBackground = TextMain,
    surface = CardBg,
    onSurface = TextMain,
    surfaceVariant = ChipBg,
    onSurfaceVariant = TextGray,
    outline = CardBorder
)

private fun darkScheme() = darkColorScheme(
    primary = SoftPurple,
    onPrimary = Color(0xFF1B1723),
    primaryContainer = Color(0xFF3A3148),
    onPrimaryContainer = TextMain,
    secondary = PrimaryPurple,
    onSecondary = Color(0xFF1B1723),
    background = PageBg,
    onBackground = TextMain,
    surface = CardBg,
    onSurface = TextMain,
    surfaceVariant = ChipBg,
    onSurfaceVariant = TextGray,
    outline = CardBorder
)

/**
 * 按当前那一档决定现在是夜里还是白天。
 *
 * 自动档读时段（18:00–29:59）；钉住的两档直接给答案。
 * 换档之后要立刻生效，所以设置里改完会直接调一次这个 ——
 * 不用等 MainActivity 下一次对表（那是半分钟一次）。
 */
fun applyNightMode(context: Context) {
    NightMode.mode = NightPrefs.get(context)
    NightMode.isDark = when (NightMode.mode) {
        NightMode.ON -> true
        NightMode.OFF -> false
        else -> isDarkTime()
    }
}

@Composable
fun MoodLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (NightMode.isDark) darkScheme() else lightScheme(),
        typography = AppTypography,
        content = content
    )
}
