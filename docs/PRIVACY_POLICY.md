# นโยบายความเป็นส่วนตัว — แอป SAFE
**Privacy Policy — SAFE (Scam Alert For Everyone)**

ฉบับที่ 2 · มีผลตั้งแต่ / Effective: 16 พฤศจิกายน 2569 (16 November 2026)
ผู้พัฒนา / Developer: PanuwatKi (ทีมพัฒนา SAFE)
ติดต่อ / Contact: in.klang2551@gmail.com

> ฉบับในแอปอยู่ที่ `android/app/src/main/assets/privacy_policy_th.txt` — ถ้าแก้ไฟล์นี้ ต้องแก้ไฟล์นั้นให้ตรงกัน
> และถ้าเป็นการเปลี่ยนสาระสำคัญ ให้เพิ่ม `Prefs.CURRENT_CONSENT_VERSION` เพื่อให้ผู้ใช้ยอมรับใหม่
> ฉบับที่ 2 เพิ่ม: การจดเบอร์ไม่รู้จัก 7 วัน, สมุดเบอร์, ฐานข้อมูลเบอร์ชุมชน (ไม่บังคับ)

---

## ภาษาไทย

### 1. SAFE คืออะไร
SAFE เป็นแอปช่วยเตือนภัยมิจฉาชีพทางโทรศัพท์ โดย (1) เตือนเมื่อมีสายเข้าจากเบอร์ที่มีลักษณะเสี่ยงหรือเบอร์ที่ถูกรายงานไว้ (2) วิเคราะห์ไฟล์อัดเสียงสายหลังวางสาย (3) ฟังผ่านลำโพงระหว่างคุย (ทดลอง) และ (4) ให้คุณบันทึกและรายงานเบอร์มิจฉาชีพ/สแปม

### 2. ข้อมูลที่แอปใช้ และวิธีใช้
| ข้อมูล | ใช้ทำอะไร | เก็บไหม |
|--------|-----------|---------|
| เสียงจากไมโครโฟน (เฉพาะตอนคุณกด "เริ่มฟัง") | แปลงเป็นข้อความเพื่อวิเคราะห์ | **ไม่เก็บเสียง** |
| ไฟล์อัดเสียงสายที่โทรศัพท์ของคุณอัดไว้ | แปลงเป็นข้อความเพื่อวิเคราะห์ | **ไม่คัดลอก ไม่แก้ไข ไม่ลบไฟล์ของคุณ** |
| ข้อความบทสนทนาที่ถอดได้ | วิเคราะห์ความเสี่ยง และให้คุณยืนยันผล | เก็บในเครื่อง หลังลบเบอร์โทร เลขบัญชี เลขบัตรประชาชน รหัส OTP ลิงก์ และอีเมลออกแล้ว |
| เบอร์ที่โทรเข้า (เฉพาะเบอร์ที่ไม่อยู่ในรายชื่อติดต่อ) | ตรวจความเสี่ยงตอนสายเข้า และให้คุณกดรายงานสายเมื่อกี้ได้ | เก็บในเครื่อง **7 วัน** แล้วลบอัตโนมัติ |
| สมุดเบอร์ (เบอร์ ป้าย ประเภท โน้ต ที่คุณบันทึกเอง) | เตือน/ตัดสายตามที่คุณตั้ง | เก็บในเครื่องจนกว่าคุณจะลบ |
| ข้อความที่คุณแชร์ วาง หรือพิมพ์มาให้ตรวจ (เช่น SMS, LINE) และลิงก์ในข้อความ | วิเคราะห์ความเสี่ยงในเครื่อง (SAFE ไม่เปิดลิงก์) | **ไม่เก็บ** เว้นแต่คุณกด "บันทึก" (ลบข้อมูลส่วนตัวออกก่อน) |
| คำตอบแบบถามตอบ 5 ข้อหลังวางสาย | ประเมินความเสี่ยง | **ไม่เก็บ** |

SAFE **ไม่** เข้าถึงรายชื่อติดต่อ ประวัติการโทร ข้อความ SMS ตำแหน่ง หรือบัญชีใดๆ ของคุณ และไม่มีการลงทะเบียนหรือเข้าสู่ระบบ

