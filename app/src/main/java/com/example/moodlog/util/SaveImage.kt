package com.example.moodlog.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/**
 * 把 app 里的一张图（收款码）存到手机的「图片」目录。
 *
 * 走 MediaStore，不申请任何存储权限：Android 10 以后写到自己的相对目录里就行，
 * 9 及更早的版本用老办法插入公共图片表（那时候需要 WRITE_EXTERNAL_STORAGE，
 * 清单里已经按 maxSdkVersion=28 声明了）。
 *
 * 存下来之后它就在你的相册里，之后怎么用（微信「扫一扫 · 相册」识别之类）由你决定。
 */
fun saveDrawableToGallery(context: Context, resId: Int, title: String): Boolean = runCatching {
    val bmp = BitmapFactory.decodeResource(context.resources, resId)
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$title.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/心锚")
            // 写完之前先挂起：中途失败不会在相册里留一张半张的图。
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return false
    val wrote = resolver.openOutputStream(uri)?.use {
        bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
    } ?: false
    if (!wrote) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
    true
}.getOrDefault(false)
