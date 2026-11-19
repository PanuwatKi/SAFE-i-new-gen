# -*- coding: utf-8 -*-
"""
train_model.py — เทรนโมเดลคัดกรองมิจฉาชีพ แล้วบันทึก + ส่งออกให้แอป Android

ขั้นตอน:
  1. โหลด CSV ทุกไฟล์ใน ml/data/ (รวม ml/data/contributed/ = ข้อมูลที่ส่งออกจากแอป ถ้ามี)
  2. วัดความแม่นยำด้วย Cross-Validation 5 รอบ
  3. รายงาน precision/recall บนชุดทดสอบ 20% ที่กันไว้
  4. เทรนตัวจริงบนข้อมูลทั้งหมด -> ml/model/scam_model.pkl + vectorizer.pkl
  5. ส่งออกเป็น JSON ให้แอป (เรียก export_model.py ต่อให้อัตโนมัติ)

วิธีใช้:
    cd ml
    pip install -r requirements.txt
    python train_model.py
"""
import glob
import os
import sys

import joblib
import pandas as pd
from sklearn.metrics import classification_report
from sklearn.model_selection import StratifiedKFold, cross_validate, train_test_split
from sklearn.pipeline import Pipeline

from safe_text import make_classifier, make_vectorizer

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(HERE, "data")
MODEL_DIR = os.path.join(HERE, "model")


def load_data():
    files = sorted(glob.glob(os.path.join(DATA_DIR, "*.csv")))
    files += sorted(glob.glob(os.path.join(DATA_DIR, "contributed", "*.csv")))
    if not files:
        sys.exit(f"❌ ไม่พบไฟล์ .csv ใน {DATA_DIR}")
    df = pd.concat([pd.read_csv(f, encoding="utf-8-sig") for f in files], ignore_index=True)
    df = df.dropna(subset=["text", "label"])
    df["text"] = df["text"].astype(str).str.strip()
    df["label"] = df["label"].astype(int)
    before = len(df)
    df = df.drop_duplicates(subset=["text"]).reset_index(drop=True)
    print(f"✅ โหลด {len(files)} ไฟล์: {len(df)} แถว (ตัดซ้ำ {before - len(df)}) "
          f"| มิจฉาชีพ {int((df.label == 1).sum())} / ปกติ {int((df.label == 0).sum())}")
    return df["text"], df["label"]


def main():
    X, y = load_data()

    print("\n🔬 Cross-Validation 5 รอบ")
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
    pipe = Pipeline([("vec", make_vectorizer()), ("clf", make_classifier())])
    s = cross_validate(pipe, X, y, cv=cv, scoring=["accuracy", "precision", "recall", "f1"])
    for k in ["accuracy", "precision", "recall", "f1"]:
        v = s[f"test_{k}"]
        print(f"  {k:10s}: {v.mean() * 100:.1f}% (±{v.std() * 100:.1f}%)")

    print("\n📊 ชุดทดสอบ 20% ที่กันไว้")
    X_tr, X_te, y_tr, y_te = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)
    pipe.fit(X_tr, y_tr)
    print(classification_report(y_te, pipe.predict(X_te), target_names=["ปกติ (0)", "มิจฉาชีพ (1)"]))

    print("⚡ เทรนตัวจริงบนข้อมูลทั้งหมด...")
    vectorizer = make_vectorizer()
    model = make_classifier().fit(vectorizer.fit_transform(X), y)

    os.makedirs(MODEL_DIR, exist_ok=True)
    joblib.dump(model, os.path.join(MODEL_DIR, "scam_model.pkl"))
    joblib.dump(vectorizer, os.path.join(MODEL_DIR, "vectorizer.pkl"))
    print(f"✅ บันทึก {MODEL_DIR}/scam_model.pkl + vectorizer.pkl")

    import export_model
    export_model.main()


if __name__ == "__main__":
    main()
