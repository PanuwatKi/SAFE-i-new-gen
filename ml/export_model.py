# -*- coding: utf-8 -*-
"""
export_model.py — แปลงโมเดล scikit-learn (.pkl) เป็น JSON ให้แอป Android ใช้คำนวณในเครื่อง (ออฟไลน์)

คณิตศาสตร์ที่ส่งออก (แอปต้องคำนวณแบบเดียวกันทุกขั้น):
  1. t   = normalize_text(text)
  2. ngram: ยุบช่องว่างซ้ำ -> แยกตามช่องว่าง -> แต่ละช่วง w เติม " " หน้า-หลัง
            -> ตัดชิ้นยาว n = 2..5 ตัวอักษร (แบบ sklearn analyzer="char_wb")
  3. x_i = (1 + ln(count_i)) * idf_i   เฉพาะชิ้นที่อยู่ใน vocab แล้วหารด้วยความยาว L2
  4. p   = sigmoid(sum(x_i * w_i) + bias)          (Multinomial Naive Bayes, 2 คลาส)
  5. %   = 100 / (1 + exp(a * p + b))               (sigmoid calibration)

และสร้างไฟล์ test vector ไว้ให้ unit test ฝั่ง Kotlin ตรวจว่าผลตรงกับ Python

วิธีใช้ (ปกติ train_model.py เรียกให้เองอัตโนมัติ):
    python export_model.py
"""
import glob
import json
import math
import os
import re
import shutil
import sys
from datetime import datetime, timezone

import joblib
import pandas as pd

import safe_text
from safe_text import normalize_text, scam_percent

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(HERE, "model")
ANDROID_APP = os.path.join(HERE, "..", "android", "app", "src")
ASSET_PATH = os.path.join(ANDROID_APP, "main", "assets", "scam_model.json")
# เว็บแอปและบอต LINE ใช้ไฟล์เดียวกัน (ทดสอบว่าตรงกันใน web/test/core.test.js)
WEB_MODEL_PATH = os.path.join(HERE, "..", "web", "scam_model.json")
TEST_VECTOR_PATH = os.path.join(ANDROID_APP, "test", "resources", "model_test_vectors.json")

# ประโยคเพิ่มเติมสำหรับทดสอบ (นอกเหนือจากข้อมูลเทรน) ครอบคลุมกรณีขอบ
EXTRA_TEST_TEXTS = [
    "", "สวัสดี", "คุณคุณคุณคุณ", "OTP otp Otp", "ๆๆๆ ไปไหนมา",
    "โทรจากธนาคารนะคะ บัญชีของคุณถูกอายัด กรุณาโอนเงินไปบัญชีปลอดภัย",
    "แม่จ๋า เย็นนี้กินข้าวบ้านไหม",
    "  หลาย   ช่องว่าง\tและ\nขึ้นบรรทัด  ",
    "Hello this is DHL, your parcel is held at customs",
    "😀 อิโมจิ 👍 ปนข้อความ",
]

_WS2 = re.compile(r"\s\s+")


def char_wb_ngrams(text, lo, hi):
    """จำลอง sklearn analyzer='char_wb' แบบตรงไปตรงมา (ใช้เป็นต้นแบบให้ Kotlin)"""
    out = []
    for w in _WS2.sub(" ", text).split():
        w = " " + w + " "
        w_len = len(w)
        for n in range(lo, hi + 1):
            offset = 0
            out.append(w[offset:offset + n])
            while offset + n < w_len:
                offset += 1
                out.append(w[offset:offset + n])
            if offset == 0:
                break
    return out


def reference_percent(text, m):
    """คำนวณ % จาก JSON ที่ส่งออก ด้วยวิธีเดียวกับแอป (ไว้ตรวจว่าตรงกับ sklearn)"""
    index = m["_index"]
    counts = {}
    for g in char_wb_ngrams(normalize_text(text), *m["ngram_range"]):
        i = index.get(g)
        if i is not None:
            counts[i] = counts.get(i, 0) + 1
    vals = {i: (1.0 + math.log(c)) * m["idf"][i] for i, c in counts.items()}
    norm = math.sqrt(sum(v * v for v in vals.values()))
    z = m["bias"]
    if norm > 0:
        z += sum(v / norm * m["weights"][i] for i, v in vals.items())
    p = 1.0 / (1.0 + math.exp(-z))
    a, b = m["calibration"]["a"], m["calibration"]["b"]
    return 100.0 / (1.0 + math.exp(a * p + b))


