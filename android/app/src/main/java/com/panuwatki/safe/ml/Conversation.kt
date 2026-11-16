package com.panuwatki.safe.ml

/**
 * บทสนทนาสะสมทั้งสาย: วิเคราะห์ "ทุกประโยครวมกัน" ไม่ใช่ทีละประโยค
 * และจำค่าความเสี่ยงสูงสุดไว้ (เคยแดงแล้วจะไม่กลับเป็นเขียวเอง — เตือนแล้วไม่ลืม)
 */
class Conversation(private val classifier: ScamClassifier) {

    private val lines = mutableListOf<String>()
    var peak: Double = 0.0
        private set

    val transcript: String get() = lines.joinToString(" ")
    val isEmpty: Boolean get() = lines.isEmpty()

    fun add(utterance: String): ConversationState {
        val u = utterance.trim()
        if (u.isNotEmpty()) lines += u
        return state()
    }

    fun state(): ConversationState {
        val text = transcript
        if (!hasEnoughText(text)) {
            return ConversationState(text, latest = null, peak = peak, peakColor = classifier.colorOf(peak), enoughData = false)
        }
        val latest = classifier.classify(text)
        peak = maxOf(peak, latest.percent)
        return ConversationState(text, latest, peak, classifier.colorOf(peak), enoughData = true)
    }

    fun reset() {
        lines.clear()
        peak = 0.0
    }

    companion object {
        /** ข้อความสั้นเกินไป (เช่น แค่ "ฮัลโหล") ยังไม่ตัดสิน — ต้องตรงกับ ml/predict.py */
        const val MIN_CHARS = 8

        fun hasEnoughText(text: String): Boolean =
            ScamClassifier.normalize(text).count { !it.isWhitespace() } >= MIN_CHARS
    }
}

data class ConversationState(
    val transcript: String,
    val latest: Verdict?,
    val peak: Double,
    val peakColor: RiskColor,
    val enoughData: Boolean,
)
