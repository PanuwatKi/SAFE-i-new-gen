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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) launchRequest = parse(intent)
        setContent {
            SafeTheme {
                AppRoot(launchRequest = launchRequest, onLaunchHandled = { launchRequest = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parse(intent)?.let { launchRequest = it }
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