### 3. ข้อมูลออกจากเครื่องหรือไม่
- **AI ของ SAFE ทำงานในเครื่องเท่านั้น** ข้อความบทสนทนาไม่ถูกส่งออกจากเครื่องโดย SAFE
- **การแปลงเสียงเป็นข้อความ** ใช้บริการถอดเสียงที่มากับ Android (เช่น ของ Google) SAFE เลือกแบบถอดในเครื่องก่อนเสมอ แต่ถ้าเครื่องถอดภาษาไทยในเครื่องไม่ได้ บริการนั้นอาจส่งเสียงไปประมวลผลที่เซิร์ฟเวอร์ของผู้ให้บริการ ภายใต้นโยบายความเป็นส่วนตัวของผู้ให้บริการนั้น
- **ฐานข้อมูลเบอร์ชุมชน (ไม่บังคับ ปิดเป็นค่าเริ่มต้น)** — ถ้าคุณเปิดใช้:
  - SAFE จะดาวน์โหลดรายชื่อเบอร์ที่ถูกรายงานมาเก็บในเครื่องเป็นระยะ และตรวจสายเข้าในเครื่อง **เซิร์ฟเวอร์จึงไม่รู้ว่าใครโทรหาคุณ**
  - เมื่อคุณกด "รายงาน" SAFE จะส่ง: รหัสของเบอร์ (hash), ประเภท (มิจฉาชีพ/สแปม) และหมวดที่เลือกจากรายการ ไปยัง Google Firebase พร้อมรหัสผู้ใช้นิรนามที่ระบบสร้างขึ้น (ไม่ผูกกับชื่อ อีเมล หรือเบอร์ของคุณ)
  - ไม่มีการส่งโน้ต ข้อความบทสนทนา หรือเสียง
  - หมายเหตุ: รหัสของเบอร์ย้อนกลับเป็นเบอร์ได้หากตั้งใจ จึงถือเป็นข้อมูลแฝง (pseudonymous) ไม่ใช่นิรนามสมบูรณ์
  - ผู้ใช้คนอื่นจะเห็นเพียง "จำนวนรายงาน" ของเบอร์นั้น เมื่อมีผู้รายงานตั้งแต่ 3 คนขึ้นไป ไม่เห็นว่าใครรายงาน
- **การส่งออกข้อมูล** (ไฟล์ CSV บทสนทนา หรือไฟล์สมุดเบอร์) เกิดขึ้นเฉพาะเมื่อคุณกดส่งออกและเลือกที่บันทึกเอง
- SAFE ไม่ขาย ไม่ให้เช่า และไม่แบ่งปันข้อมูลของคุณกับบุคคลที่สามเพื่อการโฆษณา ไม่มีโฆษณา และไม่มีเครื่องมือติดตามพฤติกรรม

### 4. การช่วยพัฒนา AI (ไม่บังคับ)
คุณสามารถยืนยันว่าบทสนทนาเป็นมิจฉาชีพหรือไม่ และส่งออกเป็นไฟล์เพื่อส่งให้ทีมพัฒนาใช้ปรับปรุงโมเดล ก่อนยืนยัน กรุณาตรวจและลบชื่อคนหรือข้อมูลส่วนตัวที่ยังเหลืออยู่ ข้อมูลที่ส่งให้ทีมพัฒนาใช้เพื่อพัฒนาการคัดกรองมิจฉาชีพเท่านั้น และคุณขอให้ลบได้ทุกเมื่อ

### 5. ระยะเวลาเก็บ และการลบ
- เบอร์ไม่รู้จักที่โทรเข้า: ลบอัตโนมัติเมื่อครบ 7 วัน
- ผลวิเคราะห์ที่ยังไม่ได้ยืนยัน: ลบอัตโนมัติเมื่อครบ 30 วัน
- ผลที่ยืนยันแล้ว และสมุดเบอร์: เก็บจนกว่าคุณจะลบ
- ปิดฐานข้อมูลชุมชน: ลบรายชื่อที่ดาวน์โหลดไว้ในเครื่องทันที
- "ลบข้อมูลทั้งหมด" ในหน้าตั้งค่า หรือถอนการติดตั้งแอป จะลบทุกอย่างในเครื่อง
- รายงานที่ส่งขึ้นฐานข้อมูลชุมชนแล้ว ไม่ผูกกับตัวตนของคุณ จึงลบผ่านแอปไม่ได้ แต่ติดต่อทีมพัฒนาให้ลบได้
- ข้อมูลในเครื่องไม่ถูกสำรองขึ้นคลาวด์และไม่ย้ายไปเครื่องใหม่

