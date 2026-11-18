/**
 * SAFE LINE bot — รันบน Google Apps Script (ฟรี ไม่ต้องผูกบัตร)
 *
 * ผู้ใช้ส่งต่อข้อความแปลก ๆ มาที่บอต -> บอตตอบผล 3 สี + ตรวจลิงก์
 * ส่งเบอร์มา -> ตรวจรูปแบบเบอร์ · พิมพ์ "ตอบ 5 ข้อ" -> ถามตอบหลังวางสาย
 *
 * ความเป็นส่วนตัว (docs/SECURITY_POLICY.md ข้อ 5.1):
 *   - ไม่เก็บข้อความ เว้นแต่ผู้ใช้กดปุ่ม "ใช่ มิจฉาชีพ" / "ไม่ใช่มิจฉาชีพ" ซึ่งถือเป็นการยินยอมให้เก็บ
 *   - ก่อนเก็บ ลบเบอร์ เลขบัญชี เลขบัตร OTP ลิงก์ อีเมล ด้วย SAFE.redact() และไม่เก็บ LINE user ID
 *   - ข้อความล่าสุดพักใน CacheService ได้ไม่เกิน 10 นาที (เพื่อรอผู้ใช้กดยินยอม) แล้วหมดอายุเอง
 *
 * ต้องมีไฟล์ Core.gs = เนื้อหาเดียวกับ web/safe-core.js (คัดลอกทั้งไฟล์)
 * ค่าตั้ง (Project Settings -> Script properties):
 *   LINE_CHANNEL_ACCESS_TOKEN  (จำเป็น)
 *   MODEL_FILE_ID              (จำเป็น) ID ของไฟล์ scam_model.json ใน Google Drive
 *   SHEET_ID                   (ไม่บังคับ) Google Sheet สำหรับเก็บข้อความที่ผู้ใช้ยินยอม
 *   BOT_USER_ID                (แนะนำ) ID ของบอต (Basic settings -> Your user ID) ใช้กรองคำขอที่ไม่ได้มาจาก LINE
 * วิธีตั้งค่าทั้งหมด: docs/LINE_BOT_SETUP.md
 */

var CACHE_SECONDS = 600;
var LABEL = { RED: "🔴 เสี่ยงมิจฉาชีพสูงมาก", YELLOW: "🟡 น่าสงสัย ระวังไว้", GREEN: "🟢 ไม่พบสัญญาณมิจฉาชีพ" };
var ADVICE = "ห้ามโอนเงิน ห้ามบอกรหัส OTP ห้ามกดลิงก์หรือติดตั้งแอปตามที่อีกฝ่ายบอก\nถ้าไม่แน่ใจ วางสายแล้วโทรกลับเบอร์ทางการเอง หรือโทร 1441 (ศูนย์ AOC 24 ชม.)";
var HELP = "SAFE ช่วยตรวจมิจฉาชีพ 🛡️\n\n" +
  "• ส่งต่อ (Forward) หรือวางข้อความ SMS / LINE ที่น่าสงสัยมาที่นี่ แล้วจะได้ผลทันที\n" +
  "• ส่งเบอร์ที่โทรมา เช่น +697 123 456 789 เพื่อตรวจรูปแบบเบอร์\n" +
  "• พิมพ์ \"ตอบ 5 ข้อ\" ถ้าเพิ่งคุยกับสายแปลก ๆ\n\n" +
  "SAFE ไม่เก็บข้อความของคุณ เว้นแต่คุณกดยินยอมเอง · AI อาจผิดพลาดได้ ใช้เป็นเครื่องช่วยเตือน";

// ---------------- จุดรับ webhook จาก LINE ----------------
function doPost(e) {
  try {
    var body = JSON.parse(e.postData.contents);
    var props = PropertiesService.getScriptProperties();
    var botId = props.getProperty("BOT_USER_ID");
    // Apps Script อ่าน header X-Line-Signature ไม่ได้ จึงกรองด้วย destination แทน (ดูข้อจำกัดในคู่มือ)
    if (botId && body.destination !== botId) return ok_();
    (body.events || []).forEach(function (ev) {
      try { handleEvent_(ev); } catch (err) { console.error("event failed: " + err); }
    });
  } catch (err) {
    console.error("bad request: " + err);
  }
  return ok_();
}

function ok_() { return ContentService.createTextOutput("OK"); }

function handleEvent_(ev) {
  var userId = ev.source && ev.source.userId;
  if (ev.type === "follow") return reply_(ev.replyToken, [text_(HELP, mainMenu_())]);
  if (ev.type === "postback") return onPostback_(ev, userId);
  if (ev.type !== "message") return;
  if (!ev.message || ev.message.type !== "text") return reply_(ev.replyToken, [text_("ตอนนี้ SAFE ตรวจได้เฉพาะข้อความตัวอักษรนะครับ ลองคัดลอกข้อความมาวาง หรือพิมพ์เล่าว่าเขาพูดอะไร", mainMenu_())]);

  var msg = ev.message.text.trim();
  if (/^(ตอบ ?5 ?ข้อ|เริ่ม|quiz)$/i.test(msg)) return startQuiz_(ev.replyToken, userId);
  if (/^(ช่วยเหลือ|วิธีใช้|help|\?)$/i.test(msg)) return reply_(ev.replyToken, [text_(HELP, mainMenu_())]);
  if (/^[+\d][\d\s\-().]{2,20}$/.test(msg)) return reply_(ev.replyToken, [numberReply_(msg)]);
  return analyze_(ev.replyToken, userId, msg);
}

