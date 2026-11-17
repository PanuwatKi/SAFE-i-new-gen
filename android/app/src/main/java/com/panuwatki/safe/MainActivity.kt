package com.panuwatki.safe

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import com.panuwatki.safe.notify.Alerts
import com.panuwatki.safe.ui.AppRoot
import com.panuwatki.safe.ui.LaunchRequest
import com.panuwatki.safe.ui.SafeTheme

class MainActivity : ComponentActivity() {

    private var launchRequest by mutableStateOf<LaunchRequest?>(null)

    /** Intent ล่าสุดที่จัดการไปแล้ว — กันเปิดหน้าซ้ำตอนหมุนจอ แต่ไม่ทิ้ง Intent ใหม่ */
    private var handledIntentKey: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // ตรวจ Intent ทุกครั้ง แม้ Android จะฟื้นหน้าจอจาก savedInstanceState (เช่น แอปถูกปิดเบื้องหลัง
        // แล้วผู้ใช้แชร์ข้อความจาก LINE เข้ามา) — ข้ามเฉพาะ Intent เดิมที่เคยจัดการแล้ว (หมุนจอ)
        val alreadyHandled = savedInstanceState?.getInt(KEY_HANDLED_INTENT) == intentKey(intent)
        if (!alreadyHandled) launchRequest = parse(intent)
        handledIntentKey = intentKey(intent)
        setContent {
            SafeTheme {
                AppRoot(launchRequest = launchRequest, onLaunchHandled = { launchRequest = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handledIntentKey = intentKey(intent)
        parse(intent)?.let { launchRequest = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_HANDLED_INTENT, handledIntentKey)
    }

    private fun intentKey(i: Intent?): Int {
        i ?: return 0
        return listOf(
            i.action, i.type, i.dataString,
            i.getStringExtra(Intent.EXTRA_TEXT),
            IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)?.toString(),
            i.getStringExtra(Alerts.EXTRA_ANALYZE_URI),
            i.getStringExtra(Alerts.EXTRA_REPORT_NUMBER),
            i.getLongExtra(Alerts.EXTRA_OPEN_RECORD_ID, -1L),
        ).hashCode()
    }

    private companion object {
        const val KEY_HANDLED_INTENT = "handled_intent_key"
    }

    /** แปลง Intent ที่เปิดแอป: แชร์ไฟล์เสียงเข้ามา / แตะการแจ้งเตือน */
    private fun parse(intent: Intent?): LaunchRequest? {
        intent ?: return null
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { return LaunchRequest.CheckText(it) }
        }
        if (intent.action == Intent.ACTION_SEND) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let {
                return LaunchRequest.AnalyzeUri(it)
            }
        }
        intent.getStringExtra(Alerts.EXTRA_ANALYZE_URI)?.let { return LaunchRequest.AnalyzeUri(Uri.parse(it)) }
        // แตะการแจ้งเตือน "สายเมื่อกี้เสี่ยง" ที่จับคู่เบอร์ได้ -> ไปหน้ารายงานเบอร์
        intent.getStringExtra(Alerts.EXTRA_REPORT_NUMBER)?.let { return LaunchRequest.ReportNumber(it) }
        val recordId = intent.getLongExtra(Alerts.EXTRA_OPEN_RECORD_ID, -1L)
        if (recordId > 0) return LaunchRequest.OpenRecord(recordId)
        return null
    }
}
