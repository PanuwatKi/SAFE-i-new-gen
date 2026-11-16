package com.panuwatki.safe.calls

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import com.panuwatki.safe.AppScope
import com.panuwatki.safe.cloud.CommunityReports
import com.panuwatki.safe.data.Prefs
import com.panuwatki.safe.data.RecentCall
import com.panuwatki.safe.data.SafeDatabase
import com.panuwatki.safe.notify.Alerts
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ระบบ Android เรียกบริการนี้ตอนมีสายเข้าจากเบอร์ที่ไม่อยู่ในรายชื่อติดต่อ
 * (ผู้ใช้ต้องตั้ง SAFE เป็น "แอปคัดกรองสาย" ก่อน — ทำได้จากหน้าแรกของแอป)
 *
 * - ตรวจกับสมุดเบอร์ / รายชื่อชุมชนที่ดาวน์โหลดไว้ / กฎเบอร์ / ผลตรวจเบอร์ปลอมของเครือข่าย
 *   ทั้งหมดตรวจ "ในเครื่อง" ไม่มีการส่งเบอร์ที่โทรเข้าไปที่ใด
 * - ตัดสายเฉพาะเบอร์ที่ผู้ใช้ตั้ง "ตัดสายอัตโนมัติ" ไว้เองเท่านั้น
 * - จดเบอร์ไว้ในเครื่อง 7 วัน เพื่อให้ผู้ใช้กดรายงานได้ภายหลัง
 */
class ScamCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection != Call.Details.DIRECTION_INCOMING
        ) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }
        val prefs = Prefs(this)
        if (!prefs.hasConsent) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val raw = callDetails.handle?.schemeSpecificPart
        val number = PhoneNumbers.normalize(raw)
        val verification = verificationOf(callDetails)
        val dao = SafeDatabase.get(this).numbers()
        val appContext = applicationContext

        AppScope.launch {
            // ต้องตอบระบบภายในไม่กี่วินาที: ถ้าค้นข้อมูลไม่ทัน ให้ตัดสินจากกฎเบอร์อย่างเดียว
            val decision = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
                val book = number?.let { dao.find(it) }?.let {
                    CallerCheck.BookInfo(CallerCheck.BookLabel.valueOf(it.label), it.category, it.note, it.autoReject)
                }
                val community = if (number != null && CommunityReports.isActive(appContext)) {
                    dao.community(PhoneNumbers.communityKey(number))?.let { CallerCheck.CommunityInfo(it.scam, it.spam) }
                } else null
                CallerCheck.decide(raw, verification, book, community)
            } ?: CallerCheck.decide(raw, verification, null, null)

            val response = CallResponse.Builder().apply {
                if (decision.reject) {
                    setDisallowCall(true)
                    setRejectCall(true)
                }
            }.build()
            respondToCall(callDetails, response)

            if (decision.title.isNotEmpty() || number != null) {
                dao.insertRecent(
                    RecentCall(number = number, at = System.currentTimeMillis(), level = decision.level.name, title = decision.title, rejected = decision.reject),
                )
            }
            if (prefs.numberWarningEnabled && decision.level != NumberRiskLevel.NONE) {
                Alerts.numberWarning(appContext, decision)
            }
        }
    }

    private fun verificationOf(details: Call.Details): NetworkVerification {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return NetworkVerification.UNKNOWN
        return when (details.callerNumberVerificationStatus) {
            Connection.VERIFICATION_STATUS_PASSED -> NetworkVerification.PASSED
            Connection.VERIFICATION_STATUS_FAILED -> NetworkVerification.FAILED
            else -> NetworkVerification.UNKNOWN
        }
    }

    companion object {
        private const val LOOKUP_TIMEOUT_MS = 2_500L
    }
}
