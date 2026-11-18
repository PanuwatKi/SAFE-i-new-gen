# ตั้งค่าบอต LINE ของ SAFE (Google Apps Script)

บอตนี้ให้ผู้ใช้**ส่งต่อข้อความแปลก ๆ ใน LINE มาให้ SAFE ตรวจ** โดยไม่ต้องติดตั้งแอป
ฟรี ไม่ต้องผูกบัตร ใช้บัญชี Gmail ที่มี · ใช้เวลาประมาณ 30–45 นาที

สิ่งที่บอตทำ:
- ข้อความ SMS / LINE ที่ส่งมา → ตอบผล 3 สี + ตรวจลิงก์ (AI ตัวเดียวกับแอปและเว็บ)
- เบอร์โทร เช่น `+697 123 456 789` → ตรวจรูปแบบเบอร์
- พิมพ์ `ตอบ 5 ข้อ` → ถามตอบหลังวางสาย
- **ไม่เก็บข้อความ** เว้นแต่ผู้ใช้กดปุ่ม "ใช่ มิจฉาชีพ" / "ไม่ใช่มิจฉาชีพ" (ถือเป็นการยินยอม) — เก็บแบบลบข้อมูลส่วนตัวแล้ว และไม่เก็บ ID ผู้ใช้

ไฟล์ที่ใช้: [`line-bot/Code.gs`](../line-bot/Code.gs), [`line-bot/appsscript.json`](../line-bot/appsscript.json), [`web/safe-core.js`](../web/safe-core.js), [`web/scam_model.json`](../web/scam_model.json)

---

## 1. สร้าง LINE Official Account
1. เข้า https://manager.line.biz → **สร้างบัญชี LINE Official Account** (ชื่อเช่น "SAFE เตือนภัยมิจฉาชีพ")
2. ในบัญชีที่สร้าง ไปที่ **ตั้งค่า (Settings) → Messaging API → เปิดใช้งาน (Enable)** เลือก/สร้าง Provider
3. ไปที่ **ตั้งค่าการตอบกลับ (Response settings)**: ปิด "ข้อความตอบกลับอัตโนมัติ" และ "ข้อความทักทาย" เปิด **Webhook**
4. เข้า https://developers.line.biz/console → เลือก Provider → Channel ของบอต
   - แท็บ **Basic settings**: จด **Your user ID** (ขึ้นต้นด้วย `U...`) → ใช้เป็น `BOT_USER_ID`
   - แท็บ **Messaging API**: ส่วน **Channel access token (long-lived)** → กด **Issue** → จดไว้ → ใช้เป็น `LINE_CHANNEL_ACCESS_TOKEN`
   - ⚠️ token นี้เหมือนรหัสผ่าน ห้ามโพสต์หรือใส่ในโค้ด

## 2. อัปโหลดไฟล์โมเดลขึ้น Google Drive
1. อัปโหลด `web/scam_model.json` ขึ้น Google Drive (ไม่ต้องแชร์ให้ใคร)
2. คลิกขวา → **แชร์ → คัดลอกลิงก์** ลิงก์จะเป็น `https://drive.google.com/file/d/XXXXXXXX/view` → **XXXXXXXX คือ `MODEL_FILE_ID`**
3. ทุกครั้งที่เทรนโมเดลใหม่: คลิกขวาไฟล์เดิม → **จัดการเวอร์ชัน → อัปโหลดเวอร์ชันใหม่** (ID เดิมไม่เปลี่ยน)

## 3. (ไม่บังคับ) สร้าง Google Sheet เก็บข้อมูลที่ผู้ใช้ยินยอม
1. สร้าง Google Sheet ใหม่ ชื่อเช่น "SAFE ข้อมูลจากผู้ใช้" (ไม่ต้องแชร์ให้ใคร)
2. ลิงก์จะเป็น `https://docs.google.com/spreadsheets/d/YYYYYYYY/edit` → **YYYYYYYY คือ `SHEET_ID`**
3. ถ้าไม่ใส่ `SHEET_ID` บอตจะไม่เก็บอะไรเลย แม้ผู้ใช้กดยินยอม

## 4. สร้างโปรเจกต์ Apps Script
1. เข้า https://script.google.com → **โปรเจกต์ใหม่** ตั้งชื่อ "SAFE LINE bot"
2. ไฟล์ `Code.gs` ที่มีอยู่: ลบของเดิมทั้งหมด → วางเนื้อหาจาก `line-bot/Code.gs`
3. กด **+ → สคริปต์** ตั้งชื่อ `Core` → วางเนื้อหาจาก `web/safe-core.js` **ทั้งไฟล์**
4. ⚙️ **Project Settings** → ติ๊ก **Show "appsscript.json" manifest file** → กลับไปหน้า Editor เปิด `appsscript.json` → วางเนื้อหาจาก `line-bot/appsscript.json`
5. ⚙️ **Project Settings → Script properties → Add script property** ใส่ให้ครบ:

   | Property | ค่า |
   |----------|-----|
   | `LINE_CHANNEL_ACCESS_TOKEN` | จากข้อ 1.4 |
   | `BOT_USER_ID` | จากข้อ 1.4 |
   | `MODEL_FILE_ID` | จากข้อ 2 |
   | `SHEET_ID` | จากข้อ 3 (ไม่บังคับ) |

