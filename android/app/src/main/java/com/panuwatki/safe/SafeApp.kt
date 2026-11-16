package com.panuwatki.safe

import android.app.Application
import android.content.Context
import com.panuwatki.safe.calls.RecordingWatcherWorker
import com.panuwatki.safe.notify.Alerts

class SafeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Alerts.createChannels(this)
        // ตั้งงานตรวจไฟล์อัดเสียงสายไว้ (ทำงานเฉพาะเมื่อผู้ใช้เปิดฟีเจอร์และยอมรับนโยบายแล้ว)
        RecordingWatcherWorker.schedule(this)
    }
}

object BuildConfigInfo {
    fun versionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
}
