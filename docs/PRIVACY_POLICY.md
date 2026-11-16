# นโยบายความเป็นส่วนตัว — แอป SAFE
**Privacy Policy — SAFE (Scam Alert For Everyone)**

มีผลตั้งแต่ / Effective: 16 พฤศจิกายน 2569 (16 November 2026)
ผู้พัฒนา / Developer: PanuwatKi (ทีมพัฒนา SAFE)
ติดต่อ / Contact: in.klang2551@gmail.com

> ฉบับในแอปอยู่ที่ `android/app/src/main/assets/privacy_policy_th.txt` — ถ้าแก้ไฟล์นี้ ต้องแก้ไฟล์นั้นให้ตรงกัน
> และถ้าเป็นการเปลี่ยนสาระสำคัญ ให้เพิ่ม `Prefs.CURRENT_CONSENT_VERSION` เพื่อให้ผู้ใช้ยอมรับใหม่

---

## ภาษาไทย

### 1. SAFE คืออะไร
SAFE เป็นแอปช่วยเตือนภัยมิจฉาชีพทางโทรศัพท์ โดย (1) เตือนเมื่อมีสายเข้าจากเบอร์ที่มีลักษณะเสี่ยง (2) วิเคราะห์ไฟล์อัดเสียงสายหลังวางสาย และ (3) ฟังผ่านลำโพงระหว่างคุย (ทดลอง) แล้วแสดงผลเป็นสีเขียว เหลือง แดง

### 2. ข้อมูลที่แอปใช้ และวิธีใช้
| ข้อมูล | ใช้ทำอะไร | เก็บไหม |
|--------|-----------|---------|
| เสียงจากไมโครโฟน (เฉพาะตอนคุณกด "เริ่มฟัง") | แปลงเป็นข้อความเพื่อวิเคราะห์ | **ไม่เก็บเสียง** |
| ไฟล์อัดเสียงสายที่โทรศัพท์ของคุณอัดไว้ | แปลงเป็นข้อความเพื่อวิเคราะห์ | **ไม่คัดลอก ไม่แก้ไข ไม่ลบไฟล์ของคุณ** |
| ข้อความบทสนทนาที่ถอดได้ | วิเคราะห์ความเสี่ยง และให้คุณยืนยันผล | เก็บในเครื่อง หลังลบเบอร์โทร เลขบัญชี เลขบัตรประชาชน รหัส OTP ลิงก์ และอีเมลออกแล้ว |
| เบอร์โทรของสายเข้า (เฉพาะเบอร์ที่ไม่อยู่ในรายชื่อ) | ตรวจว่าเป็นเบอร์ต่างประเทศ/เบอร์ปลอมหรือไม่ | **ไม่เก็บ** |

SAFE **ไม่** เข้าถึงรายชื่อติดต่อ ประวัติการโทร ข้อความ SMS ตำแหน่ง หรือบัญชีผู้ใช้ใดๆ และไม่มีการลงทะเบียนหรือเข้าสู่ระบบ

### 3. ข้อมูลออกจากเครื่องหรือไม่
- **AI ของ SAFE ทำงานในเครื่องเท่านั้น** และแอปไม่มีสิทธิ์ใช้อินเทอร์เน็ต SAFE จึงไม่ส่งข้อมูลของคุณไปที่ใด
- **การแปลงเสียงเป็นข้อความ** ใช้บริการถอดเสียงที่ติดตั้งมากับ Android (เช่น ของ Google) SAFE เลือกแบบถอดในเครื่องก่อนเสมอ แต่ถ้าเครื่องถอดภาษาไทยในเครื่องไม่ได้ บริการนั้นอาจส่งเสียงไปประมวลผลที่เซิร์ฟเวอร์ของผู้ให้บริการ ภายใต้นโยบายความเป็นส่วนตัวของผู้ให้บริการนั้น
- **การส่งออกข้อมูล** เกิดขึ้นเฉพาะเมื่อคุณกด "ส่งออก CSV" และเลือกที่บันทึกไฟล์เอง
- SAFE ไม่ขาย ไม่ให้เช่า และไม่แบ่งปันข้อมูลของคุณกับบุคคลที่สาม ไม่มีโฆษณา และไม่มีเครื่องมือติดตามพฤติกรรม

### 4. การช่วยพัฒนา AI (ไม่บังคับ)
คุณสามารถยืนยันว่าบทสนทนาเป็นมิจฉาชีพหรือไม่ และส่งออกเป็นไฟล์เพื่อส่งให้ทีมพัฒนาใช้ปรับปรุงโมเดล ก่อนยืนยัน กรุณาตรวจและลบชื่อคนหรือข้อมูลส่วนตัวที่ยังเหลืออยู่ ข้อมูลที่ส่งให้ทีมพัฒนาจะใช้เพื่อพัฒนาความสามารถในการคัดกรองมิจฉาชีพเท่านั้น และคุณขอให้ลบได้ทุกเมื่อทางอีเมลด้านบน

