package com.panuwatki.safe.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** ตัวช่วยสร้าง SpeechRecognizer: ใช้แบบ "ถอดเสียงในเครื่อง" ก่อนเสมอ ถ้าเครื่องรองรับ */
object Recognizers {
    const val LANGUAGE = "th-TH"

    fun isAvailable(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun onDeviceAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun create(context: Context, preferOnDevice: Boolean): SpeechRecognizer =
        if (preferOnDevice && onDeviceAvailable(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

    fun baseIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    /** error ที่แปลว่า "เครื่องนี้ถอดภาษาไทยในเครื่องไม่ได้" -> ให้ลองตัวถอดเสียงปกติแทน */
    fun isLanguageError(error: Int): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
}
