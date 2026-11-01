package com.example.moodlog.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.moodlog.data.ConfirmLabel
import com.example.moodlog.data.EMOTION_FIELDS
import com.example.moodlog.data.IMPULSE_LABELS
import com.example.moodlog.data.LIFE_DIMS
import com.example.moodlog.data.LifeDims
import com.example.moodlog.data.LifeEvent
import com.example.moodlog.data.MoodRecord
import com.example.moodlog.data.Move
import com.example.moodlog.data.OUTCOME_LABELS
import com.example.moodlog.data.labels
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * 把 A 空间（情绪）的全部记录导出成 CSV 到「下载」目录，返回文件名；失败返回 null。
 * Android 10 以上通过 MediaStore 写入，不需要存储权限。
 */
fun exportCsv(context: Context, records: List<MoodRecord>): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val name = "心锚_情绪_${System.currentTimeMillis()}.csv"
    // 「日期」是早上六点起算的逻辑日，「实际时间」是挂钟时间 —— 给别人看的时候 26:26 会造成困惑。
    val header = listOf(
        "时间戳", "日期", "逻辑时间", "实际日期", "实际时间",
        "想TA", "想联系", ConfirmLabel.name, "恐慌",
        *EMOTION_FIELDS.map { it.first }.toTypedArray(),
        "自己加的情绪",
        "冲动等级", "出口", "慌起来的时刻", "回答出口的时刻",
        "从慌到出口(分钟)", "我安全了的时刻", "从求助到安全(分钟)",
        "日记"
    )
    return writeCsv(context, name, header) { w ->
        records.sortedBy { it.timestamp }.forEach { r ->
            val dt = r.timestamp.toLocalDateTime()
            val e = r.emotions
            val row = listOf(
                r.timestamp.toString(),
                r.timestamp.logicalDate().toString(),
                r.timestamp.logicalTimeString(),
                dt.toLocalDate().toString(),
                dt.format(TIME_FMT),
                if (r.miss >= 0) r.miss.toString() else "",
                if (r.contact >= 0) r.contact.toString() else "",
                if (r.confirm >= 0) r.confirm.toString() else "",
                if (r.panic >= 0) r.panic.toString() else "",
                // 没答的那一支留空 —— 空是「当时没问」，0 是「当时是 0 分」，两件事不能混。
                *EMOTION_FIELDS.map { (_, get) -> e?.let { get(it) }?.toString() ?: "" }.toTypedArray(),
                csv(r.customEmotions.joinToString(" ") { "${it.first} ${it.second}" }),
                r.impulse?.let { IMPULSE_LABELS.getOrNull(it) ?: "" } ?: "",
                OUTCOME_LABELS[r.outcome] ?: "",
                r.episodeAt?.toFullString() ?: "",
                r.outcomeAt?.toFullString() ?: "",
                r.toOutcomeMinutes?.toString() ?: "",
                r.safeAt?.toFullString() ?: "",
                r.toSafeMinutes?.toString() ?: "",
                csv(r.diary)
            )
            w.write(row.joinToString(","))
            w.write("\n")
        }
    }
}

/** 把 B 空间（生活 / 成为）导出成两个段落的 CSV。 */
fun exportLifeCsv(
    context: Context,
    events: List<LifeEvent>,
    dims: List<LifeDims>,
    moves: List<Move> = emptyList()
): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val name = "心锚_生活_${System.currentTimeMillis()}.csv"
    return writeCsv(context, name, emptyList()) { w ->
        w.write("【做了什么】\n")
        w.write(
            (listOf("时间戳", "日期", "逻辑时间", "标签", "升降", "备注"))
                .joinToString(",") + "\n"
        )
        events.sortedBy { it.timestamp }.forEach { e ->
            listOf(
                e.timestamp.toString(),
                e.timestamp.logicalDate().toString(),
                e.timestamp.logicalTimeString(),
                csv(e.tags.joinToString(" ")),
                e.delta.toString(),
                csv(e.note)
            ).let { w.write(it.joinToString(",") + "\n") }
        }

        w.write("\n【运动】\n")
        w.write(
            (listOf("时间戳", "日期", "逻辑时间", "类型", "分钟", "备注"))
                .joinToString(",") + "\n"
        )
        moves.sortedBy { it.timestamp }.forEach { m ->
            listOf(
                m.timestamp.toString(),
                m.timestamp.logicalDate().toString(),
                m.timestamp.logicalTimeString(),
                csv(m.tag),
                if (m.minutes > 0) m.minutes.toString() else "",
                csv(m.note)
            ).let { w.write(it.joinToString(",") + "\n") }
        }

        w.write("\n【六个维度】\n")
        w.write(
            (listOf("时间戳", "日期", "逻辑时间") + LIFE_DIMS.map { it.name })
                .joinToString(",") + "\n"
        )
        dims.sortedBy { it.timestamp }.forEach { d ->
            listOf(
                d.timestamp.toString(),
                d.timestamp.logicalDate().toString(),
                d.timestamp.logicalTimeString()
            ).plus(LIFE_DIMS.map { it.get(d).toString() })
                .let { w.write(it.joinToString(",") + "\n") }
        }
    }
}

private fun writeCsv(
    context: Context,
    name: String,
    header: List<String>,
    body: (BufferedWriter) -> Unit
): String? {
    return try {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null

        resolver.openOutputStream(uri)?.use { stream ->
            stream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            BufferedWriter(OutputStreamWriter(stream, StandardCharsets.UTF_8)).use { w ->
                if (header.isNotEmpty()) {
                    w.write(header.joinToString(","))
                    w.write("\n")
                }
                body(w)
                w.flush()
            }
        }

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        name
    } catch (e: Exception) {
        null
    }
}

private fun csv(text: String): String {
    val flat = text.replace("\r", " ").replace("\n", " ")
    return "\"" + flat.replace("\"", "\"\"") + "\""
}
