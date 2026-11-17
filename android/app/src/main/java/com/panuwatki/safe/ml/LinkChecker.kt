package com.panuwatki.safe.ml

import java.util.Locale

/**
 * ตรวจลิงก์ในข้อความ (SMS / LINE) ด้วยกฎในเครื่อง — ไม่เปิดลิงก์ ไม่ต่ออินเทอร์เน็ต
 * อิงจากรูปแบบที่ AOC 1441 / ธปท. / ตำรวจไซเบอร์ เตือนบ่อย:
 *   ลิงก์ย่อ, ไฟล์ .apk, อ้างหน่วยงานรัฐแต่ไม่ใช่ .go.th, ชื่อเว็บเลียนแบบธนาคาร/ขนส่ง/ร้านค้า,
 *   ที่อยู่เป็นตัวเลข IP, ชื่อโดเมนที่ใช้ตัวอักษรหน้าตาเหมือน (punycode), นามสกุลเว็บที่ถูกใช้หลอกบ่อย
 */
object LinkChecker {

    enum class Level { INFO, CAUTION, HIGH }

    data class Finding(val url: String, val host: String, val level: Level, val reason: String)

    private val urlRegex = Regex(
        """(?i)\b(?:https?://[^\s<>"']+|www\.[^\s<>"']+|[a-z0-9][a-z0-9-]*(?:\.[a-z0-9-]+)*\.(?:com|net|org|info|co|th|ly|me|io|xyz|top|icu|cc|vip|shop|site|online|link|app|club|live|buzz|cn|in\.th|co\.th|go\.th|or\.th|ac\.th)(?:/[^\s<>"']*)?)""",
    )

    private val shorteners = setOf(
        "bit.ly", "tinyurl.com", "t.co", "cutt.ly", "shorturl.at", "rb.gy", "is.gd", "ow.ly",
        "s.id", "t.ly", "goo.gl", "tiny.cc", "rebrand.ly", "shorturl.asia",
    )

    /** นามสกุลเว็บราคาถูกที่มิจฉาชีพใช้ทำเว็บปลอมบ่อย (ไม่ได้แปลว่าทุกเว็บอันตราย) */
    private val riskyTlds = setOf("xyz", "top", "icu", "cc", "vip", "shop", "site", "online", "link", "club", "live", "buzz")

    /** คำที่แปลว่าข้อความ "อ้างเป็นหน่วยงานรัฐ" */
    private val govClaims = listOf(
        "กรม", "กระทรวง", "ตำรวจ", "สรรพากร", "ประกันสังคม", "การไฟฟ้า", "การประปา", "ศาล", "อัยการ",
        "ปปง", "ดีเอสไอ", "dsi", "ธนาคารแห่งประเทศไทย", "รัฐบาล", "เงินเยียวยา", "ภาษี",
    )

    /**
     * ชื่อแบรนด์ที่ถูกเลียนแบบบ่อย -> โดเมนจริง (registrable domain)
     * ใส่เฉพาะชื่อที่ยาวและเฉพาะเจาะจง เพื่อไม่ให้เตือนผิดง่าย
     */
    private val brands = mapOf(
        "kasikorn" to setOf("kasikornbank.com"),
        "kbank" to setOf("kasikornbank.com"),
        "krungthai" to setOf("krungthai.com"),
        "bangkokbank" to setOf("bangkokbank.com"),
        "krungsri" to setOf("krungsri.com"),
        "ttbbank" to setOf("ttbbank.com"),
        "thailandpost" to setOf("thailandpost.co.th"),
        "thaipost" to setOf("thailandpost.co.th"),
        "flashexpress" to setOf("flashexpress.co.th", "flashexpress.com"),
        "lazada" to setOf("lazada.co.th", "lazada.com"),
        "shopee" to setOf("shopee.co.th", "shopee.com"),
    )

    private val thaiSecondLevel = setOf("co", "or", "go", "ac", "in", "net", "mi")

    /** รัฐวิสาหกิจ/หน่วยงานที่เว็บจริงไม่ได้ลงท้ายด้วย .go.th (ไม่ต้องเตือนเรื่อง .go.th) */
    private val stateEnterprises = setOf(
        "pea.co.th", "mea.or.th", "mwa.co.th", "pwa.co.th", "gsb.or.th", "baac.or.th",
        "bot.or.th", "thailandpost.co.th",
    )

