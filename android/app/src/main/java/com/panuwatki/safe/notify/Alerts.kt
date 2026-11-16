package com.panuwatki.safe.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.panuwatki.safe.MainActivity
import com.panuwatki.safe.R
import com.panuwatki.safe.calls.NumberRisk
import com.panuwatki.safe.calls.NumberRiskLevel
import com.panuwatki.safe.ml.RiskColor

/** การแจ้งเตือนทั้งหมดของแอป (ข้อความสั้น ชัด อ่านง่ายสำหรับผู้สูงอายุ) */
object Alerts {
    const val CHANNEL_ALERT = "scam_alert"
    const val CHANNEL_INFO = "info"
    private const val ID_NUMBER = 1001
    private const val ID_CONVERSATION = 1002
    private const val ID_TAP_TO_CHECK = 1003

    const val EXTRA_OPEN_RECORD_ID = "open_record_id"
    const val EXTRA_ANALYZE_URI = "analyze_uri"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "เตือนมิจฉาชีพ", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "เตือนเมื่อสายหรือบทสนทนามีความเสี่ยง"
                enableVibration(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INFO, "ข้อมูลทั่วไป", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun numberWarning(context: Context, risk: NumberRisk) {
        val title = if (risk.level == NumberRiskLevel.HIGH) "🔴 ระวัง! สายนี้เสี่ยงมิจฉาชีพ" else "🟡 ระวังสายนี้"
        notify(context, ID_NUMBER, CHANNEL_ALERT, title, risk.reason, openAppIntent(context, null))
    }

    fun conversationRisk(context: Context, color: RiskColor, percent: Double, words: List<String>, recordId: Long) {
        val title = when (color) {
            RiskColor.RED -> "🔴 สายเมื่อกี้เสี่ยงมิจฉาชีพสูงมาก (${percent.toInt()}%)"
            RiskColor.YELLOW -> "🟡 สายเมื่อกี้น่าสงสัย (${percent.toInt()}%)"
            RiskColor.GREEN -> return
        }
        val body = buildString {
            append("ห้ามโอนเงิน ห้ามบอกรหัส OTP ห้ามกดลิงก์ ถ้าไม่แน่ใจ โทรถามลูกหลานหรือสายด่วน 1441")
            if (words.isNotEmpty()) append("\nคำที่น่าสงสัย: ").append(words.joinToString(" "))
        }
        val intent = openAppIntent(context, null).putExtra(EXTRA_OPEN_RECORD_ID, recordId)
        notify(context, ID_CONVERSATION, CHANNEL_ALERT, title, body, intent)
    }

    fun tapToCheckRecording(context: Context, uri: Uri) {
        val intent = openAppIntent(context, null)
            .putExtra(EXTRA_ANALYZE_URI, uri.toString())
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        notify(context, ID_TAP_TO_CHECK, CHANNEL_INFO, "มีไฟล์อัดเสียงสายใหม่", "แตะเพื่อให้ SAFE ตรวจสายเมื่อกี้", intent)
    }

    private fun openAppIntent(context: Context, action: String?): Intent =
        Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission") // ตรวจสิทธิ์ใน canNotify() แล้ว
    private fun notify(context: Context, id: Int, channel: String, title: String, body: String, intent: Intent) {
        if (!canNotify(context)) return
        val pi = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (channel == CHANNEL_ALERT) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (channel == CHANNEL_ALERT) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}
