package com.panuwatki.safe

import android.content.Context
import android.net.Uri
import com.panuwatki.safe.data.AnalysisEntity
import com.panuwatki.safe.data.SafeDatabase
import com.panuwatki.safe.ml.Conversation
import com.panuwatki.safe.ml.ConversationState
import com.panuwatki.safe.ml.PiiRedactor
import com.panuwatki.safe.ml.RiskColor
import com.panuwatki.safe.ml.ScamClassifier
import com.panuwatki.safe.speech.FileTranscriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** ศูนย์กลางการวิเคราะห์ ใช้ร่วมกันทั้งหน้าจอและงานเบื้องหลัง */
object SafeEngine {

    private val lock = Mutex()
    @Volatile private var cached: ScamClassifier? = null

    suspend fun classifier(context: Context): ScamClassifier = cached ?: lock.withLock {
        cached ?: withContext(Dispatchers.Default) {
            context.applicationContext.assets.open("scam_model.json").use { ScamClassifier.load(it) }
        }.also { cached = it }
    }

    sealed interface RecordingOutcome {
        data class Success(val id: Long, val state: ConversationState, val suspiciousWords: List<String>) : RecordingOutcome
        data class NoSpeech(val reason: String) : RecordingOutcome
        data class Failure(val reason: String) : RecordingOutcome
    }

    /** ถอดเสียงไฟล์ -> วิเคราะห์ทั้งบทสนทนา (สะสมทีละช่วง + จำค่าสูงสุด) -> บันทึกแบบลบข้อมูลส่วนตัวแล้ว */
    suspend fun analyzeRecording(context: Context, uri: Uri): RecordingOutcome {
        val classifier = classifier(context)
        val segments = FileTranscriber.transcribe(context, uri).getOrElse {
            return RecordingOutcome.Failure(it.message ?: "ถอดเสียงไม่สำเร็จ")
        }
        val conversation = Conversation(classifier)
        var state = conversation.state()
        withContext(Dispatchers.Default) {
            for (s in segments) state = conversation.add(s)
        }
        if (!state.enoughData) return RecordingOutcome.NoSpeech("ไม่ได้ยินเสียงพูดในไฟล์ หรือพูดสั้นเกินไป")
        val words = if (state.peakColor != RiskColor.GREEN) {
            withContext(Dispatchers.Default) { classifier.explain(state.transcript) }
        } else emptyList()
        val id = save(context, state, AnalysisEntity.SOURCE_RECORDING)
        return RecordingOutcome.Success(id, state, words)
    }

    /** บันทึกผล (ลบเบอร์/เลขบัญชี/ลิงก์ ฯลฯ ก่อนเสมอ) */
    suspend fun save(context: Context, state: ConversationState, source: String): Long =
        SafeDatabase.get(context).analyses().insert(
            AnalysisEntity(
                createdAt = System.currentTimeMillis(),
                source = source,
                transcript = PiiRedactor.redact(state.transcript),
                percent = state.peak,
                color = state.peakColor.name,
            ),
        )

    /** ลบผลที่ผู้ใช้ไม่ได้ยืนยัน (เก่ากว่า RETENTION_DAYS) และเบอร์สายล่าสุด (เก่ากว่า RECENT_DAYS) */
    suspend fun purgeExpired(context: Context) {
        val now = System.currentTimeMillis()
        val db = SafeDatabase.get(context)
        db.analyses().purgeUnlabeledBefore(now - TimeUnit.DAYS.toMillis(SafeDatabase.RETENTION_DAYS))
        db.numbers().purgeRecentBefore(now - TimeUnit.DAYS.toMillis(SafeDatabase.RECENT_DAYS))
    }
}