    fun extractUrls(text: String): List<String> =
        urlRegex.findAll(text).map { it.value.trimEnd('.', ',', ')', ']', '!', '?', '"', '\'') }.distinct().toList()

    fun hostOf(url: String): String {
        val noScheme = url.substringAfter("://", url)
        return noScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            .substringBefore(':').lowercase(Locale.ROOT).removePrefix("www.")
    }

    /** โดเมนหลักที่จดทะเบียน: shop.example.co.th -> example.co.th, a.b.example.com -> example.com */
    fun registrableDomain(host: String): String {
        val labels = host.split('.').filter { it.isNotEmpty() }
        if (labels.size <= 2) return labels.joinToString(".")
        val tld = labels.last()
        val sld = labels[labels.size - 2]
        val keep = if (tld == "th" && sld in thaiSecondLevel) 3 else 2
        return labels.takeLast(keep).joinToString(".")
    }

    fun check(text: String): List<Finding> {
        val claimsGov = govClaims.any { text.contains(it, ignoreCase = true) }
        return extractUrls(text).map { url -> assess(url, claimsGov) }
    }

    fun assess(url: String, textClaimsGov: Boolean): Finding {
        val host = hostOf(url)
        val domain = registrableDomain(host)
        val path = url.lowercase(Locale.ROOT).substringAfter(host, "")
        val tld = host.substringAfterLast('.', "")
        fun f(level: Level, reason: String) = Finding(url, host, level, reason)

        if (path.contains(".apk")) {
            return f(Level.HIGH, "ลิงก์ให้ดาวน์โหลดแอป (.apk) นอก Play Store — แอปดูดเงินมักมาแบบนี้ ห้ามติดตั้ง")
        }
        if (Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(host)) {
            return f(Level.HIGH, "ลิงก์เป็นตัวเลข IP ไม่ใช่ชื่อเว็บ — เว็บจริงของหน่วยงานไม่ทำแบบนี้")
        }
        if (host.split('.').any { it.startsWith("xn--") }) {
            return f(Level.HIGH, "ชื่อเว็บใช้ตัวอักษรหน้าตาคล้ายของจริง (เว็บปลอมแบบแนบเนียน)")
        }
        for ((token, official) in brands) {
            if (host.contains(token) && domain !in official) {
                return f(Level.HIGH, "ชื่อเว็บมีคำว่า \"$token\" แต่ไม่ใช่เว็บจริง (เว็บจริงคือ ${official.first()})")
            }
        }
        val looksGov = host.contains("gov") || host.contains("go-th") || host.contains("goth")
        if (!host.endsWith(".go.th") && domain !in stateEnterprises && (looksGov || textClaimsGov)) {
            val level = if (looksGov) Level.HIGH else Level.CAUTION
            return f(level, "ข้อความอ้างเป็นหน่วยงานรัฐ แต่ลิงก์ไม่ใช่ .go.th — เว็บหน่วยงานรัฐไทยจริงลงท้ายด้วย .go.th")
        }
        if (domain in shorteners) {
            return f(Level.CAUTION, "ลิงก์ย่อ — มองไม่เห็นว่าพาไปเว็บไหน มิจฉาชีพใช้บ่อย")
        }
        if (tld in riskyTlds) {
            return f(Level.CAUTION, "นามสกุลเว็บ .$tld ถูกใช้ทำเว็บปลอมบ่อย ตรวจให้แน่ใจก่อนกด")
        }
        return f(Level.INFO, "ไม่พบจุดผิดปกติจากชื่อลิงก์ (ไม่ได้แปลว่าปลอดภัย 100%)")
    }

    /** สีโดยรวมจากลิงก์ทั้งหมด (null = ไม่มีลิงก์ที่น่าสงสัย) */
    fun colorOf(findings: List<Finding>): RiskColor? = when {
        findings.any { it.level == Level.HIGH } -> RiskColor.RED
        findings.any { it.level == Level.CAUTION } -> RiskColor.YELLOW
        else -> null
    }
}
