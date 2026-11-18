// ทดสอบ safe-core.js ให้ผลตรงกับ Python (test vectors) และกติกาเดียวกับฝั่ง Android
// รัน:  node --test web/test/
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const SAFE = require("../safe-core.js");

const root = path.join(__dirname, "..", "..");
const model = JSON.parse(fs.readFileSync(path.join(root, "web", "scam_model.json"), "utf8"));
const clf = SAFE.createClassifier(model);

test("web model is the same file the Android app ships", () => {
  const android = fs.readFileSync(path.join(root, "android", "app", "src", "main", "assets", "scam_model.json"), "utf8");
  assert.equal(fs.readFileSync(path.join(root, "web", "scam_model.json"), "utf8"), android);
});

test("matches Python reference vectors", () => {
  const vectors = JSON.parse(fs.readFileSync(path.join(root, "android", "app", "src", "test", "resources", "model_test_vectors.json"), "utf8"));
  assert.ok(vectors.length > 20);
  for (const v of vectors) {
    const got = clf.scamPercent(v.text);
    assert.ok(Math.abs(got - v.percent) < 1e-3, `text="${v.text}" expected ${v.percent} got ${got}`);
  }
});

test("normalize and char_wb match Python/sklearn", () => {
  assert.equal(SAFE.normalize("คุณคุณคุณ"), "คุณ");
  assert.equal(SAFE.normalize("OTP"), "otp");
  assert.equal(SAFE.normalize("ไปๆ ไป"), "ไป  ไป");
  assert.deepEqual(SAFE.charWbNgrams("ab c", 2, 3), [" a", "ab", "b ", " ab", "ab ", " c", "c ", " c "]);
  assert.deepEqual(SAFE.charWbNgrams("   ", 2, 5), []);
});

test("obvious cases and conversation peak", () => {
  assert.equal(clf.classify("โทรจากธนาคารนะคะ บัญชีของคุณถูกอายัด กรุณาโอนเงินไปบัญชีปลอดภัยด่วน").color, "RED");
  assert.equal(clf.classify("แม่จ๋า เย็นนี้กินข้าวบ้านไหม").color, "GREEN");
  const c = SAFE.createConversation(clf);
  assert.equal(c.add("ฮัลโหล").enoughData, false);
  assert.equal(c.add("โทรจากสถานีตำรวจ บัญชีคุณพัวพันคดีฟอกเงิน ต้องโอนเงินมาตรวจสอบ").peakColor, "RED");
  assert.equal(c.add("โอเคครับ ขอบคุณครับ สวัสดีครับ").peakColor, "RED");
});

test("link checker matches LinkCheckerTest.kt", () => {
  const L = SAFE.links;
  assert.deepEqual(L.extract("กด https://bit.ly/abc123 เลยค่ะ"), ["https://bit.ly/abc123"]);
  assert.deepEqual(L.extract("ดูที่ www.kasikornbank.com ครับ"), ["www.kasikornbank.com"]);
  assert.deepEqual(L.extract("ไม่มีลิงก์ ค่าส่ง 3.50 บาท"), []);
  assert.equal(L.registrableDomain("shop.example.co.th"), "example.co.th");
  assert.equal(L.registrableDomain("a.b.example.com"), "example.com");
  for (const u of ["https://example.com/update.apk", "http://203.0.113.5/login", "https://xn--kasikrnbank-9za.com",
    "http://kbank-secure.com/login", "https://thaipost-delivery.xyz/pay", "https://gov-th-refund.com"]) {
    assert.equal(L.assess(u, false).level, "HIGH", u);
  }
  assert.equal(L.assess("https://bit.ly/abc", false).level, "CAUTION");
  assert.equal(L.assess("https://promo-gift.top", false).level, "CAUTION");
  assert.equal(L.assess("https://www.kasikornbank.com/th", false).level, "INFO");
  assert.equal(L.assess("https://shopee.co.th/cart", false).level, "INFO");
  assert.equal(L.check("กรมสรรพากรแจ้งคืนภาษี กดรับเงินที่ https://refund-tax.com")[0].level, "CAUTION");
  assert.equal(L.check("กรมสรรพากรแจ้ง ดูรายละเอียดที่ https://www.rd.go.th")[0].level, "INFO");
  assert.equal(L.check("การไฟฟ้าแจ้งดับไฟ ดูที่ https://www.pea.co.th")[0].level, "INFO");
  assert.equal(L.colorOf(L.check("โหลดแอป https://x.com/a.apk และ https://bit.ly/z")), "RED");
  assert.equal(L.colorOf([]), null);
});

test("number rules match NumberRiskTest.kt", () => {
  const N = SAFE.numbers;
  assert.equal(N.assess("+697123456789").level, "HIGH");
  assert.equal(N.assess("+698 81 234 5678").level, "CAUTION");
  assert.equal(N.assess("+66").level, "CAUTION");
  assert.equal(N.assess("+85512345678").level, "CAUTION");
  assert.equal(N.assess("00185512345678").level, "CAUTION");
  assert.equal(N.assess("").level, "CAUTION");
  assert.equal(N.assess("+660812345678").level, "CAUTION");
  assert.equal(N.assess("0812345678").level, "NONE");
  assert.equal(N.assess("02-123-4567").level, "NONE");
  assert.equal(N.assess("1441").level, "NONE");
  assert.equal(N.normalize("081-234-5678"), "+66812345678");
  assert.equal(N.normalize("001 855 12 345 678"), "+85512345678");
});

test("checklist matches ScamChecklistTest.kt", () => {
  const C = SAFE.checklist;
  assert.equal(C.evaluate({ ASKS_MONEY: true }).color, "RED");
  assert.equal(C.evaluate({ ASKS_SECRET: true }).color, "RED");
  assert.equal(C.evaluate({ ASKS_LINK_APP: true }).color, "RED");
  assert.equal(C.evaluate({ PRESSURE: true }).color, "YELLOW");
  assert.equal(C.evaluate({ CLAIMS_AUTHORITY: true, ASKS_MONEY: false }).color, "YELLOW");
  const allNo = Object.fromEntries(C.questions.map((q) => [q.id, false]));
  assert.equal(C.evaluate(allNo).color, "GREEN");
});

test("redaction matches PiiRedactorTest.kt", () => {
  assert.equal(SAFE.redact("โทรกลับ 081-234-5678 นะ"), "โทรกลับ [เบอร์โทร] นะ");
  assert.equal(SAFE.redact("เบอร์ +66 81 234 5678"), "เบอร์ [เบอร์โทร]");
  assert.equal(SAFE.redact("บัตร 1-2345-67890-12-3"), "บัตร [เลขบัตร]");
  assert.equal(SAFE.redact("บัญชี 123-4-56789-0"), "บัญชี [ตัวเลข]");
  assert.equal(SAFE.redact("รหัส ๔๘๒๙๑๓"), "รหัส [ตัวเลข]");
  assert.equal(SAFE.redact("กด https://bit.ly/abc123 เลย"), "กด [ลิงก์] เลย");
  assert.equal(SAFE.redact("ส่งมา somchai@example.com"), "ส่งมา [อีเมล]");
  assert.equal(SAFE.redact("ค่าปรับ 350 บาท ภายใน 24 ชม."), "ค่าปรับ 350 บาท ภายใน 24 ชม.");
});

test("explain finds suspicious words when Intl.Segmenter exists", () => {
  if (typeof Intl.Segmenter !== "function") return;
  const words = clf.explain("โทรจากธนาคาร บัญชีของคุณถูกอายัด ต้องโอนเงินด่วน");
  assert.ok(words.length > 0);
});
