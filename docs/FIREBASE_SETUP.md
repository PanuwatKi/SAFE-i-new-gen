# ตั้งค่าฐานข้อมูลเบอร์ชุมชน (Firebase) — SAFE

> **ไม่บังคับ** — ถ้าไม่ทำขั้นตอนนี้ แอปยัง build และใช้งานได้ครบ แค่ใช้สมุดเบอร์แบบในเครื่องอย่างเดียว
> (การ์ด "ฐานข้อมูลเบอร์ชุมชน" จะไม่แสดงในแอป) ไม่ต้องแก้โค้ดใด ๆ

ใช้แพ็กฟรี **Spark** ได้ ไม่ต้องผูกบัตร ใช้เวลาประมาณ 20–30 นาที

## 1. สร้างโปรเจกต์
1. ไปที่ https://console.firebase.google.com → **Add project** → ตั้งชื่อ เช่น `safe-community`
2. หน้าถาม Google Analytics → **ปิด** (SAFE ไม่ใช้ analytics ตามนโยบาย)

## 2. เพิ่มแอป Android
1. Project Overview → ไอคอน Android → Package name: `com.panuwatki.safe`
2. **ไม่ต้อง** ดาวน์โหลด `google-services.json` (SAFE อ่านค่าจากไฟล์ properties แทน เพื่อให้ build ได้แม้ไม่มี Firebase)
3. ไปที่ ⚙️ **Project settings → General** แล้วจดค่า 3 ตัว:
   - **Project ID**
   - **App ID** (ในส่วน Your apps ขึ้นต้นด้วย `1:...:android:...`)
   - **Web API Key**
4. สร้างไฟล์ `android/firebase.properties` (คัดลอกจาก `android/firebase.properties.example`) แล้วใส่ค่า:
   ```
   projectId=safe-community
   appId=1:1234567890:android:abcdef123456
   apiKey=AIza...
   ```
   ไฟล์นี้อยู่ใน `.gitignore` แล้ว จะไม่ขึ้น GitHub

## 3. เปิดบัญชีนิรนาม (Anonymous Auth)
Build → **Authentication** → Get started → Sign-in method → **Anonymous** → Enable

## 4. สร้าง Firestore + ใส่กฎความปลอดภัย
1. Build → **Firestore Database** → Create database → Location: `asia-southeast1` (สิงคโปร์ ใกล้ไทยที่สุด) → Start in **production mode**
2. แท็บ **Rules** → ลบของเดิมทั้งหมด → วางเนื้อหาจาก [`firebase/firestore.rules`](../firebase/firestore.rules) → **Publish**
3. (แนะนำ) ทดสอบกฎด้วย **Rules Playground** ในหน้าเดียวกัน ก่อนเปิดให้คนใช้จริง

## 5. เปิด App Check (กันสคริปต์ปั่นรายงาน)
1. Build → **App Check** → Apps → เลือกแอป Android → **Play Integrity** → ใส่ SHA-256 ของกุญแจเซ็นแอป
   (ดูได้ใน Play Console → Setup → App signing หรือ `./gradlew signingReport` สำหรับ debug)
2. ตอนพัฒนา (รุ่น debug): รันแอป เปิดชุมชน แล้วหาใน **Logcat** คำว่า `DebugAppCheckProvider` จะมี debug token
   → App Check → เมนู ⋮ ของแอป → **Manage debug tokens** → เพิ่ม token นั้น
3. เมื่อทดสอบผ่านแล้ว: App Check → APIs → **Cloud Firestore** → **Enforce**
   ⚠️ ถ้ายังไม่ enforce สคริปต์ภายนอกยังเขียนรายงานได้ (แต่ยังติดกฎข้อ 4)

## 6. ทดสอบ
1. Build แอปใหม่ (Android Studio จะอ่าน `firebase.properties`)
2. แอป → ตั้งค่า → **ฐานข้อมูลเบอร์ชุมชน** → เปิด → กด "ซิงก์ตอนนี้" ควรขึ้น "ซิงก์แล้ว"
3. สมุดเบอร์ → เพิ่มเบอร์ทดสอบ → เลือก "มิจฉาชีพ" + เปิด "รายงานไปยังฐานข้อมูลชุมชนด้วย" → บันทึก
4. ใน Firestore Console ควรเห็น `numbers/<hash>` ที่ `scam: 1`
5. ลองรายงานเบอร์เดิมซ้ำ → ต้องขึ้น "คุณเคยรายงานเบอร์นี้แล้ว"

## ถ้าตั้งค่าไม่สำเร็จ
ลบไฟล์ `android/firebase.properties` แล้ว build ใหม่ — แอปจะกลับเป็นสมุดเบอร์แบบในเครื่องอย่างเดียวทันที

## งานดูแลประจำ (ผู้ดูแลระบบ)
ดู [SECURITY_POLICY.md](SECURITY_POLICY.md) ข้อ 5: รับคำขอถอดเบอร์ภายใน 7 วัน, ลบข้อมูลเก่าทุก 3 เดือน, เปิด 2-Step Verification ให้บัญชี Google ที่เป็นเจ้าของโปรเจกต์
