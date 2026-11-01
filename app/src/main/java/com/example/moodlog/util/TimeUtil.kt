package com.example.moodlog.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val MD_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")
val FULL_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
val DATE_CN_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

/**
 * 一天的起点。早上六点以前，算前一天 —— 因为最难熬的那几个小时几乎都发生在那里，
 * 按零点切开的话，一整夜会被剁成两个孤立的小日子，在图上看不出形状。
 *
 * 六点这个位置是使用者自己定的：熬到那边躺下，睡一觉起来算新的一天。
 * 显示方式是时间继续往上数：02:26 → 26:26，最晚到 29:59。
 */
const val DAY_START_HOUR = 6

fun Long.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

/** 真实的挂钟时间。CSV 里保留一列它，给别人看的时候 26:26 会造成困惑。 */
fun Long.toTimeString(): String = toLocalDateTime().format(TIME_FMT)

fun Long.toFullString(): String = toLocalDateTime().format(FULL_FMT)

/** 逻辑日：06:00 及以后算当天，06:00 之前算前一天。全站统一用它。 */
fun Long.logicalDate(): LocalDate = toLocalDateTime().minusHours(DAY_START_HOUR.toLong()).toLocalDate()

/** 逻辑日里的累进时间：06:00 之前加 24 小时显示，所以会看到 24:36 / 26:26 / 29:59。 */
fun Long.logicalTimeString(): String {
    val dt = toLocalDateTime()
    val hour = if (dt.hour < DAY_START_HOUR) dt.hour + 24 else dt.hour
    return String.format("%02d:%02d", hour, dt.minute)
}

/** 这一刻属于哪个逻辑日。 */
fun logicalToday(): LocalDate =
    LocalDateTime.now().minusHours(DAY_START_HOUR.toLong()).toLocalDate()

fun Long.toLocalDate(): LocalDate = toLocalDateTime().toLocalDate()

/**
 * 深色模式的时段：每天 18:00 到次日的 05:59 —— 用累进时间说，就是 18:00 – 29:59。
 * 和逻辑日共用同一条缝：五点五十九分还属于昨天，屏幕也还属于夜里的那套颜色。
 */
fun isDarkTime(): Boolean {
    val h = LocalDateTime.now().hour
    return h >= 18 || h < DAY_START_HOUR
}
