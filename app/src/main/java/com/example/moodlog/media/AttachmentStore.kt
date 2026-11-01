package com.example.moodlog.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

private val IMAGE_EXT = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".heic")
private val VIDEO_EXT = listOf(".mp4", ".mkv", ".webm", ".3gp", ".mov")

/**
 * 附件存储。
 * 目前没有可用的服务器，所以先落在 App 私有目录（filesDir/attachments），不用申请存储权限，
 * 卸载 App 才会清除。以后要接云端，只需要把 AttachmentStore.save 换成上传 + 返回远端 URL，
 * 列表里存的仍然是字符串地址，上层不用改。
 */
object AttachmentStore {

    fun dir(context: Context): File = File(context.filesDir, "attachments").apply { mkdirs() }

    fun save(context: Context, uri: Uri, ext: String): String? {
        return try {
            val name = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}$ext"
            val target = File(dir(context), name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun newAudioFile(context: Context): File =
        File(dir(context), "voice_${System.currentTimeMillis()}.m4a")

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    fun isImage(path: String): Boolean = IMAGE_EXT.any { path.endsWith(it, ignoreCase = true) }
    fun isVideo(path: String): Boolean = VIDEO_EXT.any { path.endsWith(it, ignoreCase = true) }
    fun isAudio(path: String): Boolean = !isImage(path) && !isVideo(path)

    fun mimeOf(path: String): String = when {
        isImage(path) -> "image/*"
        isVideo(path) -> "video/*"
        else -> "audio/*"
    }

    fun extFor(mime: String?): String = when {
        mime == null -> ".bin"
        mime.startsWith("video") -> ".mp4"
        mime.startsWith("audio") -> ".m4a"
        else -> ".jpg"
    }

    /** 图片直接解码缩放，视频取第一帧，音频返回 null（由 UI 画图标）。 */
    fun thumbnail(path: String, size: Int = 240): Bitmap? {
        return try {
            if (isImage(path)) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(path, opts)
            } else if (isVideo(path)) {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(path)
                val frame = retriever.getFrameAtTime(0)
                retriever.release()
                frame
            } else null
        } catch (e: Exception) {
            null
        }
    }
}

class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var output: File? = null

    fun start(): Boolean {
        return try {
            val file = AttachmentStore.newAudioFile(context)
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            output = file
            true
        } catch (e: Exception) {
            recorder = null
            output = null
            false
        }
    }

    /** 停止录音，返回文件路径；失败返回 null。 */
    fun stop(): String? {
        val file = output
        try {
            recorder?.stop()
        } catch (e: Exception) {
            // 录音过短等情况，忽略
        }
        try {
            recorder?.release()
        } catch (e: Exception) {
            // ignore
        }
        recorder = null
        output = null
        return file?.takeIf { it.exists() }?.absolutePath
    }
}

class AudioPlayer {

    var playingPath: String? by mutableStateOf(null)
        private set

    private var player: MediaPlayer? = null

    fun toggle(path: String) {
        if (playingPath == path) stop() else play(path)
    }

    private fun play(path: String) {
        stop()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            player = mp
            playingPath = path
        } catch (e: Exception) {
            player = null
            playingPath = null
        }
    }

    fun stop() {
        try {
            player?.release()
        } catch (e: Exception) {
            // ignore
        }
        player = null
        playingPath = null
    }
}

fun openAttachment(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) {
        Toast.makeText(context, "附件已不存在", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, AttachmentStore.mimeOf(path))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "没有可以打开这个附件的应用", Toast.LENGTH_SHORT).show()
    }
}