// ---------------- ตรวจข้อความ ----------------
var classifier_ = null;
function classifier() {
  if (!classifier_) {
    var id = PropertiesService.getScriptProperties().getProperty("MODEL_FILE_ID");
    var json = DriveApp.getFileById(id).getBlob().getDataAsString("UTF-8");
    classifier_ = SAFE.createClassifier(JSON.parse(json));
  }
  return classifier_;
}

function analyze_(replyToken, userId, msg) {
  var links = SAFE.links.check(msg);
  var verdict = SAFE.hasEnoughText(msg) ? classifier().classify(msg) : null;
  var color = SAFE.maxColor(verdict && verdict.color, SAFE.links.colorOf(links));
  if (!color) {
    return reply_(replyToken, [text_("ข้อความสั้นเกินไป ลองส่งข้อความที่ได้รับมาทั้งหมด หรือพิมพ์เล่าว่าเขาพูดอะไรให้ยาวขึ้นหน่อย", mainMenu_())]);
  }
  var lines = [LABEL[color] + (verdict ? " (" + Math.round(verdict.percent) + "%)" : "")];
  links.forEach(function (f) {
    var icon = f.level === "HIGH" ? "🔴" : f.level === "CAUTION" ? "🟡" : "⚪";
    lines.push(icon + " ลิงก์ " + f.host + ": " + f.reason);
  });
  if (color !== "GREEN") lines.push("", ADVICE);
  lines.push("", "ช่วยให้ AI เก่งขึ้นไหม? ถ้ากด \"ใช่ มิจฉาชีพ\" หรือ \"ไม่ใช่มิจฉาชีพ\" แปลว่าคุณยินยอมให้ SAFE เก็บข้อความนี้ (ลบเบอร์ เลขบัญชี ลิงก์ ออกก่อน) เพื่อใช้พัฒนา AI");

  if (userId) CacheService.getUserCache().put("last:" + userId, msg.slice(0, 2000), CACHE_SECONDS);
  var items = [
    postback_("ใช่ มิจฉาชีพ", "store:1", "ใช่ เป็นมิจฉาชีพ (ยินยอมให้เก็บ)"),
    postback_("ไม่ใช่มิจฉาชีพ", "store:0", "ไม่ใช่มิจฉาชีพ (ยินยอมให้เก็บ)"),
    postback_("ไม่ต้องเก็บ", "store:none", "ไม่ต้องเก็บ"),
    message_("ตอบ 5 ข้อ"),
  ];
  return reply_(replyToken, [text_(lines.join("\n"), items)]);
}

function numberReply_(raw) {
  var r = SAFE.numbers.assess(raw);
  var head = r.level === "HIGH" ? "🔴 เบอร์นี้เสี่ยงมาก" : r.level === "CAUTION" ? "🟡 ระวังเบอร์นี้" : "⚪ ไม่พบลักษณะเสี่ยงจากรูปแบบเบอร์";
  var detail = r.reason || "แต่มิจฉาชีพปลอมเบอร์ได้ ให้ดูจากสิ่งที่เขาพูดด้วย เช่น ให้โอนเงิน ขอ OTP ให้ติดตั้งแอป";
  return text_(head + "\n" + detail, mainMenu_());
}

// ---------------- เก็บข้อมูลเมื่อผู้ใช้ยินยอม ----------------
function onPostback_(ev, userId) {
  var data = ev.postback && ev.postback.data || "";
  if (data.indexOf("quiz:") === 0) return answerQuiz_(ev.replyToken, userId, data.slice(5));
  if (data.indexOf("store:") !== 0) return;
  var cache = CacheService.getUserCache();
  var key = "last:" + userId;
  var msg = userId ? cache.get(key) : null;
  cache.remove(key);
  var choice = data.slice(6);
  if (choice === "none") return reply_(ev.replyToken, [text_("รับทราบครับ SAFE ไม่เก็บข้อความนี้", mainMenu_())]);
  if (!msg) return reply_(ev.replyToken, [text_("ข้อความหมดเวลาแล้ว (เก็บไว้รอแค่ 10 นาที) ส่งมาใหม่ได้เลยครับ", mainMenu_())]);
  var sheetId = PropertiesService.getScriptProperties().getProperty("SHEET_ID");
  if (!sheetId) return reply_(ev.replyToken, [text_("ขอบคุณครับ (ตอนนี้ยังไม่ได้เปิดการเก็บข้อมูล ข้อความนี้จึงไม่ถูกเก็บ)", mainMenu_())]);
  var sheet = SpreadsheetApp.openById(sheetId).getSheets()[0];
  if (sheet.getLastRow() === 0) sheet.appendRow(["text", "label", "category", "channel", "received_at"]);
  // ไม่เก็บ LINE user ID / ชื่อ / เวลาละเอียด — เก็บแค่วันที่ เพื่อใช้ลบข้อมูลตามรอบ
  sheet.appendRow([SAFE.redact(msg), choice === "1" ? 1 : 0, "", "chat", Utilities.formatDate(new Date(), "Asia/Bangkok", "yyyy-MM-dd")]);
  return reply_(ev.replyToken, [text_("ขอบคุณที่ช่วยให้ SAFE เก่งขึ้นครับ 🙏 (เก็บแบบลบข้อมูลส่วนตัวแล้ว)", mainMenu_())]);
}

