package com.example.moodlog.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.moodlog.data.DayDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把一天的总结画成一张长图。
 *
 * 为什么是画，不是截屏：
 * 1) Compose 的 Lazy 列表只画屏幕上那几项，系统滚动截屏会只截到一屏；
 * 2) 就算整页是全量渲染的，也还带着状态栏、导航栏和那一排底部菜单。
 * 直接用 Canvas 画一遍，出来的才是「一张干净的、可以发给谁看的」东西。
 *
 * 它画的还是那些话：不打分、不评级、不发徽章，数据少就少画。
 */
object LongImage {

    private const val W = 1080
    private const val PAD = 64f
    private const val CARD_PAD = 40f
    private const val CARD_GAP = 26f
    private const val ROW_GAP = 14f
    private const val RADIUS = 34f

    private const val INK = 0xFF2B2733.toInt()
    private const val GRAY = 0xFF5C5666.toInt()
    private const val LIGHT = 0xFF8A8395.toInt()
    private const val PURPLE = 0xFF8B6FB5.toInt()
    private const val PAGE = 0xFFFAF8FC.toInt()
    private const val CARD_BG = 0xFFFFFFFF.toInt()
    private const val CARD_LINE = 0xFFEDE7F3.toInt()

    fun render(d: DayDigest): Bitmap {
        val inner = (W - PAD * 2).toInt()
        val textW = (inner - CARD_PAD * 2).toInt()

        val title = layout(d.dayLabel, Style.TITLE, inner)

        val cards = mutableListOf<Card>()
        fun card(label: String?, entries: List<Pair<Style, String>>) {
            if (entries.isEmpty()) return
            val rows = mutableListOf<StaticLayout>()
            if (label != null) rows += layout(label, Style.LABEL, textW)
            entries.forEach { (s, t) -> rows += layout(t, s, textW) }
            cards += Card(rows)
        }

        card(null, listOfNotNull(d.headline.takeIf { it.isNotBlank() }?.let { Style.HEAD to it }))
        card("情绪", one(d.emotion, Style.BODY))
        card("生活", one(d.life, Style.BODY))
        card("我话事 · 这一天做了什么", d.charges.map { Style.BULLET to it })
        card("运动", d.moves.map { Style.BULLET to it })
        card("我遇见 · 那天说的几句话", d.meets.map { Style.BULLET to it })
        card("离我想成为的那个人", one(d.distance, Style.BODY))
        card("今天记下的几次", d.timeline.map { Style.BULLET to it })
        card("当时写的", d.diaries.map { Style.BULLET to it })
        card("打卡那一栏以前记下的", d.checkins.map { Style.BULLET to it })
        card("给这天留的一句", one(d.note, Style.QUOTE))
        card("我想成为的那个人", one(d.goal, Style.BODY))

        val footer = layout("心锚 · 这是只给你自己看的地方", Style.FOOT, inner)

        var total = PAD + title.height + 34f
        cards.forEachIndexed { i, c ->
            if (i > 0) total += CARD_GAP
            total += c.height()
        }
        total += 34f + footer.height + PAD

        val bmp = Bitmap.createBitmap(W, total.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(PAGE)

        var y = PAD
        canvas.withSave {
            translate(PAD, y)
            title.draw(this)
        }
        y += title.height + 34f

        val rect = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = CARD_LINE
        }

        cards.forEach { c ->
            val h = c.height()
            val box = RectF(PAD, y, W - PAD, y + h)
            rect.color = CARD_BG
            canvas.drawRoundRect(box, RADIUS, RADIUS, rect)
            canvas.drawRoundRect(box, RADIUS, RADIUS, line)
            var ry = y + CARD_PAD
            c.rows.forEach { row ->
                canvas.withSave {
                    translate(PAD + CARD_PAD, ry)
                    row.draw(this)
                }
                ry += row.height + ROW_GAP
            }
            y += h + CARD_GAP
        }

        canvas.withSave {
            translate(PAD, total - PAD - footer.height)
            footer.draw(this)
        }
        return bmp
    }

    /**
     * 存到相册里的「心锚」目录。
     * Android 10 起走 MediaStore —— 不用申请存储权限；
     * 更老的版本拿不到权限就不存，明确告诉界面存不成，不假装成功。
     */
    fun save(context: Context, bmp: Bitmap, dayLabel: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(Date())
        val name = "心锚_${dayLabel.replace("\\s".toRegex(), "")}_$stamp.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/心锚")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            name
        }.getOrNull()
    }

    // ------------------------------------------------------------ 排版

    private fun one(text: String, style: Style): List<Pair<Style, String>> =
        text.takeIf { it.isNotBlank() }?.let { listOf(style to it) } ?: emptyList()

    private class Card(val rows: List<StaticLayout>) {
        fun height(): Float {
            val sum = rows.sumOf { it.height } + ROW_GAP * (rows.size - 1).coerceAtLeast(0)
            return sum + CARD_PAD * 2
        }
    }

    private enum class Style(val size: Int, val color: Int, val bold: Boolean) {
        TITLE(52, INK, true),
        HEAD(32, INK, false),
        LABEL(24, PURPLE, true),
        BODY(28, GRAY, false),
        BULLET(26, GRAY, false),
        QUOTE(30, PURPLE, false),
        FOOT(22, LIGHT, false)
    }

    private fun paint(style: Style): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.color
        textSize = style.size.toFloat()
        typeface = if (style.bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    private fun layout(text: String, style: Style, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint(style), width)
            .setLineSpacing(10f, 1.16f)
            .setAlignment(
                if (style == Style.FOOT || style == Style.TITLE) Layout.Alignment.ALIGN_CENTER
                else Layout.Alignment.ALIGN_NORMAL
            )
            .build()

    private inline fun Canvas.withSave(block: Canvas.() -> Unit) {
        val n = save()
        try {
            block()
        } finally {
            restoreToCount(n)
        }
    }
}
