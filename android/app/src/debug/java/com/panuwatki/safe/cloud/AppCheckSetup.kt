package com.panuwatki.safe.cloud

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/** รุ่น debug: ใช้ debug token (ดู logcat แล้วนำไปลงทะเบียนใน Firebase Console > App Check) */
object AppCheckSetup {
    fun install(app: FirebaseApp) {
        FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
