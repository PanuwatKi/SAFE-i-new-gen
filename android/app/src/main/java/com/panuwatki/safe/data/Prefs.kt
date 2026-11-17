package com.panuwatki.safe.data

import android.content.Context
import androidx.core.content.edit

/** การตั้งค่าของผู้ใช้ (เก็บในเครื่อง) */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("safe_prefs", Context.MODE_PRIVATE)

    /** เวอร์ชันของข้อความยินยอม — ถ้าแก้นโยบายสาระสำคัญ ให้เพิ่มเลขนี้ ผู้ใช้จะต้องยอมรับใหม่ */
    var consentVersion: Int
        get() = sp.getInt(KEY_CONSENT, 0)
        set(v) = sp.edit { putInt(KEY_CONSENT, v) }

    val hasConsent: Boolean get() = consentVersion >= CURRENT_CONSENT_VERSION

    var numberWarningEnabled: Boolean
        get() = sp.getBoolean(KEY_NUMBER_WARNING, true)
        set(v) = sp.edit { putBoolean(KEY_NUMBER_WARNING, v) }

    var autoRecordingCheckEnabled: Boolean
        get() = sp.getBoolean(KEY_AUTO_RECORDING, false)
        set(v) = sp.edit { putBoolean(KEY_AUTO_RECORDING, v) }

    /** เวลาล่าสุดที่สแกนหาไฟล์อัดเสียงสาย (ms) — ใช้หาเฉพาะไฟล์ใหม่ */
    var lastRecordingScanMs: Long
        get() = sp.getLong(KEY_LAST_SCAN, 0L)
        set(v) = sp.edit { putLong(KEY_LAST_SCAN, v) }

    /** ร่วมฐานข้อมูลเบอร์ของชุมชน (คลาวด์) — ปิดเป็นค่าเริ่มต้น ผู้ใช้ต้องเปิดเอง */
    var communityEnabled: Boolean
        get() = sp.getBoolean(KEY_COMMUNITY, false)
        set(v) = sp.edit { putBoolean(KEY_COMMUNITY, v) }

    /** เวลา updatedAt ล่าสุดที่ดาวน์โหลดรายชื่อเบอร์ชุมชนมาแล้ว (ms) */
    var communityLastSyncMs: Long
        get() = sp.getLong(KEY_COMMUNITY_SYNC, 0L)
        set(v) = sp.edit { putLong(KEY_COMMUNITY_SYNC, v) }

    /** ผ่านหน้าตั้งค่าแบบทีละขั้นแล้ว (กดเสร็จสิ้นหรือข้าม) — ไม่ได้แปลว่าเปิดครบทุกอย่าง */
    var setupCompleted: Boolean
        get() = sp.getBoolean(KEY_SETUP, false)
        set(v) = sp.edit { putBoolean(KEY_SETUP, v) }

    fun clearAll() = sp.edit { clear() }

    companion object {
        /** v2: เพิ่มการจดเบอร์ไม่รู้จัก 7 วัน + ฐานข้อมูลชุมชน (ไม่บังคับ) */
        const val CURRENT_CONSENT_VERSION = 2
        private const val KEY_SETUP = "setup_completed"
        private const val KEY_COMMUNITY = "community_enabled"
        private const val KEY_COMMUNITY_SYNC = "community_last_sync"
        private const val KEY_CONSENT = "consent_version"
        private const val KEY_NUMBER_WARNING = "number_warning"
        private const val KEY_AUTO_RECORDING = "auto_recording"
        private const val KEY_LAST_SCAN = "last_recording_scan"
    }
}
