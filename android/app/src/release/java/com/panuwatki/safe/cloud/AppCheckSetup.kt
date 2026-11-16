package com.panuwatki.safe.cloud

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** รุ่น release: ใช้ Play Integrity ยืนยันว่าคำขอมาจากแอป SAFE จริงที่ติดตั้งจาก Play (กันสคริปต์ปั่นรายงาน) */
object AppCheckSetup {
    fun install(app: FirebaseApp) {
        FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
