package com.example.moodlog.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import androidx.documentfile.provider.DocumentFile

/**
 * 云备份：把本地数据库加密后，写到你自己选的「云盘同步文件夹」（比如 Google Drive 在手机上的同步目录）。
 *
 * 设计取舍：
 * - 不联网、不加任何网络权限 —— 数据只经过这台手机的剪贴板和文件系统，完全本地完成。
 * - 加密密钥由「你设置的备份密码」派生（PBKDF2），不依赖设备。所以换手机、重装后，
 *   只要有密码就能恢复，备份文件落到任何云盘都安全。
 * - 自动备份用的密码存在 AndroidKeyStore 里（设备绑定），仅用于「免输密码的每日备份」这一便利；
 *   备份文件本身不靠它，靠你的密码。
 */

private const val MAGIC = "XINMAO1"
private const val PBKDF2_ITER = 120_000
private const val PREFS = "xinmao_backup"

/** AES-GCM 加解密。明文进、密文出；密文进、明文出。密码错或文件被改会抛异常。 */
object BackupCrypto {
    fun encrypt(src: File, pass: String): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveKey(pass, salt), "AES"), GCMParameterSpec(128, iv))
        }
        val plain = src.readBytes()
        val ct = cipher.doFinal(plain)
        return ByteArrayOutputStream().apply {
            write(MAGIC.toByteArray(Charsets.US_ASCII))
            write(salt)
            write(iv)
            write(ct)
        }.toByteArray()
    }

    fun decrypt(data: ByteArray, pass: String): ByteArray {
        val `in` = ByteArrayInputStream(data)
        val magic = ByteArray(MAGIC.length).also { `in`.read(it) }
        require(magic.contentEquals(MAGIC.toByteArray(Charsets.US_ASCII))) { "这个文件不是本应用生成的备份。" }
        val salt = ByteArray(16).also { `in`.read(it) }
        val iv = ByteArray(12).also { `in`.read(it) }
        val ct = `in`.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(deriveKey(pass, salt), "AES"), GCMParameterSpec(128, iv))
        }
        return cipher.doFinal(ct) // 密码错 → AEADBadTagException
    }

    private fun deriveKey(pass: String, salt: ByteArray): ByteArray {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return f.generateSecret(PBEKeySpec(pass.toCharArray(), salt, PBKDF2_ITER, 256)).encoded
    }
}

/** 把自动备份用的密码用 AndroidKeyStore 加密后存在私有 SharedPreferences 里。设备绑定，重装后需重设。 */
object PassphraseStore {
    private const val ALIAS = "xinmao_backup_pass"

    fun save(context: Context, pass: String) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val enc = cipher.doFinal(pass.toByteArray(Charsets.UTF_8))
        prefs(context).edit {
            putString("pass_enc", Base64.encodeToString(enc, Base64.NO_WRAP))
            putString("pass_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
        }
    }

    fun load(context: Context): String? {
        val enc = prefs(context).getString("pass_enc", null) ?: return null
        val iv = prefs(context).getString("pass_iv", null) ?: return null
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        return String(cipher.doFinal(Base64.decode(enc, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    fun clear(context: Context) = prefs(context).edit { remove("pass_enc"); remove("pass_iv") }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(ALIAS)) return ks.getKey(ALIAS, null) as SecretKey
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }
}

/** 自动备份的配置：选中的文件夹 URI、是否开启、上次备份时间。 */
object BackupPrefs {
    fun folderUri(context: Context): Uri? {
        val s = prefs(context).getString("folder_uri", null) ?: return null
        return Uri.parse(s)
    }
    fun setFolderUri(context: Context, uri: Uri?) {
        prefs(context).edit {
            if (uri == null) remove("folder_uri") else putString("folder_uri", uri.toString())
        }
    }
    fun autoBackup(context: Context): Boolean = prefs(context).getBoolean("auto", false)
    fun setAutoBackup(context: Context, on: Boolean) = prefs(context).edit { putBoolean("auto", on) }
    fun lastBackupTime(context: Context): Long = prefs(context).getLong("last", 0L)
    fun setLastBackupTime(context: Context, t: Long) = prefs(context).edit { putLong("last", t) }
}

object BackupManager {
    /** 把加密后的数据库写到用户选的文件夹，返回生成的文件名。 */
    fun writeEncryptedToTree(context: Context, treeUri: Uri, data: ByteArray): String {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("选中的文件夹打不开，请重新选择。")
        val name = "xinmao_backup_" +
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()) + ".enc"
        val file = tree.createFile("application/octet-stream", name)
            ?: throw IllegalStateException("没法在这个文件夹创建文件（可能没写权限）。")
        context.contentResolver.openOutputStream(file.uri)?.use { it.write(data) }
            ?: throw IllegalStateException("写不进去，换个文件夹试试。")
        return name
    }

    /** 读取用户选中的备份文件，返回原始密文字节。 */
    fun readEncryptedUri(context: Context, uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("读不到这个备份文件。")
    }

    /** 把解密后的数据库字节写回 App 私有目录，覆盖现有库；同时清掉 -wal/-shm 防止脏读。 */
    fun replaceDatabase(dbFile: File, plain: ByteArray) {
        val dir = dbFile.parentFile ?: throw IllegalStateException("找不到数据库目录。")
        File(dir, dbFile.name + "-wal").delete()
        File(dir, dbFile.name + "-shm").delete()
        dbFile.writeBytes(plain)
    }

    /** 启动时按需自动备份：开了开关、选了文件夹、存了密码、且距上次超过 24 小时才做。 */
    fun autoBackupIfNeeded(context: Context, repo: MoodRepository) {
        if (!BackupPrefs.autoBackup(context)) return
        val folderUri = BackupPrefs.folderUri(context) ?: return
        val pass = PassphraseStore.load(context) ?: return
        if (System.currentTimeMillis() - BackupPrefs.lastBackupTime(context) < 24 * 3600_000L) return
        val db = repo.getDbFile()
        if (!db.exists()) return
        repo.flushWal()
        val enc = BackupCrypto.encrypt(db, pass)
        writeEncryptedToTree(context, folderUri, enc)
        BackupPrefs.setLastBackupTime(context, System.currentTimeMillis())
    }
}

private fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
