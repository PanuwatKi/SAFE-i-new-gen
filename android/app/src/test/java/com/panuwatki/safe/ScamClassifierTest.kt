package com.panuwatki.safe

import com.panuwatki.safe.ml.Conversation
import com.panuwatki.safe.ml.RiskColor
import com.panuwatki.safe.ml.ScamClassifier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * ตรวจว่าโมเดลฝั่ง Kotlin ให้ % "ตรงกับ" scikit-learn ฝั่ง Python
 * test vector สร้างโดย ml/export_model.py (ถ้าเทรนใหม่ ไฟล์นี้จะอัปเดตตาม)
 */
class ScamClassifierTest {

    @Serializable
    data class Vector(val text: String, val percent: Double)

    companion object {
        lateinit var classifier: ScamClassifier

        @BeforeClass
        @JvmStatic
        fun load() {
            classifier = File("src/main/assets/scam_model.json").inputStream().use { ScamClassifier.load(it) }
        }
    }

    @Test
    fun matchesPythonReference() {
        val json = javaClass.classLoader!!.getResource("model_test_vectors.json")!!.readText(Charsets.UTF_8)
        val vectors = Json.decodeFromString<List<Vector>>(json)
        assertTrue(vectors.size > 20)
        for (v in vectors) {
            assertEquals("text=\"${v.text}\"", v.percent, classifier.scamPercent(v.text), 1e-3)
        }
    }

    @Test
    fun normalizeMatchesPython() {
        assertEquals("คุณ", ScamClassifier.normalize("คุณคุณคุณ"))
        assertEquals("otp", ScamClassifier.normalize("OTP"))
        // ๆ กลายเป็นช่องว่าง จึงได้ 2 ช่อง (Python: normalize_text("ไปๆ ไป") == "ไป  ไป")
        assertEquals("ไป  ไป", ScamClassifier.normalize("ไปๆ ไป"))
    }

    @Test
    fun charWbNgramsMatchSklearn() {
        // sklearn: TfidfVectorizer(analyzer="char_wb", ngram_range=(2,3)).build_analyzer()("ab c")
        assertEquals(
            listOf(" a", "ab", "b ", " ab", "ab ", " c", "c ", " c "),
            ScamClassifier.charWbNgrams("ab c", 2, 3),
        )
        assertEquals(emptyList<String>(), ScamClassifier.charWbNgrams("   ", 2, 5))
    }

    @Test
    fun obviousCases() {
        assertEquals(RiskColor.RED, classifier.classify("โทรจากธนาคารนะคะ บัญชีของคุณถูกอายัด กรุณาโอนเงินไปบัญชีปลอดภัยด่วน").color)
        assertEquals(RiskColor.GREEN, classifier.classify("แม่จ๋า เย็นนี้กินข้าวบ้านไหม").color)
    }

    @Test
    fun conversationKeepsPeak() {
        val c = Conversation(classifier)
        assertFalse(c.add("ฮัลโหล").enoughData)
        val scam = c.add("โทรจากสถานีตำรวจ บัญชีคุณพัวพันคดีฟอกเงิน ต้องโอนเงินมาตรวจสอบ")
        assertEquals(RiskColor.RED, scam.peakColor)
        val after = c.add("โอเคครับ ขอบคุณครับ สวัสดีครับ")
        assertEquals(RiskColor.RED, after.peakColor) // เคยแดงแล้วไม่กลับเป็นเขียว
        c.reset()
        assertEquals(0.0, c.peak, 0.0)
    }
}