### 6. เจ้าของเบอร์ที่ถูกรายงาน
ถ้าเบอร์ของคุณถูกรายงานผิด ติดต่อทางอีเมลด้านบน พร้อมหลักฐานว่าเป็นเจ้าของเบอร์ ทีมพัฒนาจะตรวจสอบและลบภายใน 7 วัน

### 7. สิทธิ์ของคุณ (ตาม พ.ร.บ.คุ้มครองข้อมูลส่วนบุคคล พ.ศ. 2562)
คุณมีสิทธิ์ขอเข้าถึง แก้ไข ลบ คัดค้าน หรือถอนความยินยอม ข้อมูลในเครื่องจัดการได้เองทั้งหมดในแอป สำหรับข้อมูลบนคลาวด์หรือที่ส่งให้ทีมพัฒนา ติดต่อทางอีเมล เราจะตอบภายใน 30 วัน และคุณมีสิทธิ์ร้องเรียนต่อสำนักงานคณะกรรมการคุ้มครองข้อมูลส่วนบุคคล (สคส.)

### 8. ข้อควรทราบ
- AI และรายงานจากชุมชนอาจผิดพลาดได้ ใช้เป็นเครื่องช่วยเตือน ไม่ใช่คำตัดสิน และไม่ใช่คำแนะนำทางกฎหมายหรือการเงิน
- SAFE ตัดสายเฉพาะเบอร์ที่คุณตั้ง "ตัดสายอัตโนมัติ" เองเท่านั้น
- การอัดเสียงสายเป็นฟีเจอร์ของโทรศัพท์คุณ ไม่ใช่ของ SAFE โปรดใช้ตามกฎหมาย
- ผู้เยาว์ควรใช้แอปภายใต้ความยินยอมของผู้ปกครอง

### 9. การเปลี่ยนแปลงนโยบาย
หากมีการเปลี่ยนแปลงสาระสำคัญ แอปจะแสดงนโยบายใหม่และขอความยินยอมจากคุณอีกครั้ง

### 10. บริการอื่นของ SAFE: เว็บไซต์ และบอต LINE
**เว็บไซต์ SAFE** — ข้อความและเบอร์ที่คุณใส่ ประมวลผลในเบราว์เซอร์ของคุณเท่านั้น ไม่ถูกส่งไปที่ใดและไม่ถูกบันทึก

**บอต LINE ของ SAFE**
- ข้อความที่คุณส่งให้บอต ถูกส่งผ่าน LINE ไปประมวลผลที่ Google Apps Script ของทีมพัฒนา แล้วตอบกลับ (LINE และ Google ประมวลผลตามนโยบายของตนเอง)
- ข้อความล่าสุดพักไว้ชั่วคราวไม่เกิน 10 นาที เพื่อรอว่าคุณจะกดยินยอมให้เก็บหรือไม่ แล้วหมดอายุเอง
- **เก็บเฉพาะเมื่อคุณกด "ใช่ มิจฉาชีพ" หรือ "ไม่ใช่มิจฉาชีพ"** โดยลบเบอร์โทร เลขบัญชี เลขบัตร รหัส ลิงก์ และอีเมลออกก่อน **ไม่เก็บ ID หรือชื่อ LINE ของคุณ** เก็บใน Google Sheet ของทีมพัฒนาเพื่อใช้พัฒนา AI และลบภายใน 90 วันหลังรวมเข้าชุดข้อมูล
- เพราะไม่เก็บ ID ของคุณ เราจึงระบุไม่ได้ว่าข้อความไหนเป็นของคุณ หากต้องการให้ลบ ให้แจ้งเนื้อหาข้อความทางอีเมลด้านบน

---

## English

### 1. What SAFE does
SAFE helps protect people from phone scams by (1) warning about risky or reported incoming numbers, (2) analysing the phone's own call recordings after a call ends, (3) listening through the speakerphone during a call (experimental), and (4) letting you save and report scam/spam numbers.

