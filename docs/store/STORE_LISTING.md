# ข้อความหน้าร้าน Google Play — SAFE

> คัดลอกไปวางใน Play Console → Grow → Store presence → Main store listing
> ภาพทั้งหมดอยู่ในโฟลเดอร์นี้ เป็นภาพหน้าจอจริงจากแอป ไม่มีภาพที่สร้างด้วย AI
> กฎที่ต้องทำตาม: docs/PLAY_STORE_CHECKLIST.md ข้อ 4 (ห้ามอ้างว่าอัดเสียงสายได้ทุกเครื่อง · โหมดลำโพงเป็น "ทดลอง" · ห้ามอ้างการรับรองจากหน่วยงานรัฐ)

## ไฟล์ภาพ
| ช่องใน Play Console | ไฟล์ | ขนาด |
|---------------------|------|------|
| App icon | `icon_512.png` | 512×512 PNG 32-bit |
| Feature graphic | `feature_graphic_1024x500.png` | 1024×500 |
| Phone screenshots (2–8 รูป) | `screenshot_1.png` – `screenshot_5.png` | 1080×1920 |

ถ้าได้ภาพหน้าจอจากมือถือจริงแล้ว (docs/PHONE_TEST_FORM.md) ส่งให้ฝั่งพัฒนาทำภาพชุดนี้ใหม่ได้

## ข้อมูลพื้นฐาน
| ช่อง | ค่า |
|------|-----|
| ชื่อแอป (≤30 ตัวอักษร) | SAFE เตือนภัยมิจฉาชีพ |
| ภาษาหลัก | ไทย (th-TH) · เพิ่มภาษาอังกฤษ (en-US) |
| หมวดหมู่ | Tools |
| ราคา | ฟรี · ไม่มีโฆษณา · ไม่มีการซื้อในแอป |
| อีเมลติดต่อ | อีเมลเดียวกับในนโยบายความเป็นส่วนตัว |
| นโยบายความเป็นส่วนตัว | ลิงก์หน้า Google Sites ที่ทำจาก `privacy_policy.html` (docs/OWNER_TODO.md ข้อ 13) |

---

## ภาษาไทย

**คำอธิบายสั้น (≤80 ตัวอักษร)**
```
เตือนสายและ SMS มิจฉาชีพ ด้วย AI ที่ทำงานในเครื่อง ไฟ 3 สี อ่านง่าย
```

**คำอธิบายแบบเต็ม**
```
SAFE ช่วยเตือนคุณและคนในบ้าน โดยเฉพาะผู้สูงอายุ ไม่ให้ถูกแก๊งคอลเซ็นเตอร์และมิจฉาชีพออนไลน์หลอก ผลแสดงเป็นไฟ 3 สีที่เข้าใจได้ทันที: เขียว ปลอดภัย · เหลือง น่าสงสัย · แดง เสี่ยงมิจฉาชีพสูงมาก

สิ่งที่ SAFE ทำได้
• เตือนตั้งแต่สายดัง เมื่อเบอร์มีลักษณะเสี่ยง เช่น +697 หรือเป็นเบอร์ที่คุณบันทึกว่าเป็นมิจฉาชีพ
• สมุดเบอร์ จดเบอร์มิจฉาชีพหรือสแปม และเลือกให้ตัดสายเบอร์นั้นอัตโนมัติได้
• ตรวจข้อความ SMS / LINE แค่กด "แชร์" ข้อความแปลก ๆ มาที่ SAFE รู้ผลทันที พร้อมตรวจลิงก์ปลอม
• ตอบ 5 คำถามหลังวางสาย แตะ ใช่ / ไม่ใช่ แล้วได้คำแนะนำว่าควรทำอะไรต่อ
• วิเคราะห์ไฟล์อัดเสียงสายที่โทรศัพท์ของคุณบันทึกไว้ (ขึ้นกับรุ่นโทรศัพท์และบริการแปลงเสียงในเครื่อง)
• ฟังผ่านลำโพงระหว่างคุย (ทดลอง อาจใช้ไม่ได้ในบางรุ่น)
• ตั้งค่าทีละขั้น ลูกหลานช่วยตั้งให้พ่อแม่ได้ มีวิธีแยกตามยี่ห้อมือถือ
• รวมเบอร์แจ้งเหตุไว้ที่เดียว เช่น ศูนย์ AOC 1441 และ ธปท. 1213

ความเป็นส่วนตัว
• AI วิเคราะห์ข้อความในเครื่องของคุณ ไม่ส่งข้อความไปวิเคราะห์ที่เซิร์ฟเวอร์ของ SAFE
• ไม่เก็บไฟล์เสียง ก่อนบันทึกผล แอปลบเบอร์โทร เลขบัญชี เลขบัตร และลิงก์ออกให้อัตโนมัติ
• ผลที่คุณไม่ได้ยืนยันจะถูกลบเองใน 30 วัน และลบข้อมูลทั้งหมดได้ทุกเมื่อ
• ฐานข้อมูลรายงานเบอร์ชุมชนปิดไว้เป็นค่าเริ่มต้น เปิดเมื่อคุณต้องการเท่านั้น
• การแปลงเสียงเป็นข้อความใช้บริการของ Android ในเครื่อง ซึ่งบางรุ่นอาจประมวลผลบนเซิร์ฟเวอร์ของผู้ให้บริการ

ข้อควรทราบ
SAFE เป็นเครื่องมือช่วยเตือน ไม่ใช่หน่วยงานรัฐ และไม่ได้รับการรับรองจากหน่วยงานใด ผลจาก AI อาจผิดพลาดได้ ถ้าสงสัย ให้วางสายแล้วโทรกลับเบอร์ทางการเอง หรือโทรปรึกษาศูนย์ AOC 1441

หลักจำง่าย: ไม่เชื่อ ไม่รีบ ไม่โอน
```

---

## English

**Short description (≤80 characters)**
```
Scam call & SMS alerts with on-device AI. Simple 3-color warnings in Thai.
```

**Full description**
```
SAFE helps you and your family, especially older relatives, avoid phone and online scams common in Thailand. Results use three simple colors: green (safe), yellow (suspicious) and red (very likely a scam).

What SAFE does
• Warns while the phone is still ringing when a number looks risky, such as +697, or when you have saved it as a scam number
• Number book: save scam or spam numbers and choose to reject their calls automatically
• Checks SMS and LINE messages: tap "Share" on a strange message to get a result at once, including fake-link checks
• 5 questions after a call: tap yes or no and get advice on what to do next
• Analyzes call recordings that your phone already saves (depends on the phone model and its speech service)
• Speakerphone listening during a call (experimental; may not work on some phones)
• Step-by-step setup that family members can do for older users, with brand-specific guides
• Official hotlines in one place, such as the AOC 1441 anti-online-scam center and the Bank of Thailand 1213

Privacy
• The AI analyzes text on your device; messages are not sent to SAFE servers for analysis
• No audio is stored; phone numbers, account numbers, ID numbers and links are removed before saving
• Unconfirmed results are deleted after 30 days, and you can delete everything at any time
• The community number-report database is off by default and used only if you turn it on
• Speech-to-text uses your phone's Android speech service, which on some phones may process audio on the provider's servers

Please note
SAFE is a warning tool. It is not a government agency and is not endorsed by any agency. AI results can be wrong. If in doubt, hang up and call the official number yourself, or call AOC 1441.
```
