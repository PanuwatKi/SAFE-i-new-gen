package com.panuwatki.safe.ml

/**
 * ลบข้อมูลส่วนบุคคลออกจากข้อความ "ก่อนบันทึกลงเครื่อง" (นโยบาย: เก็บเท่าที่จำเป็น)
 * - ลิงก์ / อีเมล
 * - เลขบัตรประชาชน 13 หลัก
 * - เบอร์โทร (0x..., +66...)
 * - ตัวเลขยาวตั้งแต่ 4 หลัก (เลขบัญชี, OTP, รหัส ฯลฯ)
 * ตัวเลขสั้น (เช่น "350 บาท") เก็บไว้ เพราะช่วยให้ AI เรียนรู้รูปแบบได้
 *
 * ข้อจำกัด: ชื่อคนและตัวเลขที่พูดเป็นคำ ("ศูนย์แปดหนึ่ง...") ลบอัตโนมัติไม่ได้
 * แอปจึงให้ผู้ใช้ตรวจ/แก้ข้อความก่อนบันทึกเป็นข้อมูลเทรนทุกครั้ง
 */
object PiiRedactor {
    const val LINK = "[ลิงก์]"
    const val EMAIL = "[อีเมล]"
    const val ID_CARD = "[เลขบัตร]"
    const val PHONE = "[เบอร์โทร]"
    const val NUMBER = "[ตัวเลข]"

    // โดเมนหลายชั้นได้ (scb.co.th) และ TLD ต้องจบคำ (x.comic ไม่ใช่ลิงก์) — ตรงกับ web/safe-core.js และ ml/redact.py
    private val url = Regex("""(?i)\b(?:https?://|www\.)\S+|\b[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|net|org|co|th|ly|me|io|xyz|link|app|cc|info)(?![a-z0-9-])(?:/\S*)?""")
    private val email = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")
    // ตัวเลขที่อาจมี + นำหน้า และมีช่องว่าง/ขีด/จุดคั่นได้
    private val digits = Regex("""\+?\d(?:[\d\s\-.]*\d)?""")

    fun redact(text: String): String {
        var t = thaiDigitsToArabic(text)
        t = email.replace(t, EMAIL) // ก่อนลิงก์ ไม่งั้น "example.com" ในอีเมลจะถูกมองเป็นลิงก์
        t = url.replace(t, LINK)
        t = digits.replace(t) { m -> classifyNumber(m.value) ?: m.value }
        return t
    }

    private fun classifyNumber(raw: String): String? {
        val d = raw.filter { it.isDigit() }
        return when {
            d.length < 4 -> null
            d.length == 13 && !raw.startsWith("+") -> ID_CARD
            raw.startsWith("+") || (d.startsWith("0") && d.length in 9..10) || (d.startsWith("66") && d.length in 10..11) -> PHONE
            else -> NUMBER
        }
    }

    fun thaiDigitsToArabic(text: String): String = buildString(text.length) {
        for (c in text) append(if (c in '๐'..'๙') '0' + (c - '๐') else c)
    }
}
