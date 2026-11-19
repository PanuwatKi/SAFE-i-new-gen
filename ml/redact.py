# -*- coding: utf-8 -*-
"""
redact.py — ลบข้อมูลส่วนบุคคลออกจากข้อความ (ตรงกับ PiiRedactor.kt และ SAFE.redact ใน web/safe-core.js)

ใช้ใน merge_contributions.py เพื่อลบซ้ำอีกรอบก่อนรวมข้อมูลจากผู้ใช้เข้าชุดเทรน
(ข้อมูลจากแอป/บอตผ่านการลบมาแล้ว แต่ไฟล์อาจถูกแก้ด้วยมือก่อนส่งมา)

ลบ: ลิงก์ · อีเมล · เลขบัตรประชาชน 13 หลัก · เบอร์โทร · ตัวเลขยาวตั้งแต่ 4 หลัก (เลขบัญชี OTP รหัส)
เก็บ: ตัวเลขสั้น เช่น "350 บาท" เพราะช่วยให้ AI เรียนรู้รูปแบบ
ลบไม่ได้: ชื่อคน และตัวเลขที่พูดเป็นคำ -> merge_contributions.py จึงทำเครื่องหมายให้คนตรวจ
"""
import re

LINK = "[ลิงก์]"
EMAIL = "[อีเมล]"
ID_CARD = "[เลขบัตร]"
PHONE = "[เบอร์โทร]"
NUMBER = "[ตัวเลข]"

# re.ASCII ให้ \b ตัดคำแบบเดียวกับ JavaScript (ตัวอักษรไทยไม่นับเป็นส่วนของคำ)
_EMAIL_RE = re.compile(r"[A-Za-z0-9_.+-]+@[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)+")
_URL_RE = re.compile(
    r"\b(?:https?://|www\.)\S+|\b[a-z0-9-]+\.(?:com|net|org|co|th|ly|me|io|xyz|link|app|cc|info)(?:/\S*)?",
    re.IGNORECASE | re.ASCII,
)
# ตัวเลขที่อาจมี + นำหน้า และมีช่องว่าง/ขีด/จุดคั่นได้ (\s แบบ Unicode เหมือน JavaScript)
_DIGITS_RE = re.compile(r"\+?[0-9](?:[0-9\s\-.]*[0-9])?")
_THAI_DIGITS = str.maketrans("๐๑๒๓๔๕๖๗๘๙", "0123456789")


def _classify_number(m):
    raw = m.group(0)
    d = re.sub(r"[^0-9]", "", raw)
    if len(d) < 4:
        return raw
    if len(d) == 13 and not raw.startswith("+"):
        return ID_CARD
    if raw.startswith("+") or (d.startswith("0") and 9 <= len(d) <= 10) or (d.startswith("66") and 10 <= len(d) <= 11):
        return PHONE
    return NUMBER


def redact(text):
    t = str(text).translate(_THAI_DIGITS)
    t = _EMAIL_RE.sub(EMAIL, t)          # ก่อนลิงก์ ไม่งั้น "example.com" ในอีเมลจะถูกมองเป็นลิงก์
    t = _URL_RE.sub(LINK, t)
    return _DIGITS_RE.sub(_classify_number, t)