## 5. ทดสอบในหน้า Apps Script (ยังไม่ต้องมี LINE)
1. เลือกฟังก์ชัน `testLocally` ด้านบน → กด **Run**
2. ครั้งแรก Google จะขอสิทธิ์ (อ่าน Drive, ใช้ Sheets, เชื่อมต่อภายนอก) → กดอนุญาต
   (ถ้าขึ้น "Google hasn't verified this app" → Advanced → Go to SAFE LINE bot — เป็นสคริปต์ของคุณเอง)
3. ดู **Execution log** ควรเห็นประมาณนี้:
   ```
   100% RED | links: ["CAUTION"] | กรมสรรพากรแจ้ง ...
   0% GREEN | links: [] | แม่จ๋า ...
   ```

## 6. เผยแพร่เป็น Web app แล้วเชื่อมกับ LINE
1. กด **Deploy → New deployment** → รูปเฟือง → **Web app**
   - Execute as: **Me**
   - Who has access: **Anyone**
2. กด Deploy → คัดลอก **Web app URL** (ลงท้ายด้วย `/exec`)
3. กลับไปที่ LINE Developers → แท็บ **Messaging API** → **Webhook URL** → วาง URL → Update → เปิด **Use webhook**
4. เพิ่มบอตเป็นเพื่อนด้วย QR code ในแท็บเดียวกัน → ส่งข้อความทดสอบ เช่น
   `กรมสรรพากรแจ้ง คุณได้รับเงินคืนภาษี กดยืนยันที่ https://rd-refund.top/th`
   ควรได้คำตอบ 🔴 พร้อมปุ่ม "ใช่ มิจฉาชีพ / ไม่ใช่มิจฉาชีพ / ไม่ต้องเก็บ / ตอบ 5 ข้อ"

> ปุ่ม **Verify** ใน LINE Developers อาจขึ้น error เพราะ Apps Script ตอบกลับผ่านการ redirect
> ถ้าส่งข้อความจริงแล้วบอตตอบได้ ถือว่าใช้งานได้

**แก้โค้ดแล้วต้อง Deploy ใหม่:** Deploy → **Manage deployments** → ✏️ → Version: **New version** → Deploy (URL เดิมไม่เปลี่ยน)

---

## ข้อจำกัดที่ควรรู้
- **ตรวจลายเซ็นของ LINE ไม่ได้:** Apps Script อ่าน header `X-Line-Signature` ไม่ได้ บอตจึงกรองคำขอด้วย `BOT_USER_ID` แทน
  คนที่รู้ทั้ง URL ของบอตและ ID ของบอต (ทั้งคู่ไม่ได้เปิดเผยที่ไหน) สามารถส่งคำขอปลอมได้:
  - ส่งข้อความหาผู้ใช้ไม่ได้ (ไม่มี reply token จริง)
  - **แต่ใส่แถวปลอมลง Sheet ได้** (ส่งข้อความปลอม + กดยินยอมปลอม) — ข้อมูลใน Sheet จึง**ต้องตรวจด้วยตาก่อนนำไปเทรนทุกครั้ง** (เป็นกฎอยู่แล้วใน SECURITY_POLICY ข้อ 6.2)
  - ถ้าคนใช้เยอะ หรือ URL หลุด: Deploy ใหม่เพื่อเปลี่ยน URL และควรย้ายไป Cloud Functions ที่ตรวจลายเซ็นได้
- **โควตาบัญชีฟรี:** Google จำกัดจำนวนการทำงานต่อวัน เหมาะกับผู้ใช้จำนวนไม่มากถึงปานกลาง
- **ความเร็ว:** บอตโหลดไฟล์โมเดล (1.4 MB) จาก Drive ทุกครั้งที่มีข้อความ ตอบภายในประมาณ 1–3 วินาที
- **ค่าบริการ LINE:** การตอบกลับข้อความที่ผู้ใช้ส่งมา (reply) ไม่นับโควตาข้อความของแพ็กเกจฟรี ตามเงื่อนไขที่ทราบ — ตรวจเงื่อนไขล่าสุดกับ LINE อีกครั้ง

## ดูแลข้อมูลใน Sheet (ถ้าเปิดเก็บ)
- ทุกเดือน: **ไฟล์ → ดาวน์โหลด → CSV** → ตรวจด้วยตาว่าไม่มีชื่อคน/ที่อยู่ → วางไว้ที่ `ml/data/contributed/` → `python ml/train_model.py`
- ลบแถวที่เก่ากว่า **90 วัน** หลังรวมเข้าชุดข้อมูลแล้ว (ดูคอลัมน์ `received_at`)
- คอลัมน์ตรงกับฟอร์แมต `Dataset*.csv` อยู่แล้ว (`text,label,category,channel`) ส่วน `received_at` ให้ลบออกก่อนเทรนได้
