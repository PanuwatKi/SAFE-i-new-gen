# ออกแอปรุ่นจริงขึ้น Google Play

> ทำหลังสมัคร Play Console แล้ว (docs/OWNER_TODO.md ข้อ 14) · ตั้งค่าในโปรเจกต์ไว้ให้แล้ว คุณทำแค่สร้างกุญแจครั้งเดียว แล้วเลือก build ทางใดทางหนึ่ง
> CI ตรวจทุกครั้งที่แก้โค้ดว่ารุ่น release build ผ่าน (ขั้น "Build release bundle" ใน Android CI)

## 1. สร้างกุญแจอัปโหลด (ครั้งเดียว · 10 นาที)
กุญแจนี้ใช้ยืนยันกับ Google ว่าไฟล์ที่อัปโหลดมาจากคุณ (Play App Signing จะเซ็นซ้ำด้วยกุญแจจริงของ Google อีกชั้น)

**วิธีง่ายที่สุด: Android Studio**
1. เปิดโฟลเดอร์ `android` ใน Android Studio → เมนู **Build → Generate Signed App Bundle or APK** → เลือก **Android App Bundle**
2. **Create new...** → เก็บไฟล์ไว้**นอกโฟลเดอร์โปรเจกต์** เช่น `Documents\SAFE-keys\safe-upload.jks`
3. ตั้งรหัสผ่านที่เดายาก · Alias: `upload` · Validity: 25 ปี · กรอกชื่อ/องค์กรตามจริง

**หรือใช้คำสั่ง** (เปิด PowerShell ในโฟลเดอร์ที่จะเก็บกุญแจ):
```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore safe-upload.jks -keyalg RSA -keysize 2048 -validity 9125 -alias upload
```

⚠️ **สำรองไฟล์ `.jks` และรหัสผ่านไว้ 2 ที่** (เช่น ตัวจัดการรหัสผ่าน + แฟลชไดรฟ์) · **ห้ามใส่ใน GitHub** (โปรเจกต์ตั้ง .gitignore กันไว้แล้ว) · ถ้าทำหาย ขอรีเซ็ตกุญแจอัปโหลดผ่าน Play Console ได้ เพราะใช้ Play App Signing

## 2. เพิ่มเลขเวอร์ชันทุกครั้งก่อนอัปโหลด
ในไฟล์ `android/app/build.gradle.kts`: `versionCode` +1 (Google ไม่รับเลขซ้ำ) และแก้ `versionName` เช่น `0.2.0` → `0.3.0`

## 3. สร้างไฟล์ .aab (เลือกทางเดียว)
**ทาง A · Android Studio:** Build → Generate Signed App Bundle → เลือกกุญแจจากข้อ 1 → **release** → ได้ `android/app/release/app-release.aab`

**ทาง B · ไฟล์ตั้งค่าในเครื่อง:** สร้างไฟล์ `android/keystore.properties` (ไม่ขึ้น GitHub) แล้วสั่ง build
```properties
storeFile=C:/Users/<ชื่อคุณ>/Documents/SAFE-keys/safe-upload.jks
storePassword=<รหัสผ่านไฟล์>
keyAlias=upload
keyPassword=<รหัสผ่านกุญแจ>
```
```powershell
cd android; .\gradlew.bat bundleRelease
```
ได้ไฟล์ที่ `android/app/build/outputs/bundle/release/app-release.aab`

**ทาง C · ให้ GitHub build ให้:** ตั้ง secrets 4 ตัวที่ GitHub → Settings → Secrets and variables → Actions
| Secret | ค่า |
|--------|-----|
| `SAFE_UPLOAD_KEYSTORE_BASE64` | ไฟล์ .jks แปลงเป็นข้อความ: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("safe-upload.jks")) \| Set-Clipboard` แล้ววาง |
| `SAFE_UPLOAD_STORE_PASSWORD` | รหัสผ่านไฟล์ |
| `SAFE_UPLOAD_KEY_ALIAS` | `upload` |
| `SAFE_UPLOAD_KEY_PASSWORD` | รหัสผ่านกุญแจ |

แล้วไปที่ Actions → **Release bundle** → Run workflow → ดาวน์โหลด artifact `safe-release-aab` (เก็บไว้ 7 วัน)

## 4. อัปโหลดขึ้น Play Console
1. สร้างแอปใหม่ ชื่อ SAFE · ภาษาไทย · ฟรี · เปิด **Play App Signing** (ค่าเริ่มต้นของแอปใหม่)
2. **Testing → Internal testing** อัปโหลด `.aab` ก่อน ลองติดตั้งผ่าน Play Store กับตัวเอง
3. **Closed testing** (Internal testing ไม่นับ) ใส่อีเมลผู้ทดสอบหรือ Google Group → ส่ง **ลิงก์ร่วมทดสอบ (opt-in link)** ให้ทุกคน → แต่ละคนกด "เป็นผู้ทดสอบ" แล้วติดตั้งจาก Play Store
   - ผู้ทดสอบที่เคยลงรุ่นจาก GitHub Releases **ต้องถอนแอปเดิมก่อน** เพราะรุ่นทดสอบเซ็นด้วยกุญแจคนละชุดกับรุ่นใน Play จึงติดตั้งทับกันไม่ได้
   - ต้องมีอย่างน้อย 12 คนที่อยู่ในการทดสอบต่อเนื่อง 14 วัน ณ วันที่กดขอเปิดจริง ([เงื่อนไขของ Google](https://support.google.com/googleplay/android-developer/answer/14151465))
4. กรอกหน้าร้าน + Data safety ตาม `docs/PLAY_STORE_CHECKLIST.md` และ `docs/store/` → Dashboard → **Apply for production** (ตอบคำถามเรื่องผลการทดสอบ) → Google มักตรวจภายใน 7 วัน

ถ้าเปิดใช้ฐานข้อมูลเบอร์ชุมชน: สร้าง `android/firebase.properties` (docs/FIREBASE_SETUP.md) **ก่อน** build รุ่นที่จะอัปโหลด และลงทะเบียนลายนิ้วมือกุญแจใน Firebase App Check
