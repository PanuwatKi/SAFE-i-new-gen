package com.panuwatki.safe.ml

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * ตัวคัดกรองมิจฉาชีพที่รันในเครื่อง 100% (ไม่ต่ออินเทอร์เน็ต)
 *
 * คำนวณแบบเดียวกับ scikit-learn ทุกขั้น (ดู ml/export_model.py):
 *   normalize -> char_wb n-gram (2..5) -> TF-IDF (sublinear + L2) -> Naive Bayes -> sigmoid calibration
 * ผลถูกตรวจกับค่าจาก Python ใน unit test (ScamClassifierTest)
 */
class ScamClassifier(private val model: ModelFile) {

    private val index: HashMap<String, Int> = HashMap<String, Int>(model.terms.size * 2).apply {
        model.terms.forEachIndexed { i, t -> put(t, i) }
    }
    private val minN = model.ngramRange[0]
    private val maxN = model.ngramRange[1]

    val modelCreatedAt: String get() = model.createdAt
    val featureCount: Int get() = model.terms.size

    /** % โอกาสเป็นมิจฉาชีพ (0-100) */
    fun scamPercent(text: String): Double {
        val counts = HashMap<Int, Int>()
        for (g in charWbNgrams(normalize(text), minN, maxN)) {
            val i = index[g] ?: continue
            counts[i] = (counts[i] ?: 0) + 1
        }
        var normSq = 0.0
        val values = HashMap<Int, Double>(counts.size * 2)
        for ((i, c) in counts) {
            val v = (1.0 + ln(c.toDouble())) * model.idf[i]
            values[i] = v
            normSq += v * v
        }
        var z = model.bias
        if (normSq > 0) {
            val norm = sqrt(normSq)
            for ((i, v) in values) z += v / norm * model.weights[i]
        }
        val p = 1.0 / (1.0 + exp(-z))
        return 100.0 / (1.0 + exp(model.calibration.a * p + model.calibration.b))
    }

    fun colorOf(percent: Double): RiskColor = when {
        percent >= model.thresholds.red -> RiskColor.RED
        percent >= model.thresholds.yellow -> RiskColor.YELLOW
        else -> RiskColor.GREEN
    }

    fun classify(text: String): Verdict {
        val p = scamPercent(text)
        return Verdict(p, colorOf(p))
    }

    /**
     * อธิบายว่า "คำไหน" ทำให้ AI สงสัย: ลองตัดทีละคำออก แล้วดูว่าความเสี่ยงลดลงเท่าไหร่
     * (ตัดคำด้วย BreakIterator ภาษาไทยของระบบ — ใช้แค่เพื่ออธิบาย ไม่กระทบตัวเลข %)
     */
    fun explain(text: String, topN: Int = 5): List<String> {
        val base = logit(scamPercent(text))
        val drops = mutableListOf<Pair<String, Double>>()
        for (word in thaiWords(text).distinct()) {
            if (word.length < 2) continue
            val drop = base - logit(scamPercent(text.replace(word, " ")))
            if (drop > 0.1) drops += word to drop
        }
        return drops.sortedByDescending { it.second }.take(topN).map { it.first }
    }

    private fun logit(percent: Double): Double {
        val p = (percent / 100.0).coerceIn(1e-9, 1 - 1e-9)
        return ln(p / (1 - p))
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val repeatRegex = Regex("(.{2,15}?)\\1{2,}")

        fun load(input: InputStream): ScamClassifier =
            ScamClassifier(json.decodeFromString(ModelFile.serializer(), input.bufferedReader(Charsets.UTF_8).readText()))

        /** ต้องตรงกับ normalize_text() ใน ml/safe_text.py */
        fun normalize(text: String): String {
            val t = text.lowercase(Locale.ROOT).replace("ๆ", " ")
            return repeatRegex.replace(t, "$1")
        }

        /** จำลอง sklearn analyzer="char_wb" (นับเป็น code point แบบ Python) */
        fun charWbNgrams(text: String, minN: Int, maxN: Int): List<String> {
            val out = ArrayList<String>()
            for (word in splitWhitespace(text)) {
                val cps = " $word ".codePoints().toArray()
                val len = cps.size
                for (n in minN..maxN) {
                    var offset = 0
                    out.add(String(cps, 0, minOf(n, len)))
                    while (offset + n < len) {
                        offset++
                        out.add(String(cps, offset, n))
                    }
                    if (offset == 0) break
                }
            }
            return out
        }

        /** เหมือน str.split() ของ Python: ตัดตามช่องว่างทุกชนิด ทิ้งช่วงว่าง */
        fun splitWhitespace(text: String): List<String> {
            val parts = ArrayList<String>()
            val sb = StringBuilder()
            for (ch in text) {
                if (ch.isWhitespace()) {
                    if (sb.isNotEmpty()) { parts.add(sb.toString()); sb.setLength(0) }
                } else {
                    sb.append(ch)
                }
            }
            if (sb.isNotEmpty()) parts.add(sb.toString())
            return parts
        }

        fun thaiWords(text: String): List<String> {
            val it = BreakIterator.getWordInstance(Locale.forLanguageTag("th-TH"))
            it.setText(text)
            val words = mutableListOf<String>()
            var start = it.first()
            var end = it.next()
            while (end != BreakIterator.DONE) {
                val w = text.substring(start, end).trim()
                if (w.isNotEmpty() && w.any { c -> c.isLetter() }) words += w
                start = end
                end = it.next()
            }
            return words
        }
    }
}

enum class RiskColor { GREEN, YELLOW, RED }

data class Verdict(val percent: Double, val color: RiskColor)

@Serializable
data class ModelFile(
    val format: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("ngram_range") val ngramRange: List<Int>,
    val thresholds: Thresholds,
    val bias: Double,
    val calibration: Calibration,
    val terms: List<String>,
    val idf: DoubleArray,
    val weights: DoubleArray,
)

@Serializable
data class Thresholds(val red: Double, val yellow: Double)

@Serializable
data class Calibration(val a: Double, val b: Double)
