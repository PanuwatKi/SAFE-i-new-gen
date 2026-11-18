// ทดสอบบอต LINE โดยจำลองบริการของ Google Apps Script (ไม่ต้องมี LINE หรือ Google จริง)
// รัน:  node --test line-bot/test/bot.test.js
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const root = path.join(__dirname, "..", "..");

function makeBot({ withSheet = true, botId = "Ubot" } = {}) {
  const replies = [];
  const sheetRows = [];
  const cache = new Map();
  const props = {
    LINE_CHANNEL_ACCESS_TOKEN: "token",
    MODEL_FILE_ID: "model-id",
    BOT_USER_ID: botId,
    ...(withSheet ? { SHEET_ID: "sheet-id" } : {}),
  };
  const ctx = {
    console,
    PropertiesService: { getScriptProperties: () => ({ getProperty: (k) => props[k] ?? null }) },
    DriveApp: { getFileById: () => ({ getBlob: () => ({ getDataAsString: () => fs.readFileSync(path.join(root, "web", "scam_model.json"), "utf8") }) }) },
    CacheService: { getUserCache: () => ({ put: (k, v) => cache.set(k, v), get: (k) => cache.get(k) ?? null, remove: (k) => cache.delete(k) }) },
    UrlFetchApp: {
      fetch: (url, opt) => {
        replies.push({ url, auth: opt.headers.Authorization, body: JSON.parse(opt.payload) });
        return { getResponseCode: () => 200, getContentText: () => "{}" };
      },
    },
    ContentService: { createTextOutput: (t) => ({ text: t }) },
    SpreadsheetApp: { openById: () => ({ getSheets: () => [{ getLastRow: () => sheetRows.length, appendRow: (r) => sheetRows.push(r) }] }) },
    Utilities: { formatDate: () => "2026-11-18" },
    Intl,
  };
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(root, "web", "safe-core.js"), "utf8"), ctx, { filename: "Core.gs" });
  vm.runInContext(fs.readFileSync(path.join(root, "line-bot", "Code.gs"), "utf8"), ctx, { filename: "Code.gs" });
  const post = (events, destination = "Ubot") =>
    ctx.doPost({ postData: { contents: JSON.stringify({ destination, events }) } });
  const textEvent = (text) => ({ type: "message", replyToken: "r" + replies.length, source: { userId: "U1" }, message: { type: "text", text } });
  const postback = (data) => ({ type: "postback", replyToken: "p" + replies.length, source: { userId: "U1" }, postback: { data } });
  const lastText = () => replies[replies.length - 1].body.messages[0].text;
  return { post, textEvent, postback, replies, sheetRows, lastText };
}

test("scam message gets a red reply with link warning and consent buttons", () => {
  const b = makeBot();
  const out = b.post([b.textEvent("กรมสรรพากรแจ้ง คุณได้รับเงินคืนภาษี 8,500 บาท ยืนยันตัวตนที่ https://rd-refund.top/th")]);
  assert.equal(out.text, "OK");
  assert.equal(b.replies.length, 1);
  assert.equal(b.replies[0].url, "https://api.line.me/v2/bot/message/reply");
  assert.equal(b.replies[0].auth, "Bearer token");
  const msg = b.replies[0].body.messages[0];
  assert.match(msg.text, /เสี่ยงมิจฉาชีพสูงมาก/);
  assert.match(msg.text, /rd-refund\.top/);
  const labels = msg.quickReply.items.map((i) => i.action.label);
  assert.deepEqual(labels, ["ใช่ มิจฉาชีพ", "ไม่ใช่มิจฉาชีพ", "ไม่ต้องเก็บ", "ตอบ 5 ข้อ"]);
  for (const l of labels) assert.ok([...l].length <= 20, "LINE quick reply label must be <= 20 chars: " + l);
});

test("normal message is green and nothing is stored without consent", () => {
  const b = makeBot();
  b.post([b.textEvent("แม่จ๋า เย็นนี้หนูกลับบ้านช้าหน่อยนะ ไม่ต้องรอกินข้าว")]);
  assert.match(b.lastText(), /ไม่พบสัญญาณมิจฉาชีพ/);
  assert.equal(b.sheetRows.length, 0);
  b.post([b.postback("store:none")]);
  assert.equal(b.sheetRows.length, 0);
  assert.match(b.lastText(), /ไม่เก็บ/);
});

test("consent stores only the redacted text in dataset format", () => {
  const b = makeBot();
  b.post([b.textEvent("โทรกลับ 081-234-5678 ด่วน บัญชีคุณถูกอายัด ต้องโอนเงินไปที่ https://bit.ly/x")]);
  b.post([b.postback("store:1")]);
  assert.equal(b.sheetRows.length, 2); // header + 1 row
  // แถวที่ได้มาจาก vm context เป็น array คนละ realm -> คัดลอกก่อนเทียบ
  assert.deepEqual([...b.sheetRows[0]], ["text", "label", "category", "channel", "received_at"]);
  const [text, label, , channel] = b.sheetRows[1];
  assert.equal(label, 1);
  assert.equal(channel, "chat");
  assert.doesNotMatch(text, /081|bit\.ly/);
  assert.match(text, /\[เบอร์โทร\]/);
  assert.match(text, /\[ลิงก์\]/);
  assert.ok(!b.sheetRows[1].includes("U1"), "must not store LINE user id");
  // กดซ้ำไม่ได้ (ข้อความถูกลบจาก cache แล้ว)
  b.post([b.postback("store:0")]);
  assert.equal(b.sheetRows.length, 2);
});

test("without SHEET_ID nothing is stored even with consent", () => {
  const b = makeBot({ withSheet: false });
  b.post([b.textEvent("บัญชีคุณถูกอายัด ต้องโอนเงินไปบัญชีปลอดภัยด่วน")]);
  b.post([b.postback("store:1")]);
  assert.equal(b.sheetRows.length, 0);
  assert.match(b.lastText(), /ยังไม่ได้เปิดการเก็บข้อมูล/);
});

test("phone number check", () => {
  const b = makeBot();
  b.post([b.textEvent("+697 123 456 789")]);
  assert.match(b.lastText(), /เสี่ยงมาก/);
  b.post([b.textEvent("081-234-5678")]);
  assert.match(b.lastText(), /ไม่พบลักษณะเสี่ยง/);
});

test("5-question quiz flow ends in a verdict", () => {
  const b = makeBot();
  b.post([b.textEvent("ตอบ 5 ข้อ")]);
  assert.match(b.lastText(), /ข้อ 1\/5/);
  b.post([b.postback("quiz:yes")]); // อ้างหน่วยงาน
  b.post([b.postback("quiz:yes")]); // ให้โอนเงิน
  b.post([b.postback("quiz:no")]);
  b.post([b.postback("quiz:skip")]);
  b.post([b.postback("quiz:no")]);
  assert.match(b.lastText(), /เสี่ยงมิจฉาชีพสูงมาก/);
  assert.match(b.lastText(), /ขอให้โอนเงิน/);
});

test("requests not addressed to our bot are ignored", () => {
  const b = makeBot();
  b.post([b.textEvent("บัญชีคุณถูกอายัด ต้องโอนเงิน")], "Usomeone-else");
  assert.equal(b.replies.length, 0);
});

test("follow event sends help", () => {
  const b = makeBot();
  b.post([{ type: "follow", replyToken: "f1", source: { userId: "U1" } }]);
  assert.match(b.lastText(), /SAFE ช่วยตรวจมิจฉาชีพ/);
});
