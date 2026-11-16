package com.panuwatki.safe.calls

import android.content.Context
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.panuwatki.safe.SafeEngine
import com.panuwatki.safe.data.Prefs
import com.panuwatki.safe.data.SafeDatabase
import com.panuwatki.safe.ml.RiskColor
import com.panuwatki.safe.notify.Alerts
import java.time.Duration

/**
 * "วางสายปุ๊บ ตรวจอัตโนมัติ": ระบบปลุกงานนี้เมื่อมีไฟล์เสียงใหม่ในเครื่อง (ไม่ต้องรันค้างไว้ ไม่เปลืองแบต)
 * ถ้าเป็นไฟล์อัดเสียงสาย -> ถอดเสียง -> วิเคราะห์ -> แจ้งเตือนถ้าเสี่ยง
 * ถ้าถอดเสียงเบื้องหลังไม่ได้ -> แจ้งเตือนให้ผู้ใช้แตะเพื่อตรวจในแอปแทน
 */
class RecordingWatcherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        try {
            val prefs = Prefs(ctx)
            if (!prefs.hasConsent || !prefs.autoRecordingCheckEnabled) return Result.success()

            val now = System.currentTimeMillis()
            val since = prefs.lastRecordingScanMs.takeIf { it > 0 } ?: (now - FIRST_SCAN_WINDOW_MS)
            val recordings = CallRecordings.findNewSince(ctx, since)
            prefs.lastRecordingScanMs = now

            for (rec in recordings) {
                when (val outcome = SafeEngine.analyzeRecording(ctx, rec.uri)) {
                    is SafeEngine.RecordingOutcome.Success ->
                        if (outcome.state.peakColor != RiskColor.GREEN) {
                            // จับคู่กับเบอร์ไม่รู้จักที่โทรเข้าช่วงเดียวกัน (ไฟล์บันทึกเสร็จตอนวางสาย)
                            val callStart = rec.addedAtMs - rec.durationMs
                            val caller = SafeDatabase.get(ctx).numbers()
                                .recentBetween(callStart - MATCH_WINDOW_MS, rec.addedAtMs)
                            Alerts.conversationRisk(
                                ctx, outcome.state.peakColor, outcome.state.peak, outcome.suspiciousWords, outcome.id,
                                callerNumber = caller?.number,
                                numberLookedSafe = caller?.level == NumberRiskLevel.NONE.name,
                            )
                        }
                    is SafeEngine.RecordingOutcome.NoSpeech -> Unit
                    is SafeEngine.RecordingOutcome.Failure -> Alerts.tapToCheckRecording(ctx, rec.uri)
                }
            }
            SafeEngine.purgeExpired(ctx)
        } finally {
            // ตั้งให้ระบบปลุกอีกครั้งเมื่อมีไฟล์ใหม่ (งานแบบนี้ปลุกได้ครั้งเดียวต่อการตั้ง)
            enqueue(ctx, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "call-recording-watcher"
        private const val FIRST_SCAN_WINDOW_MS = 10 * 60 * 1000L
        /** เผื่อเวลาระหว่างเสียงเรียกเข้ากับเริ่มอัดเสียง */
        private const val MATCH_WINDOW_MS = 3 * 60 * 1000L

        fun schedule(context: Context) = enqueue(context, ExistingWorkPolicy.KEEP)

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }

        private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
            val prefs = Prefs(context)
            if (!prefs.hasConsent || !prefs.autoRecordingCheckEnabled) return
            val constraints = Constraints.Builder()
                .addContentUriTrigger(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true)
                .setTriggerContentUpdateDelay(Duration.ofSeconds(5))
                .setTriggerContentMaxDelay(Duration.ofSeconds(30))
                .build()
            val request = OneTimeWorkRequestBuilder<RecordingWatcherWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, policy, request)
        }
    }
}