### 5. ระยะเวลาเก็บ และการลบ
- ผลวิเคราะห์ที่คุณยังไม่ได้ยืนยัน: ลบอัตโนมัติเมื่อครบ 30 วัน
- ผลที่คุณยืนยันแล้ว: เก็บจนกว่าคุณจะลบ
- ลบได้ทีละรายการ หรือ "ลบข้อมูลทั้งหมด" ในหน้าตั้งค่า หรือถอนการติดตั้งแอป
- ข้อมูลไม่ถูกสำรองขึ้นคลาวด์และไม่ย้ายไปเครื่องใหม่

### 6. สิทธิ์ของคุณ (ตาม พ.ร.บ.คุ้มครองข้อมูลส่วนบุคคล พ.ศ. 2562)
คุณมีสิทธิ์ขอเข้าถึง แก้ไข ลบ คัดค้าน หรือถอนความยินยอม ข้อมูลที่อยู่ในเครื่องคุณจัดการได้เองทั้งหมดในแอป สำหรับข้อมูลที่คุณเคยส่งให้ทีมพัฒนา ติดต่อทางอีเมลด้านบน เราจะตอบภายใน 30 วัน และคุณมีสิทธิ์ร้องเรียนต่อสำนักงานคณะกรรมการคุ้มครองข้อมูลส่วนบุคคล (สคส.)

### 7. ข้อควรทราบ
- AI อาจผิดพลาดได้ ใช้เป็นเครื่องช่วยเตือน ไม่ใช่คำตัดสิน และไม่ใช่คำแนะนำทางกฎหมายหรือการเงิน
- การอัดเสียงสายเป็นฟีเจอร์ของโทรศัพท์คุณ ไม่ใช่ของ SAFE โปรดใช้ตามกฎหมายและแจ้งคู่สนทนาตามความเหมาะสม
- ผู้เยาว์ควรใช้แอปภายใต้ความยินยอมของผู้ปกครอง

### 8. การเปลี่ยนแปลงนโยบาย
หากมีการเปลี่ยนแปลงสาระสำคัญ แอปจะแสดงนโยบายใหม่และขอความยินยอมจากคุณอีกครั้ง

---

## English

### 1. What SAFE does
SAFE helps protect people from phone scams by (1) warning about risky incoming numbers, (2) analysing the phone's own call recordings after a call ends, and (3) listening through the speakerphone during a call (experimental). Results are shown as green, yellow or red.

### 2. Data the app uses
| Data | Purpose | Stored? |
|------|---------|---------|
| Microphone audio (only after you tap "Start listening") | Converted to text for analysis | **Audio is never stored** |
| Call recordings made by your phone's dialer | Converted to text for analysis | **Never copied, modified or deleted** |
| Transcribed conversation text | Risk analysis; lets you confirm the result | Stored on the device only, after phone numbers, account numbers, national ID numbers, OTP codes, links and email addresses are removed |
| Incoming caller number (only numbers not in your contacts) | Checks for foreign/spoofed numbers | **Not stored** |

SAFE does **not** access contacts, call logs, SMS, location or any accounts. There is no sign-up or login.

### 3. Does data leave the device?
- **SAFE's AI runs entirely on the device**, and the app does not have the Internet permission, so SAFE itself cannot send your data anywhere.
- **Speech-to-text** uses the speech recognition service provided by Android (for example Google's). SAFE always prefers on-device recognition; if your device cannot recognise Thai on-device, that service may process audio on its provider's servers under the provider's own privacy policy.
- **Exporting** only happens when you tap "Export CSV" and choose where to save the file.
- SAFE does not sell, rent or share your data with third parties, shows no ads and contains no analytics or tracking.

### 4. Helping improve the AI (optional)
You may confirm whether a conversation was a scam and export it to send to the developers to improve the model. Please review the text and remove any remaining names or personal details first. Contributed data is used only to improve scam detection, and you may ask for it to be deleted at any time via the contact email.

### 5. Retention and deletion
- Unconfirmed results are deleted automatically after 30 days.
- Confirmed results are kept until you delete them.
- You can delete single items, use "Delete all data" in Settings, or uninstall the app.
- Data is excluded from cloud backup and device-to-device transfer.

### 6. Your rights (Thailand PDPA B.E. 2562)
You may request access, correction, deletion, objection or withdrawal of consent. Everything stored on your device can be managed in the app. For data you have sent to the developers, contact us at the email above; we respond within 30 days. You may also complain to the Personal Data Protection Committee (PDPC) of Thailand.

### 7. Important notes
- The AI can make mistakes. It is a warning aid, not a verdict, and not legal or financial advice.
- Call recording is a feature of your phone, not of SAFE. Please follow applicable laws and inform the other party where appropriate.
- Minors should use the app with a parent's or guardian's consent.

### 8. Changes
If we make material changes, the app will show the new policy and ask for your consent again.
