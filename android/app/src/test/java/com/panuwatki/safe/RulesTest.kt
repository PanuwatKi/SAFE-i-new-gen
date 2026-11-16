package com.panuwatki.safe

import com.panuwatki.safe.calls.CallRecordings
import com.panuwatki.safe.calls.NumberRiskLevel
import com.panuwatki.safe.calls.NumberRiskRules
import com.panuwatki.safe.data.AnalysisEntity
import com.panuwatki.safe.data.CsvExporter
import com.panuwatki.safe.ml.PiiRedactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiiRedactorTest {
    @Test
    fun redactsPersonalNumbers() {
        assertEquals("โทรกลับ ${PiiRedactor.PHONE} นะ", PiiRedactor.redact("โทรกลับ 081-234-5678 นะ"))
        assertEquals("เบอร์ ${PiiRedactor.PHONE}", PiiRedactor.redact("เบอร์ +66 81 234 5678"))
        assertEquals("บัตร ${PiiRedactor.ID_CARD}", PiiRedactor.redact("บัตร 1-2345-67890-12-3"))
        assertEquals("บัญชี ${PiiRedactor.NUMBER}", PiiRedactor.redact("บัญชี 123-4-56789-0"))
        assertEquals("รหัส OTP ${PiiRedactor.NUMBER}", PiiRedactor.redact("รหัส OTP 482913"))
        assertEquals("รหัส ${PiiRedactor.NUMBER}", PiiRedactor.redact("รหัส ๔๘๒๙๑๓"))
    }

    @Test
    fun redactsLinksAndEmails() {
        assertEquals("กด ${PiiRedactor.LINK} เลย", PiiRedactor.redact("กด https://bit.ly/abc123 เลย"))
        assertEquals("กด ${PiiRedactor.LINK} เลย", PiiRedactor.redact("กด thai-post.xyz/track เลย"))
        assertEquals("ส่งมา ${PiiRedactor.EMAIL}", PiiRedactor.redact("ส่งมา somchai@example.com"))
    }

    @Test
    fun keepsShortNumbers() {
        assertEquals("ค่าปรับ 350 บาท ภายใน 24 ชม.", PiiRedactor.redact("ค่าปรับ 350 บาท ภายใน 24 ชม."))
    }
}

class NumberRiskTest {
    @Test
    fun spoofedPrefixesAreHigh() {
        assertEquals(NumberRiskLevel.HIGH, NumberRiskRules.assess("+697123456789").level)
        assertEquals(NumberRiskLevel.HIGH, NumberRiskRules.assess("+698 12 345 6789").level)
    }

    @Test
    fun foreignAndHiddenAreCaution() {
        assertEquals(NumberRiskLevel.CAUTION, NumberRiskRules.assess("+85512345678").level)
        assertEquals(NumberRiskLevel.CAUTION, NumberRiskRules.assess("00185512345678").level)
        assertEquals(NumberRiskLevel.CAUTION, NumberRiskRules.assess(null).level)
        assertEquals(NumberRiskLevel.CAUTION, NumberRiskRules.assess("").level)
    }

    @Test
    fun thaiNumbersAreNone() {
        assertEquals(NumberRiskLevel.NONE, NumberRiskRules.assess("0812345678").level)
        assertEquals(NumberRiskLevel.NONE, NumberRiskRules.assess("+66812345678").level)
        assertEquals(NumberRiskLevel.NONE, NumberRiskRules.assess("1441").level)
    }
}

class CallRecordingsTest {
    @Test
    fun detectsCommonRecorderFolders() {
        assertTrue(CallRecordings.isCallRecording("Recordings/Call/", "Call recording 0812345678_240101.m4a"))
        assertTrue(CallRecordings.isCallRecording("MIUI/sound_recorder/call_rec/", "rec.mp3"))
        assertTrue(CallRecordings.isCallRecording("Music/Recordings/Call Recordings/", "a.m4a"))
        assertTrue(CallRecordings.isCallRecording("Call/", "x.amr"))
    }

    @Test
    fun ignoresOtherAudio() {
        assertFalse(CallRecordings.isCallRecording("Music/", "song.mp3"))
        assertFalse(CallRecordings.isCallRecording("Recordings/Voice Recorder/", "memo.m4a"))
        assertFalse(CallRecordings.isCallRecording("WhatsApp/Media/WhatsApp Voice Notes/", "PTT-1.opus"))
    }
}

class CsvExporterTest {
    @Test
    fun exportsOnlyLabeledRowsInDatasetFormat() {
        val rows = listOf(
            AnalysisEntity(1, 0, "live", "โอนเงิน \"ด่วน\"\nเลย", 90.0, "RED", 1, "ธนาคาร"),
            AnalysisEntity(2, 0, "live", "ยังไม่ยืนยัน", 10.0, "GREEN", null, null),
            AnalysisEntity(3, 0, "recording", "กินข้าวยัง", 5.0, "GREEN", 0, null),
        )
        val lines = CsvExporter.toCsv(rows).removePrefix("﻿").trimEnd().split("\r\n")
        assertEquals(CsvExporter.HEADER, lines[0])
        assertEquals("\"โอนเงิน \"\"ด่วน\"\" เลย\",1,\"ธนาคาร\",call", lines[1])
        assertEquals("\"กินข้าวยัง\",0,\"\",call", lines[2])
        assertEquals(3, lines.size)
    }
}
