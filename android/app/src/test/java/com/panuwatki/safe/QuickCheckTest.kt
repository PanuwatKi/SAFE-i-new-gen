package com.panuwatki.safe

import com.panuwatki.safe.data.AnalysisEntity
import com.panuwatki.safe.data.CsvExporter
import com.panuwatki.safe.ml.LinkChecker
import com.panuwatki.safe.ml.LinkChecker.Level
import com.panuwatki.safe.ml.RiskColor
import com.panuwatki.safe.ml.ScamChecklist
import com.panuwatki.safe.ml.ScamChecklist.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkCheckerTest {

    @Test
    fun extractsLinksFromThaiText() {
        assertEquals(listOf("https://bit.ly/abc123"), LinkChecker.extractUrls("กด https://bit.ly/abc123 เลยค่ะ"))
        assertEquals(listOf("www.kasikornbank.com"), LinkChecker.extractUrls("ดูที่ www.kasikornbank.com ครับ"))
        assertEquals(emptyList<String>(), LinkChecker.extractUrls("ไม่มีลิงก์ ค่าส่ง 3.50 บาท"))
    }

    @Test
    fun registrableDomainHandlesThaiSecondLevel() {
        assertEquals("example.co.th", LinkChecker.registrableDomain("shop.example.co.th"))
        assertEquals("example.com", LinkChecker.registrableDomain("a.b.example.com"))
        assertEquals("rd.go.th", LinkChecker.registrableDomain("rd.go.th"))
    }

    @Test
    fun flagsDangerousLinks() {
        assertEquals(Level.HIGH, LinkChecker.assess("https://example.com/update.apk", false).level)
        assertEquals(Level.HIGH, LinkChecker.assess("http://203.0.113.5/login", false).level)
        assertEquals(Level.HIGH, LinkChecker.assess("https://xn--kasikrnbank-9za.com", false).level)
        assertEquals(Level.HIGH, LinkChecker.assess("http://kbank-secure.com/login", false).level)
        assertEquals(Level.HIGH, LinkChecker.assess("https://thaipost-delivery.xyz/pay", false).level)
        assertEquals(Level.HIGH, LinkChecker.assess("https://gov-th-refund.com", false).level)
        assertEquals(Level.CAUTION, LinkChecker.assess("https://bit.ly/abc", false).level)
        assertEquals(Level.CAUTION, LinkChecker.assess("https://promo-gift.top", false).level)
    }

    @Test
    fun officialLinksAreNotFlagged() {
        assertEquals(Level.INFO, LinkChecker.assess("https://www.kasikornbank.com/th", false).level)
        assertEquals(Level.INFO, LinkChecker.assess("https://shopee.co.th/cart", false).level)
    }

    @Test
    fun governmentClaimNeedsGoTh() {
        val fake = LinkChecker.check("กรมสรรพากรแจ้งคืนภาษี กดรับเงินที่ https://refund-tax.com")
        assertEquals(Level.CAUTION, fake.single().level)
        val real = LinkChecker.check("กรมสรรพากรแจ้ง ดูรายละเอียดที่ https://www.rd.go.th")
        assertEquals(Level.INFO, real.single().level)
        // รัฐวิสาหกิจที่เว็บจริงไม่ใช่ .go.th ไม่ต้องเตือน
        val pea = LinkChecker.check("การไฟฟ้าแจ้งดับไฟ ดูที่ https://www.pea.co.th")
        assertEquals(Level.INFO, pea.single().level)
    }

    @Test
    fun overallColor() {
        assertEquals(RiskColor.RED, LinkChecker.colorOf(LinkChecker.check("โหลดแอป https://x.com/a.apk และ https://bit.ly/z")))
        assertEquals(RiskColor.YELLOW, LinkChecker.colorOf(LinkChecker.check("https://bit.ly/z")))
        assertNull(LinkChecker.colorOf(LinkChecker.check("https://www.kasikornbank.com")))
        assertNull(LinkChecker.colorOf(emptyList()))
    }
}

class ScamChecklistTest {

    @Test
    fun redFlagsGiveRed() {
        assertEquals(RiskColor.RED, ScamChecklist.evaluate(mapOf(Question.ASKS_MONEY to true)).color)
        assertEquals(RiskColor.RED, ScamChecklist.evaluate(mapOf(Question.ASKS_SECRET to true)).color)
        assertEquals(RiskColor.RED, ScamChecklist.evaluate(mapOf(Question.ASKS_LINK_APP to true)).color)
        val r = ScamChecklist.evaluate(mapOf(Question.CLAIMS_AUTHORITY to true, Question.ASKS_MONEY to true))
        assertTrue(r.reasons.size >= 2)
    }

    @Test
    fun pressureOrClaimAloneGivesYellow() {
        assertEquals(RiskColor.YELLOW, ScamChecklist.evaluate(mapOf(Question.PRESSURE to true)).color)
        assertEquals(RiskColor.YELLOW, ScamChecklist.evaluate(mapOf(Question.CLAIMS_AUTHORITY to true, Question.ASKS_MONEY to false)).color)
    }

    @Test
    fun allNoGivesGreen() {
        val all = Question.entries.associateWith { false }
        assertEquals(RiskColor.GREEN, ScamChecklist.evaluate(all).color)
    }
}

class CsvChannelTest {
    @Test
    fun textChecksExportAsChat() {
        val rows = listOf(AnalysisEntity(1, 0, AnalysisEntity.SOURCE_TEXT, "กดลิงก์รับเงินคืน", 90.0, "RED", 1, "ฟิชชิง"))
        val line = CsvExporter.toCsv(rows).removePrefix("﻿").trimEnd().split("\r\n")[1]
        assertTrue(line, line.endsWith(",chat"))
    }
}
