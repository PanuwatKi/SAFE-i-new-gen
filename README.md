# 🛡️ SAFE — Scam Alert For Everyone

แอป Android ช่วยเตือนภัยมิจฉาชีพทางโทรศัพท์ ออกแบบให้ผู้สูงอายุที่มีโทรศัพท์เครื่องเดียวใช้ได้
AI คัดกรองทำงาน **ในเครื่อง 100%** แอปไม่มีสิทธิ์ใช้อินเทอร์เน็ต

> ต่อยอดจากต้นแบบที่ใช้แข่ง depa Regional Coding & AI Competition (Arduino UNO Q + Python)
> โค้ดต้นแบบเดิมดูได้ที่ git tag [`v1-competition`](../../tree/v1-competition)

---

## 🚦 แอปทำอะไร

| ชั้นป้องกัน | ทำงานตอนไหน | วิธีทำงาน | สถานะ |
|------------|-------------|-----------|-------|
| 1. เตือนเบอร์อันตราย | ตอนสายเข้า | `CallScreeningService` ตรวจเบอร์ที่ไม่อยู่ในรายชื่อ: +697/+698 = 🔴 · ต่างประเทศ/ซ่อนเบอร์ = 🟡 | พร้อมทดสอบ |
| 2. ตรวจหลังวางสาย | ทันทีหลังวางสาย | เมื่อแอปโทรศัพท์ของเครื่องอัดเสียงสายเสร็จ SAFE ถอดเสียง → วิเคราะห์ → เตือน | ⚠️ ต้องทดสอบบนเครื่องจริง |
| 3. ฟังผ่านลำโพง | ระหว่างคุย | เปิดลำโพง แล้ว SAFE ฟังจากไมค์และเปลี่ยนสีทันที | 🧪 ทดลอง (บางรุ่นระบบปิดไมค์ระหว่างสาย) |

ผลแสดงเป็น 3 สีเหมือนต้นแบบเดิม: 🔴 ≥ 70% · 🟡 40–69% · 🟢 < 40%
วิเคราะห์ **ทั้งบทสนทนาสะสม** และจำค่าสูงสุดไว้ (เคยแดงแล้วไม่กลับเป็นเขียวเอง)

**ช่วยเพิ่มข้อมูลให้ AI:** ทุกบทสนทนาที่วิเคราะห์แล้ว ผู้ใช้กดยืนยันได้ว่า "มิจฉาชีพ / ไม่ใช่" + เลือกประเภท + แก้คำที่ถอดผิด
แล้วส่งออกเป็น CSV ฟอร์แมตเดียวกับ `ml/data/Dataset*.csv` เพื่อเทรนโมเดลรุ่นถัดไป

### ทำไมไม่ "ฟังสายสด" ตรงๆ
ตั้งแต่ Android 10 แอปทั่วไปดึงเสียงในสายโทรศัพท์ไม่ได้ และ Google Play ห้ามใช้ Accessibility เพื่ออัดเสียงสาย
SAFE จึงใช้ 3 ชั้นข้างบนแทน ซึ่งผ่านนโยบาย Play ทั้งหมด

---

## 📁 โครงสร้าง

```
android/                 แอป Android (Kotlin + Jetpack Compose)
  app/src/main/java/com/panuwatki/safe/
    ml/        ScamClassifier (โมเดลในเครื่อง), Conversation (สะสมทั้งสาย), PiiRedactor (ลบข้อมูลส่วนตัว)
    speech/    LiveListener (ฟังสด), FileTranscriber + AudioDecoder (ถอดเสียงจากไฟล์)
    calls/     ScamCallScreeningService, NumberRisk, CallRecordings, RecordingWatcherWorker
    data/      Room database, CsvExporter, Prefs
    notify/    Alerts (การแจ้งเตือน)
    ui/        หน้าจอทั้งหมด (ตัวอักษรใหญ่ สำหรับผู้สูงอายุ)
  app/src/main/assets/scam_model.json   โมเดลที่ส่งออกจาก Python
ml/                      เทรน/ทดสอบโมเดลบนคอมพิวเตอร์
  data/Dataset*.csv      ข้อมูลเทรน (403 ประโยค, 14+ ประเภทมิจฉาชีพ)
  data/contributed/      ข้อมูลที่ส่งออกจากแอป (ไม่ขึ้น repo)
  safe_text.py           ฟังก์ชันกลาง (ต้องตรงกับ ScamClassifier.kt)
  train_model.py         เทรน + วัดผล + ส่งออกให้แอป
  export_model.py        แปลง .pkl → JSON + ตรวจว่าผลตรงกับ sklearn
  predict.py             ทดสอบโมเดลบน terminal
docs/
  SECURITY_POLICY.md     นโยบายความปลอดภัยและการจัดการข้อมูล (ระบุว่าบังคับใช้ที่ไหนในโค้ด)
  PRIVACY_POLICY.md      นโยบายความเป็นส่วนตัว ไทย/อังกฤษ (สำหรับ Play Store)
  PLAY_STORE_CHECKLIST.md เช็กลิสต์ก่อนขึ้น Play + ร่างคำตอบ Data safety
SECURITY.md              ช่องทางแจ้งช่องโหว่
```

