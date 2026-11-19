# -*- coding: utf-8 -*-
"""
ทดสอบ redact.py และ merge_contributions.py (ไม่ต้องติดตั้งอะไรเพิ่ม)
    cd ml && python test_pipeline.py
CI รันไฟล์นี้ทุกครั้งที่แก้โฟลเดอร์ ml/ (.github/workflows/ml.yml)
"""
import csv
import glob
import os
import shutil
import sys
import tempfile

import merge_contributions as M
from redact import redact

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass


def test_redact_matches_app_and_web():
    # ชุดเดียวกับ RulesTest.kt และ web/test/core.test.js
    cases = {
        "โทรกลับ 081-234-5678 นะ": "โทรกลับ [เบอร์โทร] นะ",
        "เบอร์ +66 81 234 5678": "เบอร์ [เบอร์โทร]",
        "บัตร 1-2345-67890-12-3": "บัตร [เลขบัตร]",
        "บัญชี 123-4-56789-0": "บัญชี [ตัวเลข]",
        "รหัส OTP 482913": "รหัส OTP [ตัวเลข]",
        "รหัส ๔๘๒๙๑๓": "รหัส [ตัวเลข]",
        "กด https://bit.ly/abc123 เลย": "กด [ลิงก์] เลย",
        "กด thai-post.xyz/track เลย": "กด [ลิงก์] เลย",
        "โอนผ่าน scb.co.th/abc ด่วน": "โอนผ่าน [ลิงก์] ด่วน",
        "อ่าน x.comic นะ": "อ่าน x.comic นะ",
        "ส่งมา somchai@example.com": "ส่งมา [อีเมล]",
        "ค่าปรับ 350 บาท ภายใน 24 ชม.": "ค่าปรับ 350 บาท ภายใน 24 ชม.",
        "กดhttps://bit.ly/x ด่วน": "กด[ลิงก์] ด่วน",
        "โทร 081 234 5678": "โทร [เบอร์โทร]",   # ช่องว่างแบบ NBSP ระหว่างตัวเลข
    }
    for raw, want in cases.items():
        assert redact(raw) == want, (raw, redact(raw), want)


def fake_predict(scores):
    return lambda texts: [scores.get(t, 50.0) for t in texts]


def test_check_rows_flags_and_drops():
    existing = {M.key("สวัสดีค่ะ ธนาคารโทรมายืนยันรายการ"): 0}
    rows = [
        {"text": "โอนเงินด่วน โทร 081-234-5678 นะ", "label": "1", "_source": "a.csv"},       # ลบเบอร์ -> keep
        {"text": "สวัสดีค่ะ  ธนาคารโทรมายืนยันรายการ", "label": "0", "_source": "a.csv"},     # ซ้ำข้อมูลเดิม -> ตัด
        {"text": "สวัสดีค่ะ ธนาคารโทรมายืนยันรายการ", "label": "1", "_source": "b.csv"},      # ป้ายขัดข้อมูลเดิม -> check
        {"text": "ฮัลโหล", "label": "1", "_source": "a.csv"},                                # สั้นเกิน -> ตัด
        {"text": "ข้อความทดสอบยาวพอสมควร", "label": "x", "_source": "a.csv"},               # ป้ายผิด -> ตัด
        {"text": "นายสมชาย ใจดี ยืมเงินหน่อย", "label": "1", "_source": "a.csv"},             # มีชื่อ -> check
        {"text": "แม่ เย็นนี้กินข้าวบ้านไหมจ๊ะ", "label": "1", "_source": "a.csv"},            # AI ไม่เห็นด้วย -> check
        {"text": "เพื่อนชวนไปกินหมูกระทะเย็นนี้", "label": "0", "_source": "a.csv"},
        {"text": "เพื่อนชวนไปกินหมูกระทะเย็นนี้", "label": "1", "_source": "b.csv"},        # ผู้ใช้ติดป้ายขัดกัน -> check ทั้งคู่
    ]
    scores = {
        "โอนเงินด่วน โทร [เบอร์โทร] นะ": 95.0,
        "แม่ เย็นนี้กินข้าวบ้านไหมจ๊ะ": 3.0,
        "นายสมชาย ใจดี ยืมเงินหน่อย": 60.0,
        "เพื่อนชวนไปกินหมูกระทะเย็นนี้": 5.0,
    }
    out, dropped = M.check_rows(rows, existing, fake_predict(scores))
    by_text = {}
    for r in out:
        by_text.setdefault(r["text"], []).append(r)
    assert dropped == {"ซ้ำกับข้อมูลเดิม": 1, "ข้อความสั้นเกินไป": 1, "ป้ายไม่ใช่ 0 หรือ 1": 1}, dropped
    first = by_text["โอนเงินด่วน โทร [เบอร์โทร] นะ"][0]
    assert first["decision"] == "keep" and "ลบข้อมูลส่วนตัวเพิ่ม" in first["reason"], first
    assert by_text["สวัสดีค่ะ ธนาคารโทรมายืนยันรายการ"][0]["decision"] == "check"
    assert "อาจมีชื่อคน" in by_text["นายสมชาย ใจดี ยืมเงินหน่อย"][0]["reason"]
    assert by_text["แม่ เย็นนี้กินข้าวบ้านไหมจ๊ะ"][0]["decision"] == "check"
    pair = by_text["เพื่อนชวนไปกินหมูกระทะเย็นนี้"]
    assert len(pair) == 2 and all(r["decision"] == "check" for r in pair), pair


def test_name_and_address_hints():
    assert M.NAME_RE.search("ติดต่อนางสาวสมหญิงด่วน")
    assert not M.NAME_RE.search("นายหน้าเงินกู้ อนุมัติไว")
    assert M.ADDRESS_RE.search("ส่งของไปบ้านเลขที่ ๑๒")


def write(path, rows, fields):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)


def test_apply_refuses_unreviewed_rows():
    base = tempfile.mkdtemp()
    try:
        p = M.paths(base)
        write(p["pending"], [{"decision": "check", "text": "ข้อความที่ยังไม่ได้ตรวจ", "label": "1"}], ["decision", "text", "label"])
        try:
            M.cmd_apply(p, tolerance=1.0, train=False)
        except SystemExit as e:
            assert "ตรวจให้ครบ" in str(e)
        else:
            raise AssertionError("ต้องไม่ยอมรวมถ้ายังมีแถว check")
        assert not os.path.exists(p["contributed"])
    finally:
        shutil.rmtree(base)


def test_quality_gate_blocks_poisoned_labels_and_accepts_clean_data():
    texts, labels = M.load_training_set(M.paths())
    scams = [t for t, y in zip(texts, labels) if y == 1]
    # แกล้งติดป้ายข้อความมิจฉาชีพทั้งหมดว่า "ปกติ" (เติมคำท้ายให้ไม่ซ้ำ) -> ต้องไม่ผ่าน
    _, _, failed = M.quality_gate(texts, labels, [t + " นะ" for t in scams], [0] * len(scams), tolerance=1.0)
    assert failed, "ข้อมูลที่ติดป้ายผิดทั้งชุดต้องไม่ผ่านด่าน"
    # ข้อมูลที่ป้ายถูก (สำเนาจากชุดเดิม เติมคำท้าย) -> ต้องผ่าน
    sample = list(zip(texts, labels))[::25]
    _, _, failed = M.quality_gate(texts, labels, [t + " ครับ" for t, _ in sample], [y for _, y in sample], tolerance=1.0)
    assert not failed, failed


if __name__ == "__main__":
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    for t in tests:
        t()
        print("✔", t.__name__)
    print(f"ผ่านทั้งหมด {len(tests)} ข้อ")