### 2. Data the app uses
| Data | Purpose | Stored? |
|------|---------|---------|
| Microphone audio (only after you tap "Start listening") | Converted to text for analysis | **Audio is never stored** |
| Call recordings made by your phone's dialer | Converted to text for analysis | **Never copied, modified or deleted** |
| Transcribed conversation text | Risk analysis; lets you confirm the result | On device only, after phone numbers, account numbers, national ID numbers, OTP codes, links and emails are removed |
| Incoming caller number (only numbers not in your contacts) | Ring-time check; lets you report the last call | On device for **7 days**, then deleted automatically |
| Number book (numbers, labels, categories, notes you save) | Warn/reject calls as you choose | On device until you delete it |
| Text you share, paste or type for checking (e.g. SMS, LINE) and links in it | On-device risk analysis (SAFE never opens the links) | **Not stored** unless you tap "Save" (personal data removed first) |
| Answers to the 5-question post-call checklist | Risk assessment | **Not stored** |

SAFE does **not** access your contacts, call log, SMS, location or any accounts. There is no sign-up or login.

### 3. Does data leave the device?
- **SAFE's AI runs entirely on the device.** Conversation text is never sent anywhere by SAFE.
- **Speech-to-text** uses Android's speech recognition service (for example Google's). SAFE always prefers on-device recognition; if your device cannot recognise Thai on-device, that service may process audio on its provider's servers under the provider's privacy policy.
- **Community number database (optional, off by default).** If you turn it on:
  - SAFE periodically downloads the list of reported numbers and checks incoming calls on the device, so **the server never learns who calls you**.
  - When you tap "Report", SAFE sends a hash of the number, the type (scam/spam) and a category chosen from a fixed list to Google Firebase, together with an automatically generated anonymous user ID (not linked to your name, email or phone number).
  - Notes, conversation text and audio are never sent.
  - Note: a phone-number hash can be reversed with effort, so this is pseudonymous rather than fully anonymous data.
  - Other users only see the number of reports for a number, once 3 or more people have reported it, never who reported it.
- **Exports** (conversation CSV or number-book file) only happen when you tap export and choose where to save.
- SAFE does not sell, rent or share your data for advertising, shows no ads and contains no analytics or tracking.

### 4. Helping improve the AI (optional)
You may confirm whether a conversation was a scam and export it to send to the developers. Please remove remaining names or personal details first. Contributed data is used only to improve scam detection, and you may ask for its deletion at any time.

### 5. Retention and deletion
- Unknown incoming numbers: deleted after 7 days.
- Unconfirmed results: deleted after 30 days.
- Confirmed results and the number book: kept until you delete them.
- Turning the community database off deletes the downloaded list immediately.
- "Delete all data" in Settings, or uninstalling, deletes everything on the device.
- Reports already sent to the community database are not linked to your identity, so they cannot be deleted from the app; contact us to remove them.
- On-device data is excluded from cloud backup and device-to-device transfer.

### 6. Owners of reported numbers
If your number was reported by mistake, email us with proof of ownership. We will review and remove it within 7 days.

### 7. Your rights (Thailand PDPA B.E. 2562)
You may request access, correction, deletion, objection or withdrawal of consent. On-device data can be managed in the app. For cloud data or data sent to the developers, contact us; we respond within 30 days. You may also complain to the Personal Data Protection Committee (PDPC) of Thailand.

### 8. Important notes
- The AI and community reports can be wrong. They are warning aids, not verdicts, and not legal or financial advice.
- SAFE only rejects calls from numbers you have set to "auto-reject".
- Call recording is a feature of your phone, not of SAFE. Please follow applicable laws.
- Minors should use the app with a parent's or guardian's consent.

### 9. Changes
If we make material changes, the app will show the new policy and ask for your consent again.

### 10. Other SAFE services: website and LINE bot
**SAFE website** — text and numbers you enter are processed in your browser only; nothing is sent anywhere or stored.

**SAFE LINE bot**
- Messages you send to the bot travel through LINE to the developers' Google Apps Script for analysis and a reply (LINE and Google process them under their own policies).
- Your latest message is held temporarily for up to 10 minutes while the bot waits for your consent choice, then expires.
- **It is stored only if you tap "Yes, scam" or "Not a scam"**, after phone numbers, account numbers, ID numbers, codes, links and emails are removed. **Your LINE ID and name are not stored.** Stored text goes to the developers' Google Sheet to improve the AI and is deleted within 90 days of being merged into the dataset.
- Because your ID is not stored, we cannot tell which text is yours; to request deletion, email us the message content.
