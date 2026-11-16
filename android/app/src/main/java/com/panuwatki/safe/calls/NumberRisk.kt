package com.panuwatki.safe.calls

/**
 * ประเมินความเสี่ยงจาก "เบอร์" ตอนสายเข้า (ยังไม่ได้ยินเสียง)
 * ระบบเรียกบริการนี้เฉพาะสายจากเบอร์ที่ "ไม่อยู่ในรายชื่อติดต่อ" เท่านั้น
 * เบอร์ไม่ถูกบันทึกหรือส่งไปไหน — ใช้ตัดสินแล้วทิ้ง
 */
enum class NumberRiskLevel { NONE, CAUTION, HIGH }

data class NumberRisk(val level: NumberRiskLevel, val reason: String)

object NumberRiskRules {

    /** รหัสที่ไม่มีประเทศจริง แต่แก๊งคอลเซ็นเตอร์ใช้ปลอมเบอร์บ่อยในไทย */
    private val spoofPrefixes = listOf("697", "698")
    private val thaiIdd = Regex("^00[1789]")

    fun assess(raw: String?): NumberRisk {
        if (raw.isNullOrBlank()) {
            return NumberRisk(NumberRiskLevel.CAUTION, "ไม่แสดงหมายเลข — มิจฉาชีพมักซ่อนเบอร์")
        }
        val n = raw.filter { it.isDigit() || it == '+' }
        val international: String? = when {
            n.startsWith("+") -> n.drop(1)
            thaiIdd.containsMatchIn(n) -> n.drop(3)
            else -> null
        }
        if (international == null || international.startsWith("66")) {
            return NumberRisk(NumberRiskLevel.NONE, "")
        }
        if (spoofPrefixes.any { international.startsWith(it) }) {
            return NumberRisk(
                NumberRiskLevel.HIGH,
                "เบอร์ขึ้นต้น +${international.take(3)} ไม่ใช่เบอร์ประเทศจริง แก๊งคอลเซ็นเตอร์ใช้บ่อย ห้ามโอนเงิน ห้ามบอกรหัส",
            )
        }
        return NumberRisk(NumberRiskLevel.CAUTION, "สายจากต่างประเทศ ถ้าไม่ได้รอสายนี้ อย่าเชื่อ อย่าโอนเงิน")
    }
}
