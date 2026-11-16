# -*- coding: utf-8 -*-
"""
safe_text.py — ส่วนกลางที่ train_model.py / predict.py / export_model.py ใช้ร่วมกัน

สำคัญ: ทุกอย่างในไฟล์นี้ต้อง "ตรงกับ" ฝั่งแอป Android
(android/app/src/main/java/.../ml/ScamClassifier.kt) ทุกตัวอักษร
ถ้าแก้ normalize_text / ngram / เกณฑ์สี ต้องแก้ทั้งสองฝั่ง แล้วรัน export_model.py ใหม่

ทำไมใช้ "ระดับตัวอักษร (char n-gram)" อย่างเดียว:
  - ภาษาไทยไม่มีช่องว่างระหว่างคำ ถ้าใช้ตัวตัดคำ (pythainlp newmm) ฝั่ง Android ต้องมีตัวตัดคำ
    ที่ให้ผล "เหมือนเป๊ะ" ซึ่งทำได้ยาก -> ผลในแอปจะเพี้ยนจากตอนเทรน
  - char n-gram คำนวณแบบเดียวกันได้ 100% ทั้ง Python และ Kotlin
  - ทนต่อการถอดเสียงสะกดผิด (OTP -> โอทีพี) ได้ดี
  - วัดด้วย Cross-Validation 5 รอบ: char 2-5 = 96.3% เทียบกับ word+char เดิม 96.8% (ต่างกันในระดับ noise)
"""
import re

from sklearn.feature_extraction.text import TfidfVectorizer

# เกณฑ์แบ่งสีจาก % โอกาสเป็นมิจฉาชีพ (ต้องตรงกับแอป)
RED_THRESHOLD = 70     # >= 70%  -> แดง   (เสี่ยงสูงมาก)
YELLOW_THRESHOLD = 40  # 40-69%  -> เหลือง (น่าสงสัย) ; < 40% -> เขียว (ปลอดภัย)

# ช่วงความยาวชิ้นตัวอักษรที่ใช้เป็นฟีเจอร์
NGRAM_RANGE = (2, 5)

_REPEAT_RE = re.compile(r"(.{2,15}?)\1{2,}")


def normalize_text(text):
    """ปรับข้อความให้เป็นมาตรฐานก่อนวิเคราะห์
    - พิมพ์เล็ก (OTP/otp เหมือนกัน)
    - ตัดไม้ยมก (ๆ) เป็นช่องว่าง
    - ยุบคำ/พยางค์ที่ซ้ำติดกัน 3 ครั้งขึ้นไป (คุณคุณคุณ -> คุณ)"""
    if not isinstance(text, str):
        return ""
    text = text.lower().replace("ๆ", " ")
    return _REPEAT_RE.sub(r"\1", text)


def make_vectorizer():
    """ตัวแปลง ข้อความ -> ตัวเลข (TF-IDF ของชิ้นตัวอักษร 2-5 ตัว ภายในแต่ละช่วงที่คั่นด้วยช่องว่าง)"""
    return TfidfVectorizer(
        preprocessor=normalize_text,
        analyzer="char_wb",
        ngram_range=NGRAM_RANGE,
        sublinear_tf=True,
    )


def color_of(scam_percent):
    if scam_percent >= RED_THRESHOLD:
        return "RED"
    if scam_percent >= YELLOW_THRESHOLD:
        return "YELLOW"
    return "GREEN"


def scam_percent(text, model, vectorizer):
    """คืน % โอกาสเป็นมิจฉาชีพของข้อความ"""
    proba = model.predict_proba(vectorizer.transform([text]))[0]
    classes = list(model.classes_)
    return float(proba[classes.index(1)] * 100)