// ---------------- ถามตอบ 5 ข้อ ----------------
function startQuiz_(replyToken, userId) {
  if (!userId) return;
  CacheService.getUserCache().put("quiz:" + userId, JSON.stringify({ i: 0, a: {} }), CACHE_SECONDS);
  return reply_(replyToken, [questionMsg_(0)]);
}

function questionMsg_(i) {
  var q = SAFE.checklist.questions[i];
  return text_("ข้อ " + (i + 1) + "/5: " + q.text + "\n(" + q.hint + ")", [
    postback_("ใช่", "quiz:yes", "ใช่"),
    postback_("ไม่ใช่", "quiz:no", "ไม่ใช่"),
    postback_("ไม่แน่ใจ", "quiz:skip", "ไม่แน่ใจ"),
  ]);
}

function answerQuiz_(replyToken, userId, ans) {
  var cache = CacheService.getUserCache();
  var raw = userId ? cache.get("quiz:" + userId) : null;
  if (!raw) return reply_(replyToken, [text_("หมดเวลาแล้ว พิมพ์ \"ตอบ 5 ข้อ\" เพื่อเริ่มใหม่ครับ", mainMenu_())]);
  var st = JSON.parse(raw);
  var q = SAFE.checklist.questions[st.i];
  if (ans === "yes") st.a[q.id] = true;
  if (ans === "no") st.a[q.id] = false;
  st.i++;
  if (st.i < SAFE.checklist.questions.length) {
    cache.put("quiz:" + userId, JSON.stringify(st), CACHE_SECONDS);
    return reply_(replyToken, [questionMsg_(st.i)]);
  }
  cache.remove("quiz:" + userId);
  var r = SAFE.checklist.evaluate(st.a);
  var head = { RED: "🔴 ", YELLOW: "🟡 ", GREEN: "🟢 " }[r.color] + r.title;
  var out = [head, ""].concat(r.reasons.map(function (x) { return "• " + x; }));
  if (r.color !== "GREEN") out.push("", ADVICE);
  return reply_(replyToken, [text_(out.join("\n"), mainMenu_())]);
}

// ---------------- LINE Messaging API ----------------
function mainMenu_() { return [message_("ตอบ 5 ข้อ"), message_("วิธีใช้")]; }
function message_(label) { return { type: "action", action: { type: "message", label: label, text: label } }; }
function postback_(label, data, displayText) {
  return { type: "action", action: { type: "postback", label: label, data: data, displayText: displayText } };
}
function text_(t, quickItems) {
  var m = { type: "text", text: t.slice(0, 4900) };
  if (quickItems && quickItems.length) m.quickReply = { items: quickItems };
  return m;
}
function reply_(replyToken, messages) {
  if (!replyToken) return;
  var token = PropertiesService.getScriptProperties().getProperty("LINE_CHANNEL_ACCESS_TOKEN");
  var res = UrlFetchApp.fetch("https://api.line.me/v2/bot/message/reply", {
    method: "post",
    contentType: "application/json",
    headers: { Authorization: "Bearer " + token },
    payload: JSON.stringify({ replyToken: replyToken, messages: messages }),
    muteHttpExceptions: true,
  });
  if (res.getResponseCode() !== 200) console.error("LINE reply failed: " + res.getResponseCode() + " " + res.getContentText());
}

/** ทดสอบในหน้า Apps Script (กด Run) โดยไม่ต้องมี LINE: ดูผลใน Execution log */
function testLocally() {
  var c = classifier();
  ["กรมสรรพากรแจ้ง คุณได้รับเงินคืนภาษี กดยืนยันที่ https://rd-refund.top/th", "แม่จ๋า เย็นนี้กินข้าวบ้านไหม"].forEach(function (t) {
    var v = c.classify(t);
    console.log(Math.round(v.percent) + "% " + v.color + " | links: " + JSON.stringify(SAFE.links.check(t).map(function (f) { return f.level; })) + " | " + t);
  });
  console.log("+697: " + JSON.stringify(SAFE.numbers.assess("+697123456789")));
  console.log("redact: " + SAFE.redact("โทรกลับ 081-234-5678 ที่ https://bit.ly/x"));
}