def main():
    model = joblib.load(os.path.join(MODEL_DIR, "scam_model.pkl"))
    vectorizer = joblib.load(os.path.join(MODEL_DIR, "vectorizer.pkl"))

    if len(model.calibrated_classifiers_) != 1:
        sys.exit("❌ ต้องเทรนด้วย CalibratedClassifierCV(ensemble=False) ถึงจะส่งออกได้")
    cc = model.calibrated_classifiers_[0]
    nb = cc.estimator
    cal = cc.calibrators[0]
    classes = list(nb.classes_)
    s, o = classes.index(1), classes.index(0)

    terms = [None] * len(vectorizer.vocabulary_)
    for term, idx in vectorizer.vocabulary_.items():
        terms[idx] = term
    weights = (nb.feature_log_prob_[s] - nb.feature_log_prob_[o]).tolist()
    bias = float(nb.class_log_prior_[s] - nb.class_log_prior_[o])

    exported = {
        "format": "safe-char-nb-v1",
        "created_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "ngram_range": list(vectorizer.ngram_range),
        "thresholds": {"red": safe_text.RED_THRESHOLD, "yellow": safe_text.YELLOW_THRESHOLD},
        "bias": bias,
        "calibration": {"a": float(cal.a_), "b": float(cal.b_)},
        "terms": terms,
        "idf": [round(v, 6) for v in vectorizer.idf_.tolist()],
        "weights": [round(v, 6) for v in weights],
    }

    # --- ตรวจความตรงกัน: สูตรที่ส่งออก vs sklearn จริง ---
    check = dict(exported, _index={t: i for i, t in enumerate(terms)})
    texts = []
    for f in sorted(glob.glob(os.path.join(HERE, "data", "*.csv"))):
        texts += pd.read_csv(f, encoding="utf-8-sig")["text"].dropna().astype(str).tolist()
    texts += EXTRA_TEST_TEXTS
    max_diff = 0.0
    for t in texts:
        max_diff = max(max_diff, abs(reference_percent(t, check) - scam_percent(t, model, vectorizer)))
    print(f"🔎 ตรวจ {len(texts)} ประโยค: ต่างจาก sklearn มากสุด {max_diff:.5f}%")
    if max_diff > 0.01:
        sys.exit("❌ ผลไม่ตรงกับ sklearn — ห้ามใช้ไฟล์นี้ในแอป")

    os.makedirs(os.path.dirname(ASSET_PATH), exist_ok=True)
    with open(ASSET_PATH, "w", encoding="utf-8") as fp:
        json.dump(exported, fp, ensure_ascii=False, separators=(",", ":"))
    shutil.copyfile(ASSET_PATH, WEB_MODEL_PATH)
    print(f"✅ ส่งออก {os.path.normpath(ASSET_PATH)} "
          f"+ web/scam_model.json ({len(terms)} ฟีเจอร์, {os.path.getsize(ASSET_PATH) / 1e6:.1f} MB)")

    # test vector: ตัวอย่างทุก ๆ 7 ประโยค + กรณีขอบทั้งหมด
    sample = texts[:-len(EXTRA_TEST_TEXTS)][::7] + EXTRA_TEST_TEXTS
    vectors = [{"text": t, "percent": round(reference_percent(t, check), 6)} for t in sample]
    os.makedirs(os.path.dirname(TEST_VECTOR_PATH), exist_ok=True)
    with open(TEST_VECTOR_PATH, "w", encoding="utf-8") as fp:
        json.dump(vectors, fp, ensure_ascii=False, indent=1)
    print(f"✅ test vectors {len(vectors)} ชุด -> {os.path.normpath(TEST_VECTOR_PATH)}")


if __name__ == "__main__":
    main()