---

## 🧠 โมเดล AI

`ข้อความ → normalize → char n-gram (2–5) → TF-IDF → Naive Bayes → sigmoid calibration → %`

- ใช้ฟีเจอร์ระดับตัวอักษรอย่างเดียว เพื่อให้ Kotlin คำนวณได้ **ตรงกับ Python ทุกตัวเลข** โดยไม่ต้องมีตัวตัดคำไทย
  และยังทนต่อการถอดเสียงสะกดผิด
- Cross-Validation 5 รอบ: **accuracy 96.3%** · precision 96.7% · recall 95.0% (เดิม word+char = 96.8%)
- `export_model.py` ตรวจทั้ง 413 ประโยคว่าสูตรที่ส่งออกต่างจาก sklearn ไม่เกิน 0.00017%
- `ScamClassifierTest` (Kotlin) ตรวจซ้ำอีกรอบกับ test vectors จาก Python

### เทรนใหม่ (เมื่อมีข้อมูลเพิ่ม)
```bash
cd ml
pip install -r requirements.txt
python train_model.py      # เทรน + ส่งออก scam_model.json ให้แอปอัตโนมัติ
python predict.py          # ลองพิมพ์บทสนทนาทดสอบ
```
ข้อมูลที่ส่งออกจากแอป: วางไว้ใน `ml/data/contributed/` → ตรวจด้วยตาว่าไม่มีข้อมูลส่วนตัว → รัน `train_model.py`

---

## 📱 Build แอป

1. ติดตั้ง [Android Studio](https://developer.android.com/studio) (มี JDK และ Android SDK มาในตัว)
2. **File → Open** แล้วเลือกโฟลเดอร์ `android/` รอ Gradle sync
3. ต่อมือถือ (เปิด USB debugging) แล้วกด ▶ Run
4. รัน unit test: คลิกขวาที่ `app/src/test` → **Run Tests** หรือ `./gradlew testDebugUnitTest`

ต้องการ: minSdk 26 (Android 8) · ฟีเจอร์ตรวจไฟล์เสียงต้อง Android 13+ · การเตือนตอนสายเข้าต้อง Android 10+

### สิ่งที่ต้องทดสอบบนเครื่องจริง
- [ ] ถอดเสียงไทยจากไฟล์ได้ไหม (`SpeechRecognizer` + `EXTRA_AUDIO_SOURCE` — ตัวถอดเสียงบางเครื่องไม่รองรับ)
- [ ] งานเบื้องหลังเจอไฟล์อัดเสียงสายของยี่ห้อนั้นไหม (ดูโฟลเดอร์ใน `CallRecordings.pathHints`)
- [ ] โหมดลำโพงได้ยินเสียงระหว่างสายไหม
- [ ] การแจ้งเตือนเบอร์ +697/+698 ขึ้นตอนสายเข้า

---

## 🔒 ความปลอดภัยและความเป็นส่วนตัว (สรุป)
- ไม่มีสิทธิ์ INTERNET · ไม่เก็บเสียง · ไม่เก็บเบอร์ · ไม่สำรองขึ้นคลาวด์
- ลบเบอร์ เลขบัญชี เลขบัตร OTP ลิงก์ อีเมล ก่อนบันทึกทุกครั้ง
- ผลที่ไม่ยืนยันลบเองใน 30 วัน · ปุ่มลบข้อมูลทั้งหมด
- รายละเอียด: [docs/SECURITY_POLICY.md](docs/SECURITY_POLICY.md) · [docs/PRIVACY_POLICY.md](docs/PRIVACY_POLICY.md)

## 🗺️ แผนถัดไป
- ฝังตัวถอดเสียงออฟไลน์ (whisper.cpp) แทนบริการถอดเสียงของระบบ → ออฟไลน์ 100% จริง และถอดไฟล์ได้ทุกเครื่อง
- เวอร์ชัน iOS (เตือนเบอร์ผ่าน CallKit + กรอง SMS) — iOS ไม่อนุญาตให้ฟังเสียงระหว่างสาย
- เพิ่มข้อมูลเทรนจากผู้ใช้จริง แล้ววัดผลซ้ำ
