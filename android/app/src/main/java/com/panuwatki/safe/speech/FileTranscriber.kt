package com.panuwatki.safe.speech

import android.content.Context
import android.media.AudioFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.coroutines.resume

/**
 * ถอดเสียงจาก "ไฟล์" (เช่น ไฟล์อัดเสียงสายของแอปโทรศัพท์) เป็นข้อความทีละช่วง
 *
 * ใช้ SpeechRecognizer + EXTRA_AUDIO_SOURCE (Android 13 ขึ้นไป)
 * - ไฟล์เสียงถูกแปลงเป็น PCM ชั่วคราวใน cache ของแอป แล้ว "ลบทิ้งทันที" หลังถอดเสร็จ
 * - ⚠️ ทดลอง: ตัวถอดเสียงบางเครื่องไม่รองรับการรับเสียงจากไฟล์ ต้องทดสอบบนเครื่องจริง
 *   ถ้าไม่รองรับ จะคืน Result.failure แล้วแอปแจ้งผู้ใช้
 */
object FileTranscriber {

    class NotSupportedException(message: String) : Exception(message)

    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && Recognizers.isAvailable(context)

    suspend fun transcribe(context: Context, uri: Uri): Result<List<String>> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return Result.failure(NotSupportedException("ต้องใช้ Android 13 ขึ้นไป"))
        }
        if (!Recognizers.isAvailable(context)) {
            return Result.failure(NotSupportedException("เครื่องนี้ไม่มีระบบถอดเสียง"))
        }
        val pcm = File.createTempFile("safe_pcm_", ".raw", context.cacheDir)
        return try {
            val samples = withContext(Dispatchers.IO) {
                pcm.outputStream().buffered().use { AudioDecoder.decodeToPcm16Mono(context, uri, it) }
            }
            if (samples < AudioDecoder.TARGET_RATE) return Result.failure(IllegalStateException("ไฟล์เสียงสั้นเกินไป"))
            val seconds = samples / AudioDecoder.TARGET_RATE
            val timeoutMs = (seconds * 2 + 60) * 1000
            withTimeout(timeoutMs) {
                var result = runRecognizer(context, pcm, preferOnDevice = true)
                if (result.exceptionOrNull() is LanguageUnavailable) {
                    result = runRecognizer(context, pcm, preferOnDevice = false)
                }
                result
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(NotSupportedException("ถอดเสียงนานเกินไป"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            pcm.delete()
        }
    }

    private class LanguageUnavailable : Exception("ถอดภาษาไทยในเครื่องไม่ได้")

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun runRecognizer(context: Context, pcm: File, preferOnDevice: Boolean): Result<List<String>> =
        withContext(Dispatchers.Main) {
            val pfd = ParcelFileDescriptor.open(pcm, ParcelFileDescriptor.MODE_READ_ONLY)
            val recognizer = Recognizers.create(context, preferOnDevice)
            try {
                suspendCancellableCoroutine { cont ->
                    val segments = mutableListOf<String>()
                    fun finish(r: Result<List<String>>) {
                        if (cont.isActive) cont.resume(r)
                    }
                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}

                        override fun onSegmentResults(segmentResults: Bundle) {
                            first(segmentResults)?.let { segments += it }
                        }

                        override fun onEndOfSegmentedSession() = finish(Result.success(segments.toList()))

                        override fun onResults(results: Bundle?) {
                            first(results)?.let { segments += it }
                            finish(Result.success(segments.toList()))
                        }

                        override fun onError(error: Int) {
                            when {
                                Recognizers.isLanguageError(error) -> finish(Result.failure(LanguageUnavailable()))
                                segments.isNotEmpty() -> finish(Result.success(segments.toList()))
                                error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                    finish(Result.success(emptyList()))
                                else -> finish(Result.failure(NotSupportedException("ถอดเสียงจากไฟล์ไม่สำเร็จ (รหัส $error)")))
                            }
                        }
                    })
                    val intent = Recognizers.baseIntent().apply {
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, AudioDecoder.TARGET_RATE)
                        putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                    }
                    recognizer.startListening(intent)
                }
            } finally {
                recognizer.destroy()
                pfd.close()
            }
        }

    private fun first(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}
