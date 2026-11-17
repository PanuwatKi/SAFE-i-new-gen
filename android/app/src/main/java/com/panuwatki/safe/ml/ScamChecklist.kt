package com.panuwatki.safe.ml

/**
 * ถามตอบ 5 ข้อหลังวางสาย — ใช้ได้แม้ไม่มีไฟล์อัดเสียง ไม่ต้องพิมพ์ แค่แตะใช่/ไม่ใช่
 * อิงหลัก "ไม่เชื่อ ไม่รีบ ไม่โอน" ของ AOC 1441: หน่วยงานจริงไม่โทรมาขอให้โอนเงิน ขอ OTP หรือให้ติดตั้งแอป
 */
object ScamChecklist {

    enum class Question(val text: String, val hint: String, val redFlag: Boolean) {
        CLAIMS_AUTHORITY(
            "เขาอ้างว่าเป็น ตำรวจ ธนาคาร หน่วยงานรัฐ ขนส่ง ค่ายมือถือ หรือคนรู้จักไหม?",
            "เช่น บอกว่าโทรจากสถานีตำรวจ ธนาคาร การไฟฟ้า กรมสรรพากร หรือบอกว่าเป็นลูกหลาน",
            redFlag = false,
        ),
        ASKS_MONEY(
            "เขาให้โอนเงิน ย้ายเงิน หรือจ่ายค่าธรรมเนียมก่อนไหม?",
            "เช่น โอนไป \"บัญชีปลอดภัย\" เพื่อตรวจสอบ จ่ายค่าปรับ ค่าภาษี ค่ามัดจำ",
            redFlag = true,
        ),
        ASKS_SECRET(
            "เขาขอรหัส OTP รหัสผ่าน เลขบัตร หรือให้สแกนหน้าไหม?",
            "รวมถึงขอให้อ่านตัวเลขที่ขึ้นบนจอให้ฟัง",
            redFlag = true,
        ),
        ASKS_LINK_APP(
            "เขาให้กดลิงก์ ติดตั้งแอป เพิ่มไลน์ หรือแชร์หน้าจอไหม?",
            "เช่น ให้โหลดแอปเพื่อ \"ยืนยันตัวตน\" หรือ \"รับเงินคืน\"",
            redFlag = true,
        ),
        PRESSURE(
            "เขาขู่ เร่งให้รีบทำ ห้ามวางสาย หรือห้ามบอกใครไหม?",
            "เช่น ขู่ว่าจะถูกจับ บัญชีจะถูกอายัด หรือบอกว่าเป็นความลับทางคดี",
            redFlag = false,
        ),
    }

    data class Result(val color: RiskColor, val title: String, val reasons: List<String>)

    /** @param answers คำตอบ true = ใช่, false = ไม่ใช่, ไม่มีในแผนที่ = ไม่แน่ใจ/ข้าม */
    fun evaluate(answers: Map<Question, Boolean>): Result {
        val yes = Question.entries.filter { answers[it] == true }
        val reasons = yes.map { reasonOf(it) }
        return when {
            yes.any { it.redFlag } -> Result(
                RiskColor.RED,
                "เสี่ยงมิจฉาชีพสูงมาก",
                reasons + "หน่วยงานจริงไม่โทรมาขอให้โอนเงิน ขอ OTP หรือให้ติดตั้งแอป",
            )
            Question.PRESSURE in yes -> Result(RiskColor.YELLOW, "น่าสงสัย ระวังไว้", reasons)
            Question.CLAIMS_AUTHORITY in yes -> Result(
                RiskColor.YELLOW,
                "ยังไม่พบสัญญาณอันตราย แต่ควรตรวจสอบ",
                reasons + "วางสายแล้วโทรกลับเบอร์ทางการของหน่วยงานนั้นเองก่อนทำตามใด ๆ",
            )
            else -> Result(RiskColor.GREEN, "ไม่พบสัญญาณมิจฉาชีพจากคำตอบ", listOf("ถ้ามีอะไรแปลก ๆ ภายหลัง กลับมาตอบใหม่ได้เสมอ"))
        }
    }

    private fun reasonOf(q: Question): String = when (q) {
        Question.CLAIMS_AUTHORITY -> "อ้างเป็นหน่วยงานหรือคนรู้จัก — มุกที่แก๊งคอลเซ็นเตอร์ใช้บ่อยที่สุด"
        Question.ASKS_MONEY -> "ขอให้โอนเงิน — ธนาคารและตำรวจไม่มี \"บัญชีปลอดภัย\""
        Question.ASKS_SECRET -> "ขอ OTP/รหัส/สแกนหน้า — ใครได้ไปก็ถอนเงินจากบัญชีคุณได้"
        Question.ASKS_LINK_APP -> "ให้กดลิงก์/ติดตั้งแอป/แชร์หน้าจอ — ทางที่แอปดูดเงินเข้ามาในมือถือ"
        Question.PRESSURE -> "ขู่หรือเร่งรีบ — ทำให้ไม่มีเวลาคิดและไม่ปรึกษาใคร"
    }
}
