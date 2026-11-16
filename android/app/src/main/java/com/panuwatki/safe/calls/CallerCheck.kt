package com.panuwatki.safe.calls

/**
 * รวมทุกแหล่งข้อมูลตอนสายเข้า แล้วตัดสินว่าจะเตือน/ตัดสายอย่างไร
 * ลำดับความสำคัญ:
 *   1. เครือข่ายบอกว่าเบอร์ถูกปลอม -> แดงเสมอ (แม้ผู้ใช้เคยบันทึกว่าไว้ใจ เพราะคนปลอมอาจใช้เบอร์ที่เราไว้ใจ)
 *   2. สมุดเบอร์ของผู้ใช้ (มิจฉาชีพ / สแปม / ไว้ใจได้)
 *   3. ฐานข้อมูลชุมชน (ต้องมีผู้รายงาน >= COMMUNITY_THRESHOLD เครื่อง)
 *   4. กฎเบอร์ (+697, +698, ต่างประเทศ, ซ่อนเบอร์, รูปแบบผิด)
 */
object CallerCheck {
    const val COMMUNITY_THRESHOLD = 3

    enum class BookLabel { SCAM, SPAM, TRUSTED }

    data class BookInfo(val label: BookLabel, val category: String?, val note: String?, val autoReject: Boolean)
    data class CommunityInfo(val scamReports: Int, val spamReports: Int)

    data class Decision(
        val level: NumberRiskLevel,
        val title: String,
        val reason: String,
        val reject: Boolean,
    )

    fun decide(
        rawNumber: String?,
        verification: NetworkVerification,
        book: BookInfo?,
        community: CommunityInfo?,
    ): Decision {
        if (verification == NetworkVerification.FAILED) {
            val r = NumberRiskRules.assess(rawNumber, verification)
            return Decision(NumberRiskLevel.HIGH, "🔴 ระวัง! เบอร์นี้อาจถูกปลอม", r.reason, reject = false)
        }
        if (book != null) {
            val detail = listOfNotNull(book.category, book.note?.takeIf { it.isNotBlank() }).joinToString(" · ")
            return when (book.label) {
                BookLabel.SCAM -> Decision(
                    NumberRiskLevel.HIGH, "🔴 เบอร์ที่คุณบันทึกว่าเป็นมิจฉาชีพ",
                    detail.ifEmpty { "คุณเคยบันทึกเบอร์นี้ว่าเป็นมิจฉาชีพ" }, reject = book.autoReject,
                )
                BookLabel.SPAM -> Decision(
                    NumberRiskLevel.CAUTION, "🟡 เบอร์ที่คุณบันทึกว่าเป็นสแปม",
                    detail.ifEmpty { "คุณเคยบันทึกเบอร์นี้ว่าเป็นสแปม/ขายของ" }, reject = book.autoReject,
                )
                BookLabel.TRUSTED -> Decision(NumberRiskLevel.NONE, "", "", reject = false)
            }
        }
        if (community != null) {
            val total = community.scamReports + community.spamReports
            if (total >= COMMUNITY_THRESHOLD) {
                return if (community.scamReports >= community.spamReports) {
                    Decision(
                        NumberRiskLevel.HIGH, "🔴 มีผู้ใช้ SAFE รายงานว่าเป็นมิจฉาชีพ",
                        "ถูกรายงานว่าเป็นมิจฉาชีพ ${community.scamReports} ครั้ง ห้ามโอนเงิน ห้ามบอกรหัส", reject = false,
                    )
                } else {
                    Decision(
                        NumberRiskLevel.CAUTION, "🟡 มีผู้ใช้ SAFE รายงานว่าเป็นสแปม",
                        "ถูกรายงานว่าเป็นสแปม/ขายของ ${community.spamReports} ครั้ง", reject = false,
                    )
                }
            }
        }
        val r = NumberRiskRules.assess(rawNumber, verification)
        val title = when (r.level) {
            NumberRiskLevel.HIGH -> "🔴 ระวัง! สายนี้เสี่ยงมิจฉาชีพ"
            NumberRiskLevel.CAUTION -> "🟡 ระวังสายนี้"
            NumberRiskLevel.NONE -> ""
        }
        return Decision(r.level, title, r.reason, reject = false)
    }
}
