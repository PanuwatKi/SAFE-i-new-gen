package com.panuwatki.safe.speech

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer

/**
 * ฟังเสียงจากไมค์ต่อเนื่อง แล้วส่งข้อความที่ถอดได้ออกมาทีละประโยค
 * ใช้กับ "โหมดฟังผ่านลำโพง (ทดลอง)" — ต้องเรียกจาก main thread เท่านั้น
 *
 * ไม่มีการบันทึกเสียง: เสียงไปถึงตัวถอดเสียงของระบบแล้วทิ้ง เก็บแค่ข้อความ
 */
class LiveListener(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onStatus: (Status) -> Unit,
) {
    enum class Status { LISTENING, NO_SPEECH, NO_PERMISSION, UNAVAILABLE, STOPPED }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var preferOnDevice = true
    private var silentRounds = 0

    fun start() {
        if (running) return
        if (!Recognizers.isAvailable(context)) {
            onStatus(Status.UNAVAILABLE)
            return
        }
        running = true
        silentRounds = 0
        restart(0)
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        onStatus(Status.STOPPED)
    }

    private fun restart(delayMs: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (!running) return@postDelayed
            recognizer?.destroy()
            recognizer = Recognizers.create(context, preferOnDevice).also {
                it.setRecognitionListener(listener)
                it.startListening(Recognizers.baseIntent(preferOffline = preferOnDevice).apply {
                    putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                })
            }
        }, delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = onStatus(
            if (silentRounds >= SILENT_ROUNDS_WARNING) Status.NO_SPEECH else Status.LISTENING,
        )

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let(onPartial)
        }

        override fun onResults(results: Bundle?) {
            val text = firstResult(results)
            if (!text.isNullOrBlank()) {
                silentRounds = 0
                onText(text)
            } else {
                silentRounds++
            }
            restart(0)
        }

        override fun onError(error: Int) {
            if (!running) return
            when {
                error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    running = false
                    onStatus(Status.NO_PERMISSION)
                }
                Recognizers.isLanguageError(error) && preferOnDevice -> {
                    // ถอดภาษาไทยในเครื่องไม่ได้ -> ใช้ตัวถอดเสียงปกติของระบบแทน
                    preferOnDevice = false
                    restart(0)
                }
                error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    silentRounds++
                    if (silentRounds >= SILENT_ROUNDS_WARNING) onStatus(Status.NO_SPEECH)
                    restart(0)
                }
                else -> restart(RETRY_DELAY_MS)
            }
        }
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()

    companion object {
        /** เงียบติดกันกี่รอบถึงเตือนว่า "ไม่ได้ยินเสียง" (เครื่องอาจปิดไมค์ระหว่างสาย) */
        const val SILENT_ROUNDS_WARNING = 3
        private const val RETRY_DELAY_MS = 800L
    }
}
