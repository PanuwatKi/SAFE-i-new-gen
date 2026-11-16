package com.panuwatki.safe.calls

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import com.panuwatki.safe.data.Prefs
import com.panuwatki.safe.notify.Alerts

/**
 * ระบบ Android เรียกบริการนี้ตอนมีสายเข้าจากเบอร์ที่ไม่อยู่ในรายชื่อติดต่อ
 * (ผู้ใช้ต้องตั้ง SAFE เป็น "แอปคัดกรองสาย" ก่อน — ทำได้จากหน้าแรกของแอป)
 *
 * SAFE "ไม่บล็อกสาย" และไม่บันทึกเบอร์ แค่แจ้งเตือนถ้าเบอร์มีลักษณะเสี่ยง
 */
class ScamCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // ตอบระบบทันทีว่า "ให้สายผ่านตามปกติ"
        respondToCall(callDetails, CallResponse.Builder().build())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection != Call.Details.DIRECTION_INCOMING
        ) return

        val prefs = Prefs(this)
        if (!prefs.hasConsent || !prefs.numberWarningEnabled) return

        val risk = NumberRiskRules.assess(callDetails.handle?.schemeSpecificPart)
        if (risk.level != NumberRiskLevel.NONE) Alerts.numberWarning(this, risk)
    }
}
