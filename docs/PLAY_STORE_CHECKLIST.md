# เช็กลิสต์ก่อนขึ้น Google Play — SAFE

## 1. ต้องมีก่อนส่งแอป
- [ ] บัญชี Google Play Console (ค่าสมัครครั้งเดียว $25) — บัญชีส่วนตัวที่สร้างใหม่ต้องทดสอบแบบ closed test กับผู้ทดสอบ ≥ 12 คน ติดต่อกัน 14 วันก่อนเปิดสาธารณะ
- [ ] **URL นโยบายความเป็นส่วนตัวแบบสาธารณะ** — repo เป็น private จึงใช้ GitHub Pages ฟรีไม่ได้ ทางเลือก: Google Sites, หรือ repo public แยกที่มีแค่ไฟล์นโยบาย เนื้อหาใช้ [PRIVACY_POLICY.md](PRIVACY_POLICY.md)
- [ ] สร้างกุญแจเซ็นแอป (upload key) + เปิด **Play App Signing** — ห้ามขึ้น repo (ดู [SECURITY_POLICY.md](SECURITY_POLICY.md) ข้อ 6)
- [ ] ไอคอน 512×512, ภาพหน้าจอ ≥ 2 รูป, feature graphic 1024×500
- [ ] ทดสอบบนเครื่องจริงอย่างน้อย Samsung + OPPO/Xiaomi (ฟีเจอร์ตรวจไฟล์อัดเสียงสายขึ้นกับยี่ห้อ)
- [ ] ถ้าเปิดฐานข้อมูลชุมชน: ทำตาม [FIREBASE_SETUP.md](FIREBASE_SETUP.md) และ **Enforce App Check** ก่อนเปิดตัว
- [x] ~~เปลี่ยน `fallbackToDestructiveMigration` ของ Room เป็น Migration จริง~~ — ทำแล้ว (schema ถูกเก็บใน `android/app/schemas/` และ CI ตรวจทุกครั้ง)

## 2. คำตอบแบบฟอร์ม Data safety (ร่าง — ฉบับที่ 2 มีฐานข้อมูลชุมชน)

> ตามนิยามของ Google: "เก็บ (collect)" = ส่งข้อมูลออกจากเครื่อง · ข้อมูลที่ประมวลผลในเครื่องอย่างเดียวไม่นับ
> ฟีเจอร์ชุมชนเป็นแบบ "ไม่บังคับ" → ประกาศว่าเก็บ และติ๊ก **Optional** (ผู้ใช้เลือกได้)

| ประเภทข้อมูล (Play) | เก็บ? | แชร์? | Optional? | วัตถุประสงค์ | หมายเหตุ |
|---------------------|-------|-------|-----------|--------------|----------|
| App activity → **Other user-generated content** (รายงานเบอร์: hash + ประเภท + หมวด) | ใช่ | ไม่ | ใช่ | App functionality, Fraud prevention, security | ส่งเฉพาะตอนผู้ใช้กดรายงาน |
| Device or other IDs (Firebase anonymous UID) | ใช่ | ไม่ | ใช่ | App functionality, Fraud prevention | ไม่ผูกกับตัวตน |
| Audio, Messages, Contacts, Call logs, Location | ไม่ | ไม่ | — | — | ประมวลผลในเครื่องเท่านั้น |

- เข้ารหัสระหว่างส่ง: **ใช่** (HTTPS บังคับ)
- ผู้ใช้ขอลบได้: **ใช่** — ในแอป (ลบทั้งหมด / ปิดชุมชน) + อีเมลสำหรับข้อมูลบนคลาวด์
- ⚠️ จุดที่ต้องตัดสินใจ: เบอร์ที่ถูกรายงานเป็นข้อมูลของ "บุคคลที่สาม" ไม่ใช่ของผู้ใช้ ปรึกษาผู้รู้ว่าควรประกาศเป็น Personal info → Phone number ด้วยหรือไม่ (ประกาศเกินดีกว่าขาด)
- ⚠️ การถอดเสียงทำผ่าน `SpeechRecognizer` ของระบบ ซึ่งอาจส่งเสียงขึ้นเซิร์ฟเวอร์ของผู้ให้บริการ การส่งนี้ทำโดยบริการของระบบ ไม่ใช่โค้ดของ SAFE แต่เปิดเผยไว้ในนโยบายแล้ว ถ้าผู้ตรวจของ Play ทักท้วง ให้ประกาศ "Audio → Voice or sound recordings · ephemeral · ไม่แชร์"

## 3. การประกาศสิทธิ์ที่อ่อนไหว
| สิทธิ์ | ต้องกรอกแบบฟอร์มพิเศษไหม |
|--------|--------------------------|
| `RECORD_AUDIO` | ไม่ต้อง แต่ต้องมีนโยบายความเป็นส่วนตัว และอธิบายในหน้าแอป |
| `READ_MEDIA_AUDIO` | ไม่ต้อง |
| `ROLE_CALL_SCREENING` | ไม่ต้อง (ไม่ได้ใช้สิทธิ์ Call log / SMS ซึ่งต้องกรอกฟอร์ม) — การตัดสายทำเฉพาะเบอร์ที่ผู้ใช้ตั้งเอง |
| `INTERNET` | ไม่ต้อง (ใช้เฉพาะฐานข้อมูลชุมชนแบบไม่บังคับ) |
| Accessibility | **ไม่ใช้** — Google Play ห้ามใช้ Accessibility API เพื่ออัดเสียงสาย |

## 4. หน้าร้าน (Store listing) — ข้อความที่ต้องระวัง
- ห้ามเขียนว่า "อัดเสียงสายได้" หรือ "ฟังสายได้ทุกเครื่อง" — เขียนว่า "วิเคราะห์ไฟล์อัดเสียงสายที่โทรศัพท์ของคุณบันทึกไว้"
- ระบุว่าโหมดฟังผ่านลำโพงเป็น "ทดลอง" และอาจใช้ไม่ได้บางรุ่น
- ห้ามอ้างว่าเป็นหน่วยงานรัฐหรือได้รับรองจาก AOC/ตำรวจ ถ้ายังไม่ได้รับรองจริง
- หมวดหมู่แนะนำ: Tools หรือ Communication · กลุ่มเป้าหมาย: 18+
- กรอกแบบสอบถามการจัดเรท (Content rating) ตามจริง

## 5. ก่อนกดเผยแพร่ทุกเวอร์ชัน
- [ ] `./gradlew testDebugUnitTest` ผ่าน (รวม `ScamClassifierTest` ที่ยืนยันว่าโมเดลตรงกับ Python)
- [ ] ถ้าเทรนโมเดลใหม่: รัน `python ml/train_model.py` แล้ว commit ทั้ง `scam_model.json` และ `model_test_vectors.json`
- [ ] เพิ่ม `versionCode` ใน `android/app/build.gradle.kts`
- [ ] ถ้าเปลี่ยนโครงสร้างฐานข้อมูล: เพิ่ม `version` + `AutoMigration` ใน `Database.kt` และ commit ไฟล์ schema ใหม่ (CI จะ fail ถ้าลืม)
- [ ] ถ้าแก้นโยบายสาระสำคัญ: เพิ่ม `Prefs.CURRENT_CONSENT_VERSION` และอัปเดตทั้ง `docs/PRIVACY_POLICY.md` และ `assets/privacy_policy_th.txt`
- [ ] Google Play กำหนดให้แอปใหม่ต้อง target Android รุ่นล่าสุดภายในกรอบเวลา — ตรวจ `targetSdk` ทุกครั้งก่อนส่ง
