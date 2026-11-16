package com.panuwatki.safe.calls

import java.security.MessageDigest

/**
 * ประเมินความเสี่ยงจาก "เบอร์" ตอนสายเข้า (ยังไม่ได้ยินเสียง)
 * ระบบเรียกบริการคัดกรองเฉพาะสายจากเบอร์ที่ "ไม่อยู่ในรายชื่อติดต่อ" เท่านั้น
 */
enum class NumberRiskLevel { NONE, CAUTION, HIGH }

data class NumberRisk(val level: NumberRiskLevel, val reason: String)

/** ผลตรวจเบอร์จากเครือข่าย (STIR/SHAKEN, Android 11+) — ค่ายในไทยอาจยังไม่ส่งมา จะได้ UNKNOWN */
enum class NetworkVerification { UNKNOWN, PASSED, FAILED }

object PhoneNumbers {
    private val thaiIdd = Regex("^00[1789]")

    /**
     * แปลงเบอร์ให้อยู่รูปแบบเดียวกัน (คล้าย E.164) เพื่อใช้เป็นกุญแจในสมุดเบอร์และฐานข้อมูลชุมชน
     * 081-234-5678 / +66 81 234 5678 / 66812345678 -> +66812345678
     * 001 855 12 345 678 -> +85512345678 · เบอร์สั้น เช่น 1441 คงเดิม
     */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val plus = raw.trim().startsWith("+")
        val d = raw.filter { it.isDigit() }
        if (d.isEmpty()) return null
        return when {
            plus -> "+$d"
            thaiIdd.containsMatchIn(d) -> "+" + d.drop(3)
            d.startsWith("0") && d.length in 9..10 -> "+66" + d.drop(1)
            d.startsWith("66") && d.length in 10..11 -> "+$d"
            else -> d
        }
    }

    /** แสดงผลให้อ่านง่าย: +66812345678 -> 081-234-5678 */
    fun display(normalized: String?): String {
        if (normalized == null) return "ไม่แสดงหมายเลข"
        if (normalized.startsWith("+66")) {
            val local = "0" + normalized.drop(3)
            return when (local.length) {
                10 -> "${local.take(3)}-${local.substring(3, 6)}-${local.drop(6)}"
                9 -> "${local.take(2)}-${local.substring(2, 5)}-${local.drop(5)}"
                else -> local
            }
        }
        return normalized
    }

    /**
     * กุญแจสำหรับฐานข้อมูลชุมชน (SHA-256) — ไม่ส่งเบอร์ตรง ๆ ขึ้นคลาวด์
     * หมายเหตุ: เบอร์มีแค่ ~10 หลัก จึงย้อนกลับได้ถ้าตั้งใจ นโยบายจึงเรียกว่า "ข้อมูลแฝง" ไม่ใช่นิรนาม
     */
    fun communityKey(normalized: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("safe-v1:$normalized".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

object NumberRiskRules {

    fun assess(raw: String?, verification: NetworkVerification = NetworkVerification.UNKNOWN): NumberRisk {
        if (verification == NetworkVerification.FAILED) {
            return NumberRisk(NumberRiskLevel.HIGH, "เครือข่ายตรวจแล้วว่าเบอร์นี้อาจถูกปลอม อย่าเชื่อเบอร์ที่โชว์บนจอ")
        }
        val n = PhoneNumbers.normalize(raw)
            ?: return NumberRisk(NumberRiskLevel.CAUTION, "ไม่แสดงหมายเลข มิจฉาชีพมักซ่อนเบอร์")

        if (n.startsWith("+697")) {
            return NumberRisk(
                NumberRiskLevel.HIGH,
                "+697 คือสายโทรผ่านอินเทอร์เน็ตจากต่างประเทศ ซึ่งแก๊งคอลเซ็นเตอร์ใช้บ่อยมาก ห้ามโอนเงิน ห้ามบอกรหัส",
            )
        }
        if (n.startsWith("+698")) {
            return NumberRisk(
                NumberRiskLevel.CAUTION,
                "+698 คือเบอร์มือถือไทยที่โรมมิ่งอยู่ต่างประเทศ ถ้าไม่มีคนรู้จักอยู่ต่างประเทศ ให้ระวัง",
            )
        }
        if (n.startsWith("+66")) {
            val nsn = n.drop(3)
            if (nsn.isEmpty()) {
                // กสทช. ให้แสดง +66 สำหรับสายจากต่างประเทศที่ไม่มีเลขหมายต้นทาง
                return NumberRisk(NumberRiskLevel.CAUTION, "สายจากต่างประเทศที่ไม่แสดงเบอร์ต้นทาง อย่าเชื่อ อย่าโอนเงิน")
            }
            // เบอร์ไทยที่ถูกต้อง: เลขหลัง +66 ยาว 8 (บ้าน) หรือ 9 (มือถือ) หลัก และไม่ขึ้นต้นด้วย 0
            if (nsn.startsWith("0") || nsn.length !in 8..9) {
                return NumberRisk(NumberRiskLevel.CAUTION, "รูปแบบเบอร์ผิดปกติ อาจเป็นเบอร์ปลอม")
            }
            return NumberRisk(NumberRiskLevel.NONE, "")
        }
        if (n.startsWith("+")) {
            return NumberRisk(NumberRiskLevel.CAUTION, "สายจากต่างประเทศ ถ้าไม่ได้รอสายนี้ อย่าเชื่อ อย่าโอนเงิน")
        }
        return NumberRisk(NumberRiskLevel.NONE, "")
    }
}
