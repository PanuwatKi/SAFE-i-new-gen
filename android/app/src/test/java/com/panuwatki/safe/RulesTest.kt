package com.panuwatki.safe

import com.panuwatki.safe.calls.CallRecordings
import com.panuwatki.safe.calls.CallerCheck
import com.panuwatki.safe.calls.NetworkVerification
import com.panuwatki.safe.calls.NumberRiskLevel
import com.panuwatki.safe.calls.NumberRiskRules
import com.panuwatki.safe.calls.PhoneNumbers
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
        assertEquals("โอนผ่าน ${PiiRedactor.LINK} ด่วน", PiiRedactor.redact("โอนผ่าน scb.co.th/abc ด่วน"))
        assertEquals("อ่าน x.comic นะ", PiiRedactor.redact("อ่าน x.comic นะ"))
        assertEquals("ส่งมา ${PiiRedactor.EMAIL}", PiiRedactor.redact("ส่งมา somchai@example.com"))
    }

    @Test
    fun keepsShortNumbers() {
        assertEquals("ค่าปรับ 350 บาท ภายใน 24 ชม.", PiiRedactor.redact("ค่าปรับ 350 บาท ภายใน 24 ชม."))
    }
}

class NumberRiskTest {
    @Test
    fun nbtcPrefixes() {
        // +697 = โทรผ่านอินเทอร์เน็ตจากต่างประเทศ (เสี่ยงสูง), +698 = เบอร์ไทยโรมมิ่ง (ระวัง)
        assertEquals(NumberRiskLevel.HIGH, NumberRiskRules.assess("+697123456789").level)
        assertEquals(NumberRiskLevel.CAUTION, NumberRiskRules.assess("+698 81 234 5678").level)
        assertEquals(NumberRiskLevel.CAUTION, NumberRiskRules.assess("+66").level) // ต่างประเทศไม่มีเลขต้นทาง
    }

    @Test
    fun foreignHiddenAndMalformedAreCaution() {
        assertEquals(NumberRiskLevel.CAUTION, NumberRiskRules.assess("+85512345678").level)
        assertEquals(NumberRiskLevel.CAUTION, NumberRiskRules.assess("00185512345678").level)
        assertEquals(NumberRiskLevel.CAUTION, NumberRiskRules.assess(null).level)
        assertEquals(NumberRiskLevel.CAUTION, NumberRiskRules.assess("").level)
        assertEquals(NumberRiskLevel.CAUTION, NumberRiskRules.assess("+660812345678").level) // +66 ตามด้วย 0 = ผิดรูป
        assertEquals(NumberRiskLevel.CAUTION, NumberRiskRules.assess("+6681234").level)       // สั้นเกิน
    }

    @Test
    fun thaiNumbersAreNone() {
        assertEquals(NumberRiskLevel.NONE, NumberRiskRules.assess("0812345678").level)
        assertEquals(NumberRiskLevel.NONE, NumberRiskRules.assess("+66812345678").level)
        assertEquals(NumberRiskLevel.NONE, NumberRiskRules.assess("02-123-4567").level)
        assertEquals(NumberRiskLevel.NONE, NumberRiskRules.assess("1441").level)
    }

    @Test
    fun networkSaysSpoofed() {
        assertEquals(NumberRiskLevel.HIGH, NumberRiskRules.assess("0812345678", NetworkVerification.FAILED).level)
        assertEquals(NumberRiskLevel.NONE, NumberRiskRules.assess("0812345678", NetworkVerification.PASSED).level)
    }
}

class PhoneNumbersTest {
    @Test
    fun normalizesThaiFormats() {
        assertEquals("+66812345678", PhoneNumbers.normalize("081-234-5678"))
        assertEquals("+66812345678", PhoneNumbers.normalize("+66 81 234 5678"))
        assertEquals("+66812345678", PhoneNumbers.normalize("66812345678"))
        assertEquals("+6621234567", PhoneNumbers.normalize("02 123 4567"))
        assertEquals("+85512345678", PhoneNumbers.normalize("001 855 12 345 678"))
        assertEquals("1441", PhoneNumbers.normalize("1441"))
        assertEquals(null, PhoneNumbers.normalize("  "))
        assertEquals(null, PhoneNumbers.normalize(null))
    }

    @Test
    fun displaysReadably() {
        assertEquals("081-234-5678", PhoneNumbers.display("+66812345678"))
        assertEquals("02-123-4567", PhoneNumbers.display("+6621234567"))
        assertEquals("ไม่แสดงหมายเลข", PhoneNumbers.display(null))
    }

    @Test
    fun communityKeyIsStableHexAndSameForEquivalentInput() {
        val a = PhoneNumbers.communityKey(PhoneNumbers.normalize("081-234-5678")!!)
        val b = PhoneNumbers.communityKey(PhoneNumbers.normalize("+66812345678")!!)
        assertEquals(a, b)
        assertTrue(a.matches(Regex("^[0-9a-f]{64}$"))) // ต้องตรงกับกฎใน firebase/firestore.rules
    }
}

class CallerCheckTest {
    private val scamBook = CallerCheck.BookInfo(CallerCheck.BookLabel.SCAM, "ธนาคาร", null, autoReject = true)
    private val trusted = CallerCheck.BookInfo(CallerCheck.BookLabel.TRUSTED, null, null, autoReject = false)

    @Test
    fun userBookWinsOverRules() {
        val d = CallerCheck.decide("0812345678", NetworkVerification.UNKNOWN, scamBook, null)
        assertEquals(NumberRiskLevel.HIGH, d.level)
        assertTrue(d.reject)
        // ผู้ใช้ไว้ใจเบอร์ต่างประเทศเอง -> ไม่เตือน
        assertEquals(NumberRiskLevel.NONE, CallerCheck.decide("+85512345678", NetworkVerification.UNKNOWN, trusted, null).level)
    }

    @Test
    fun spoofDetectionOverridesTrusted() {
        val d = CallerCheck.decide("0812345678", NetworkVerification.FAILED, trusted, null)
        assertEquals(NumberRiskLevel.HIGH, d.level)
        assertFalse(d.reject)
    }

    @Test
    fun communityNeedsThreshold() {
        val below = CallerCheck.CommunityInfo(scamReports = 2, spamReports = 0)
        val enough = CallerCheck.CommunityInfo(scamReports = 3, spamReports = 1)
        val spammy = CallerCheck.CommunityInfo(scamReports = 1, spamReports = 4)
        assertEquals(NumberRiskLevel.NONE, CallerCheck.decide("0812345678", NetworkVerification.UNKNOWN, null, below).level)
        assertEquals(NumberRiskLevel.HIGH, CallerCheck.decide("0812345678", NetworkVerification.UNKNOWN, null, enough).level)
        assertEquals(NumberRiskLevel.CAUTION, CallerCheck.decide("0812345678", NetworkVerification.UNKNOWN, null, spammy).level)
    }

    @Test
    fun neverRejectsWithoutUserChoice() {
        val community = CallerCheck.CommunityInfo(scamReports = 50, spamReports = 0)
        assertFalse(CallerCheck.decide("+697123", NetworkVerification.FAILED, null, community).reject)
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
