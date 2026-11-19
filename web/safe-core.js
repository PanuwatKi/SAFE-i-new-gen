/*
 * safe-core.js — "สมอง" ของ SAFE ฉบับ JavaScript
 * ใช้ร่วมกันใน: เว็บแอป (เบราว์เซอร์), บอต LINE (Google Apps Script), และชุดทดสอบ (Node.js)
 *
 * ต้องคำนวณ "ตรงกับ" ml/safe_text.py + ml/export_model.py และฝั่ง Android (ScamClassifier.kt) ทุกขั้น
 *   normalize -> char_wb n-gram (2..5, นับเป็น code point) -> TF-IDF (sublinear + L2) -> Naive Bayes -> sigmoid calibration
 * ถ้าแก้ตรงนี้ ต้องแก้ทุกฝั่งให้ตรงกัน แล้วรัน  node web/test/core.test.js
 *
 * ไม่มีการส่งข้อมูลออกไปไหนในไฟล์นี้ — ทุกอย่างคำนวณในที่ที่โค้ดรันอยู่
 */
var SAFE = (function () {
  "use strict";

  // ---------------- ข้อความ ----------------
  var REPEAT_RE = /(.{2,15}?)\1{2,}/gu;
  // ชุดช่องว่างเดียวกับ str.isspace() ของ Python (ไม่รวม U+FEFF)
  var WS_RE = /[\u0009\u000a\u000b\u000c\u000d \u001c-\u001f\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000]+/u;

  function normalize(text) {
    if (typeof text !== "string") return "";
    var t = text.toLowerCase().split("ๆ").join(" ");
    return t.replace(REPEAT_RE, "$1");
  }

  function splitWhitespace(text) {
    return text.split(WS_RE).filter(function (p) { return p.length > 0; });
  }

  /** จำลอง sklearn analyzer="char_wb" (นับเป็น code point แบบ Python) */
  function charWbNgrams(text, minN, maxN) {
    var out = [];
    splitWhitespace(text).forEach(function (word) {
      var cps = Array.from(" " + word + " ");
      var len = cps.length;
      for (var n = minN; n <= maxN; n++) {
        var offset = 0;
        out.push(cps.slice(0, Math.min(n, len)).join(""));
        while (offset + n < len) {
          offset++;
          out.push(cps.slice(offset, offset + n).join(""));
        }
        if (offset === 0) break;
      }
    });
    return out;
  }

  function hasEnoughText(text) {
    return Array.from(normalize(text)).filter(function (c) { return !WS_RE.test(c); }).length >= 8;
  }

  // ---------------- ตัวจำแนก ----------------
  function createClassifier(model) {
    if (!model || model.format !== "safe-char-nb-v1") throw new Error("ไฟล์โมเดลไม่ถูกต้อง");
    var index = new Map();
    model.terms.forEach(function (t, i) { index.set(t, i); });
    var minN = model.ngram_range[0], maxN = model.ngram_range[1];

    /** % โอกาสเป็นมิจฉาชีพ — น้ำหนักหลักฐานผ่านตัวปรับ % (ตรงกับ ScamClassifier.kt) */
    function scamPercent(text) {
      var p = 1 / (1 + Math.exp(-evidence(text)));
      return 100 / (1 + Math.exp(model.calibration.a * p + model.calibration.b));
    }

    /** น้ำหนักหลักฐานดิบของ Naive Bayes (log-odds ก่อนปรับเป็น %) — ไม่มีเพดาน ใช้อธิบายคำ */
    function evidence(text) {
      var counts = new Map();
      charWbNgrams(normalize(text), minN, maxN).forEach(function (g) {
        var i = index.get(g);
        if (i !== undefined) counts.set(i, (counts.get(i) || 0) + 1);
      });
      var normSq = 0, values = [];
      counts.forEach(function (c, i) {
        var v = (1 + Math.log(c)) * model.idf[i];
        values.push([i, v]);
        normSq += v * v;
      });
      var z = model.bias;
      if (normSq > 0) {
        var norm = Math.sqrt(normSq);
        values.forEach(function (iv) { z += (iv[1] / norm) * model.weights[iv[0]]; });
      }
      return z;
    }

    function colorOf(percent) {
      if (percent >= model.thresholds.red) return "RED";
      if (percent >= model.thresholds.yellow) return "YELLOW";
      return "GREEN";
    }

    /** คำที่ทำให้สงสัย (ตัดคำด้วย Intl.Segmenter ถ้าเบราว์เซอร์/ระบบรองรับ — ใช้อธิบายเท่านั้น) */
    function explain(text, topN) {
      topN = topN || 5;
      if (typeof Intl === "undefined" || typeof Intl.Segmenter !== "function") return [];
      var seg = new Intl.Segmenter("th", { granularity: "word" });
      var base = evidence(text);
      var seen = new Set(), drops = [];
      for (var s of seg.segment(text)) {
        var w = s.segment.trim();
        if (!s.isWordLike || Array.from(w).length < 2 || seen.has(w)) continue;
        seen.add(w);
        var drop = base - evidence(text.split(w).join(" "));
        if (drop > 0.1) drops.push([w, drop]);
      }
      drops.sort(function (a, b) { return b[1] - a[1]; });
      return drops.slice(0, topN).map(function (d) { return d[0]; });
    }

    return {
      scamPercent: scamPercent,
      evidence: evidence,
      colorOf: colorOf,
      classify: function (text) { var p = scamPercent(text); return { percent: p, color: colorOf(p) }; },
      explain: explain,
      createdAt: model.created_at,
      featureCount: model.terms.length,
    };
  }

  /** บทสนทนาสะสมทั้งสาย + จำค่าสูงสุด (เคยแดงแล้วไม่กลับเป็นเขียว) */
  function createConversation(classifier) {
    var lines = [], peak = 0;
    function state() {
      var text = lines.join(" ");
      if (!hasEnoughText(text)) return { transcript: text, latest: null, peak: peak, peakColor: classifier.colorOf(peak), enoughData: false };
      var latest = classifier.classify(text);
      peak = Math.max(peak, latest.percent);
      return { transcript: text, latest: latest, peak: peak, peakColor: classifier.colorOf(peak), enoughData: true };
    }
    return {
      add: function (u) { u = (u || "").trim(); if (u) lines.push(u); return state(); },
      reset: function () { lines = []; peak = 0; },
    };
  }

  // ---------------- ตรวจลิงก์ (ตรงกับ LinkChecker.kt) ----------------
  var URL_RE = /\b(?:https?:\/\/[^\s<>"']+|www\.[^\s<>"']+|[a-z0-9][a-z0-9-]*(?:\.[a-z0-9-]+)*\.(?:com|net|org|info|co|th|ly|me|io|xyz|top|icu|cc|vip|shop|site|online|link|app|club|live|buzz|cn|in\.th|co\.th|go\.th|or\.th|ac\.th)(?:\/[^\s<>"']*)?)/gi;
  var SHORTENERS = ["bit.ly", "tinyurl.com", "t.co", "cutt.ly", "shorturl.at", "rb.gy", "is.gd", "ow.ly", "s.id", "t.ly", "goo.gl", "tiny.cc", "rebrand.ly", "shorturl.asia"];
  var RISKY_TLDS = ["xyz", "top", "icu", "cc", "vip", "shop", "site", "online", "link", "club", "live", "buzz"];
  var GOV_CLAIMS = ["กรม", "กระทรวง", "ตำรวจ", "สรรพากร", "ประกันสังคม", "การไฟฟ้า", "การประปา", "ศาล", "อัยการ", "ปปง", "ดีเอสไอ", "dsi", "ธนาคารแห่งประเทศไทย", "รัฐบาล", "เงินเยียวยา", "ภาษี"];
  var BRANDS = [
    ["kasikorn", ["kasikornbank.com"]], ["kbank", ["kasikornbank.com"]], ["krungthai", ["krungthai.com"]],
    ["bangkokbank", ["bangkokbank.com"]], ["krungsri", ["krungsri.com"]], ["ttbbank", ["ttbbank.com"]],
    ["thailandpost", ["thailandpost.co.th"]], ["thaipost", ["thailandpost.co.th"]],
    ["flashexpress", ["flashexpress.co.th", "flashexpress.com"]], ["lazada", ["lazada.co.th", "lazada.com"]],
    ["shopee", ["shopee.co.th", "shopee.com"]],
  ];
  var THAI_SLD = ["co", "or", "go", "ac", "in", "net", "mi"];
  var STATE_ENTERPRISES = ["pea.co.th", "mea.or.th", "mwa.co.th", "pwa.co.th", "gsb.or.th", "baac.or.th", "bot.or.th", "thailandpost.co.th"];

  function extractUrls(text) {
    var found = (text.match(URL_RE) || []).map(function (u) { return u.replace(/[.,)\]!?"']+$/, ""); });
    return Array.from(new Set(found));
  }
  function hostOf(url) {
    var s = url.indexOf("://") >= 0 ? url.slice(url.indexOf("://") + 3) : url;
    s = s.split("/")[0].split("?")[0].split("#")[0].split(":")[0].toLowerCase();
    return s.indexOf("www.") === 0 ? s.slice(4) : s;
  }
  function registrableDomain(host) {
    var labels = host.split(".").filter(Boolean);
    if (labels.length <= 2) return labels.join(".");
    var keep = labels[labels.length - 1] === "th" && THAI_SLD.indexOf(labels[labels.length - 2]) >= 0 ? 3 : 2;
    return labels.slice(-keep).join(".");
  }
  function assessLink(url, textClaimsGov) {
    var host = hostOf(url), domain = registrableDomain(host);
    var lower = url.toLowerCase();
    var path = lower.indexOf(host) >= 0 ? lower.slice(lower.indexOf(host) + host.length) : "";
    var tld = host.indexOf(".") >= 0 ? host.slice(host.lastIndexOf(".") + 1) : "";
    function f(level, reason) { return { url: url, host: host, level: level, reason: reason }; }
    if (path.indexOf(".apk") >= 0) return f("HIGH", "ลิงก์ให้ดาวน์โหลดแอป (.apk) นอก Play Store — แอปดูดเงินมักมาแบบนี้ ห้ามติดตั้ง");
    if (/^\d{1,3}(\.\d{1,3}){3}$/.test(host)) return f("HIGH", "ลิงก์เป็นตัวเลข IP ไม่ใช่ชื่อเว็บ — เว็บจริงของหน่วยงานไม่ทำแบบนี้");
    if (host.split(".").some(function (l) { return l.indexOf("xn--") === 0; })) return f("HIGH", "ชื่อเว็บใช้ตัวอักษรหน้าตาคล้ายของจริง (เว็บปลอมแบบแนบเนียน)");
    for (var i = 0; i < BRANDS.length; i++) {
      var token = BRANDS[i][0], official = BRANDS[i][1];
      if (host.indexOf(token) >= 0 && official.indexOf(domain) < 0) {
        return f("HIGH", "ชื่อเว็บมีคำว่า \"" + token + "\" แต่ไม่ใช่เว็บจริง (เว็บจริงคือ " + official[0] + ")");
      }
    }
    var looksGov = host.indexOf("gov") >= 0 || host.indexOf("go-th") >= 0 || host.indexOf("goth") >= 0;
    if (!/\.go\.th$/.test(host) && STATE_ENTERPRISES.indexOf(domain) < 0 && (looksGov || textClaimsGov)) {
      return f(looksGov ? "HIGH" : "CAUTION", "ข้อความอ้างเป็นหน่วยงานรัฐ แต่ลิงก์ไม่ใช่ .go.th — เว็บหน่วยงานรัฐไทยจริงลงท้ายด้วย .go.th");
    }
    if (SHORTENERS.indexOf(domain) >= 0) return f("CAUTION", "ลิงก์ย่อ — มองไม่เห็นว่าพาไปเว็บไหน มิจฉาชีพใช้บ่อย");
    if (RISKY_TLDS.indexOf(tld) >= 0) return f("CAUTION", "นามสกุลเว็บ ." + tld + " ถูกใช้ทำเว็บปลอมบ่อย ตรวจให้แน่ใจก่อนกด");
    return f("INFO", "ไม่พบจุดผิดปกติจากชื่อลิงก์ (ไม่ได้แปลว่าปลอดภัย 100%)");
  }
  function checkLinks(text) {
    var lower = text.toLowerCase();
    var claimsGov = GOV_CLAIMS.some(function (k) { return lower.indexOf(k) >= 0; });
    return extractUrls(text).map(function (u) { return assessLink(u, claimsGov); });
  }
  function linkColor(findings) {
    if (findings.some(function (x) { return x.level === "HIGH"; })) return "RED";
    if (findings.some(function (x) { return x.level === "CAUTION"; })) return "YELLOW";
    return null;
  }

  // ---------------- เบอร์โทร (ตรงกับ NumberRisk.kt) ----------------
  function normalizeNumber(raw) {
    if (!raw || !String(raw).trim()) return null;
    raw = String(raw);
    var plus = raw.trim().charAt(0) === "+";
    var d = raw.replace(/\D/g, "");
    if (!d) return null;
    if (plus) return "+" + d;
    if (/^00[1789]/.test(d)) return "+" + d.slice(3);
    if (d.charAt(0) === "0" && d.length >= 9 && d.length <= 10) return "+66" + d.slice(1);
    if (d.indexOf("66") === 0 && d.length >= 10 && d.length <= 11) return "+" + d;
    return d;
  }
  function assessNumber(raw) {
    var n = normalizeNumber(raw);
    if (!n) return { level: "CAUTION", reason: "ไม่แสดงหมายเลข มิจฉาชีพมักซ่อนเบอร์" };
    if (n.indexOf("+697") === 0) return { level: "HIGH", reason: "+697 คือสายโทรผ่านอินเทอร์เน็ตจากต่างประเทศ ซึ่งแก๊งคอลเซ็นเตอร์ใช้บ่อยมาก ห้ามโอนเงิน ห้ามบอกรหัส" };
    if (n.indexOf("+698") === 0) return { level: "CAUTION", reason: "+698 คือเบอร์มือถือไทยที่โรมมิ่งอยู่ต่างประเทศ ถ้าไม่มีคนรู้จักอยู่ต่างประเทศ ให้ระวัง" };
    if (n.indexOf("+66") === 0) {
      var nsn = n.slice(3);
      if (!nsn) return { level: "CAUTION", reason: "สายจากต่างประเทศที่ไม่แสดงเบอร์ต้นทาง อย่าเชื่อ อย่าโอนเงิน" };
      if (nsn.charAt(0) === "0" || nsn.length < 8 || nsn.length > 9) return { level: "CAUTION", reason: "รูปแบบเบอร์ผิดปกติ อาจเป็นเบอร์ปลอม" };
      return { level: "NONE", reason: "" };
    }
    if (n.charAt(0) === "+") return { level: "CAUTION", reason: "สายจากต่างประเทศ ถ้าไม่ได้รอสายนี้ อย่าเชื่อ อย่าโอนเงิน" };
    return { level: "NONE", reason: "" };
  }

  // ---------------- ถามตอบ 5 ข้อ (ตรงกับ ScamChecklist.kt) ----------------
  var QUESTIONS = [
    { id: "CLAIMS_AUTHORITY", red: false, text: "เขาอ้างว่าเป็น ตำรวจ ธนาคาร หน่วยงานรัฐ ขนส่ง ค่ายมือถือ หรือคนรู้จักไหม?", hint: "เช่น บอกว่าโทรจากสถานีตำรวจ ธนาคาร การไฟฟ้า กรมสรรพากร หรือบอกว่าเป็นลูกหลาน", reason: "อ้างเป็นหน่วยงานหรือคนรู้จัก — มุกที่แก๊งคอลเซ็นเตอร์ใช้บ่อยที่สุด" },
    { id: "ASKS_MONEY", red: true, text: "เขาให้โอนเงิน ย้ายเงิน หรือจ่ายค่าธรรมเนียมก่อนไหม?", hint: "เช่น โอนไป \"บัญชีปลอดภัย\" เพื่อตรวจสอบ จ่ายค่าปรับ ค่าภาษี ค่ามัดจำ", reason: "ขอให้โอนเงิน — ธนาคารและตำรวจไม่มี \"บัญชีปลอดภัย\"" },
    { id: "ASKS_SECRET", red: true, text: "เขาขอรหัส OTP รหัสผ่าน เลขบัตร หรือให้สแกนหน้าไหม?", hint: "รวมถึงขอให้อ่านตัวเลขที่ขึ้นบนจอให้ฟัง", reason: "ขอ OTP/รหัส/สแกนหน้า — ใครได้ไปก็ถอนเงินจากบัญชีคุณได้" },
    { id: "ASKS_LINK_APP", red: true, text: "เขาให้กดลิงก์ ติดตั้งแอป เพิ่มไลน์ หรือแชร์หน้าจอไหม?", hint: "เช่น ให้โหลดแอปเพื่อ \"ยืนยันตัวตน\" หรือ \"รับเงินคืน\"", reason: "ให้กดลิงก์/ติดตั้งแอป/แชร์หน้าจอ — ทางที่แอปดูดเงินเข้ามาในมือถือ" },
    { id: "PRESSURE", red: false, text: "เขาขู่ เร่งให้รีบทำ ห้ามวางสาย หรือห้ามบอกใครไหม?", hint: "เช่น ขู่ว่าจะถูกจับ บัญชีจะถูกอายัด หรือบอกว่าเป็นความลับทางคดี", reason: "ขู่หรือเร่งรีบ — ทำให้ไม่มีเวลาคิดและไม่ปรึกษาใคร" },
  ];
  /** answers: { QUESTION_ID: true|false } (ไม่มี = ไม่แน่ใจ) */
  function evaluateChecklist(answers) {
    var yes = QUESTIONS.filter(function (q) { return answers[q.id] === true; });
    var reasons = yes.map(function (q) { return q.reason; });
    var ids = yes.map(function (q) { return q.id; });
    if (yes.some(function (q) { return q.red; })) {
      return { color: "RED", title: "เสี่ยงมิจฉาชีพสูงมาก", reasons: reasons.concat(["หน่วยงานจริงไม่โทรมาขอให้โอนเงิน ขอ OTP หรือให้ติดตั้งแอป"]) };
    }
    if (ids.indexOf("PRESSURE") >= 0) return { color: "YELLOW", title: "น่าสงสัย ระวังไว้", reasons: reasons };
    if (ids.indexOf("CLAIMS_AUTHORITY") >= 0) {
      return { color: "YELLOW", title: "ยังไม่พบสัญญาณอันตราย แต่ควรตรวจสอบ", reasons: reasons.concat(["วางสายแล้วโทรกลับเบอร์ทางการของหน่วยงานนั้นเองก่อนทำตามใด ๆ"]) };
    }
    return { color: "GREEN", title: "ไม่พบสัญญาณมิจฉาชีพจากคำตอบ", reasons: ["ถ้ามีอะไรแปลก ๆ ภายหลัง กลับมาตอบใหม่ได้เสมอ"] };
  }

  // ---------------- ลบข้อมูลส่วนตัว (ตรงกับ PiiRedactor.kt) ----------------
  var EMAIL_RE = /[\w.+-]+@[\w-]+(?:\.[\w-]+)+/g;
  // โดเมนหลายชั้นได้ (scb.co.th) และ TLD ต้องจบคำ (x.comic ไม่ใช่ลิงก์)
  var URL_REDACT_RE = /\b(?:https?:\/\/|www\.)\S+|\b[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|net|org|co|th|ly|me|io|xyz|link|app|cc|info)(?![a-z0-9-])(?:\/\S*)?/gi;
  var DIGITS_RE = /\+?\d(?:[\d\s\-.]*\d)?/g;
  function redact(text) {
    var t = String(text).replace(/[๐-๙]/g, function (c) { return String(c.charCodeAt(0) - 0x0E50); });
    t = t.replace(EMAIL_RE, "[อีเมล]");
    t = t.replace(URL_REDACT_RE, "[ลิงก์]");
    return t.replace(DIGITS_RE, function (raw) {
      var d = raw.replace(/\D/g, "");
      if (d.length < 4) return raw;
      if (d.length === 13 && raw.charAt(0) !== "+") return "[เลขบัตร]";
      if (raw.charAt(0) === "+" || (d.charAt(0) === "0" && d.length >= 9 && d.length <= 10) || (d.indexOf("66") === 0 && d.length >= 10 && d.length <= 11)) return "[เบอร์โทร]";
      return "[ตัวเลข]";
    });
  }

  function maxColor(a, b) {
    var rank = { GREEN: 0, YELLOW: 1, RED: 2 };
    if (!a) return b || null;
    if (!b) return a;
    return rank[a] >= rank[b] ? a : b;
  }

  return {
    normalize: normalize,
    charWbNgrams: charWbNgrams,
    hasEnoughText: hasEnoughText,
    createClassifier: createClassifier,
    createConversation: createConversation,
    links: { extract: extractUrls, hostOf: hostOf, registrableDomain: registrableDomain, assess: assessLink, check: checkLinks, colorOf: linkColor },
    numbers: { normalize: normalizeNumber, assess: assessNumber },
    checklist: { questions: QUESTIONS, evaluate: evaluateChecklist },
    redact: redact,
    maxColor: maxColor,
  };
})();

if (typeof module !== "undefined" && module.exports) module.exports = SAFE;
