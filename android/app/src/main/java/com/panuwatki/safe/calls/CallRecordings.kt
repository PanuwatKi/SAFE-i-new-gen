package com.panuwatki.safe.calls

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

data class Recording(val uri: Uri, val displayName: String, val addedAtMs: Long, val durationMs: Long)

/**
 * หาไฟล์ "อัดเสียงสาย" ที่แอปโทรศัพท์ของเครื่องสร้างขึ้นใหม่
 * (SAFE อัดเสียงสายเองไม่ได้ เพราะ Android ไม่อนุญาต จึงใช้ไฟล์ที่ระบบอัดไว้แทน)
 */
object CallRecordings {

    /** เส้นทางโฟลเดอร์ที่ยี่ห้อต่าง ๆ ใช้เก็บไฟล์อัดเสียงสาย (ตัวพิมพ์เล็ก) */
    private val pathHints = listOf(
        "recordings/call",       // Samsung One UI 4+
        "/call/",                // Samsung รุ่นเก่า, vivo (Record/Call)
        "call_rec",              // Xiaomi / Redmi / POCO (MIUI/sound_recorder/call_rec)
        "call recordings",       // OPPO / realme / OnePlus (ColorOS)
        "callrecord",
        "phonerecord",
        "บันทึกการโทร",
    )

    /** ไฟล์สั้นกว่านี้ (เช่น สายที่ไม่ได้รับ) ไม่ต้องวิเคราะห์ */
    const val MIN_DURATION_MS = 5_000L

    fun isCallRecording(relativePathOrData: String, displayName: String): Boolean {
        val s = ("/" + relativePathOrData + "/" + displayName).lowercase()
        return pathHints.any { s.contains(it) }
    }

    fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

    /** ใช้ในหน้าตั้งค่า: เช็กว่า SAFE มองเห็นไฟล์อัดเสียงสายของเครื่องนี้ไหม (ย้อนหลังกี่วัน) */
    fun countRecent(context: Context, days: Int): Int =
        findNewSince(context, System.currentTimeMillis() - days * 24L * 60 * 60 * 1000).size

    fun findNewSince(context: Context, sinceMs: Long): List<Recording> {
        if (!hasPermission(context)) return emptyList()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION") MediaStore.MediaColumns.DATA
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION,
            pathColumn,
        )
        val out = mutableListOf<Recording>()
        context.contentResolver.query(
            collection, projection,
            "${MediaStore.Audio.Media.DATE_ADDED} > ?", arrayOf((sinceMs / 1000).toString()),
            "${MediaStore.Audio.Media.DATE_ADDED} ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathCol = c.getColumnIndexOrThrow(pathColumn)
            while (c.moveToNext()) {
                val name = c.getString(nameCol) ?: ""
                val path = c.getString(pathCol) ?: ""
                val duration = c.getLong(durCol)
                if (!isCallRecording(path, name) || duration in 1 until MIN_DURATION_MS) continue
                out += Recording(
                    uri = ContentUris.withAppendedId(collection, c.getLong(idCol)),
                    displayName = name,
                    addedAtMs = c.getLong(addedCol) * 1000,
                    durationMs = duration,
                )
            }
        }
        return out
    }
}
