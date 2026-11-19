# -*- coding: utf-8 -*-
"""
merge_contributions.py — รวมข้อมูลจากผู้ใช้เข้าชุดเทรนอย่างปลอดภัย (ตาม docs/SECURITY_POLICY.md ข้อ 6.2)

ขั้นที่ 1 ตรวจ:   python merge_contributions.py
    อ่าน CSV ทุกไฟล์ใน ml/data/inbox/ (ไฟล์ที่ส่งออกจากแอป หรือ Google Sheet ของบอต LINE)
    -> ลบข้อมูลส่วนตัวซ้ำ · ตัดข้อความซ้ำ · ทำเครื่องหมายแถวที่คนต้องตรวจ
    -> เขียน ml/data/review/pending.csv  (เปิดด้วย Excel ได้)

    แก้ใน pending.csv: ช่อง decision ของแถวที่เป็น "check" ให้เปลี่ยนเป็น keep หรือ drop
    (แก้ข้อความ/ป้ายได้ถ้าจำเป็น) แล้วบันทึกเป็น CSV เหมือนเดิม

ขั้นที่ 2 รวม:   python merge_contributions.py --apply
    ด่านตรวจคุณภาพ: เทียบโมเดล "ก่อน" กับ "หลัง" เพิ่มข้อมูล บนข้อมูลเดิมชุดเดียวกัน (5-fold)
    ถ้าแย่ลงเกินเกณฑ์ (ค่าเริ่มต้น 1 จุด) -> ไม่รวม ไม่แก้ไฟล์ใด ๆ
    ถ้าผ่าน -> เขียน ml/data/contributed/<ชื่อไฟล์ต้นทาง>.csv · ย้ายต้นฉบับไป inbox/done/
            -> บันทึกลง review/merge_log.csv -> เทรนใหม่ (train_model.py)

ทุกโฟลเดอร์ข้างบนอยู่ใน .gitignore — ข้อมูลจากผู้ใช้ห้ามขึ้น repo
ตั้งชื่อไฟล์ใน inbox ให้บอกว่าได้จากใคร เมื่อไหร่ (เช่น line_2026-11-20.csv) เพื่อลบได้ถ้าเจ้าของขอ
"""
import argparse
import csv
import datetime as dt
import glob
import os
import re
import shutil
import subprocess
import sys

import numpy as np

from redact import redact
from safe_text import RED_THRESHOLD, YELLOW_THRESHOLD, normalize_text

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")

MIN_CHARS = 8            # สั้นกว่านี้ไม่มีข้อมูลพอ (ตรงกับ hasEnoughText ในแอป/เว็บ)
MAX_CHARS = 2000         # ยาวผิดปกติ -> ให้คนตรวจ
DISAGREE_LOW = 20        # ติดป้าย "มิจฉาชีพ" แต่ AI ให้ต่ำกว่านี้ -> ให้คนตรวจ
DISAGREE_HIGH = RED_THRESHOLD  # ติดป้าย "ปกติ" แต่ AI ให้สูงกว่านี้ -> ให้คนตรวจ
OLD_ORIGINAL_DAYS = 90   # นโยบาย: ลบไฟล์ต้นฉบับภายใน 90 วันหลังรวม

# ชื่อคน/ที่อยู่ — ลบอัตโนมัติไม่ได้ จึงให้คนตรวจ
NAME_RE = re.compile(r"(?:นางสาว|น\.ส\.|นาย(?!หน้า|ก)|นาง|ด\.ช\.|ด\.ญ\.)\s*[ก-๙]{2,}")
ADDRESS_RE = re.compile(r"บ้านเลขที่|หมู่ที่|ซอย\s*\S+|ตำบล|แขวง|อำเภอ")

FIELDS = ["decision", "text", "label", "category", "channel", "reason", "ai_percent", "source_file"]


def paths(base=DATA):
    return {
        "data": base,
        "inbox": os.path.join(base, "inbox"),
        "done": os.path.join(base, "inbox", "done"),
        "review": os.path.join(base, "review"),
        "pending": os.path.join(base, "review", "pending.csv"),
        "sources": os.path.join(base, "review", "pending_sources.txt"),
        "log": os.path.join(base, "review", "merge_log.csv"),
        "contributed": os.path.join(base, "contributed"),
    }


def key(text):
    """ตัวเทียบข้อความซ้ำ: มาตรฐานเดียวกับตอนเทรน + ยุบช่องว่าง"""
    return re.sub(r"\s+", " ", normalize_text(text)).strip()


def read_csv(path):
    # Excel บน Windows ภาษาไทยมักบันทึก CSV เป็น cp874 แทน UTF-8 -> อ่านได้ทั้งสองแบบ
    for enc in ("utf-8-sig", "cp874"):
        try:
            with open(path, encoding=enc, newline="") as f:
                return list(csv.DictReader(f))
        except UnicodeDecodeError:
            continue
    sys.exit(f"❌ อ่านไฟล์ไม่ได้ (ไม่ใช่ UTF-8 หรือ cp874): {path}")


def load_existing(p):
    """ข้อความที่อยู่ในชุดเทรนแล้ว: {key: label}"""
    files = sorted(glob.glob(os.path.join(p["data"], "*.csv"))) + sorted(glob.glob(os.path.join(p["contributed"], "*.csv")))
    out = {}
    for f in files:
        for r in read_csv(f):
            t = (r.get("text") or "").strip()
            if t and str(r.get("label", "")).strip() in ("0", "1"):
                out.setdefault(key(t), int(r["label"]))
    return out


def load_model():
    import joblib
    mdir = os.path.join(HERE, "model")
    mpath, vpath = os.path.join(mdir, "scam_model.pkl"), os.path.join(mdir, "vectorizer.pkl")
    if not (os.path.exists(mpath) and os.path.exists(vpath)):
        sys.exit("❌ ยังไม่มีโมเดล — รัน python train_model.py ก่อน")
    model, vec = joblib.load(mpath), joblib.load(vpath)
    col = list(model.classes_).index(1)
    return lambda texts: model.predict_proba(vec.transform(texts))[:, col] * 100


def check_rows(rows, existing, predict):
    """ขั้นที่ 1: คืน (แถวสำหรับ pending.csv, จำนวนที่ตัดทิ้งแยกตามเหตุผล)"""
    dropped = {}
    out, seen = [], {}

    def drop(why):
        dropped[why] = dropped.get(why, 0) + 1

    for r in rows:
        raw = re.sub(r"\s+", " ", (r.get("text") or "")).strip()
        label = str(r.get("label", "")).strip()
        if label not in ("0", "1"):
            drop("ป้ายไม่ใช่ 0 หรือ 1")
            continue
        text = redact(raw)
        k = key(text)
        if len(k.replace(" ", "")) < MIN_CHARS:
            drop("ข้อความสั้นเกินไป")
            continue
        reasons = []
        note = "ลบข้อมูลส่วนตัวเพิ่ม" if text != raw else ""   # แจ้งให้รู้ ไม่ต้องตรวจ
        if k in existing:
            if existing[k] == int(label):
                drop("ซ้ำกับข้อมูลเดิม")
                continue
            reasons.append("ป้ายขัดกับข้อมูลเดิม")
        if k in seen:
            first = seen[k]
            if first["label"] == label:
                drop("ซ้ำกันเองในไฟล์ที่ส่งมา")
                continue
            if "ผู้ใช้ติดป้ายขัดกัน" not in first["reason"]:
                first["reason"] = "; ".join(x for x in [first["reason"], "ผู้ใช้ติดป้ายขัดกัน"] if x)
                first["decision"] = "check"
            reasons.append("ผู้ใช้ติดป้ายขัดกัน")
        if len(text) > MAX_CHARS:
            reasons.append("ยาวผิดปกติ")
        if NAME_RE.search(text):
            reasons.append("อาจมีชื่อคน")
        if ADDRESS_RE.search(text):
            reasons.append("อาจมีที่อยู่")
        row = {
            "decision": "", "text": text, "label": label,
            "category": (r.get("category") or "").strip(),
            "channel": (r.get("channel") or "").strip(),
            "reason": "; ".join(x for x in reasons + [note] if x),
            "ai_percent": "", "source_file": r.get("_source", ""),
            "_needs_check": bool(reasons),
        }
        seen.setdefault(k, row)
        out.append(row)

    if out:
        probs = predict([r["text"] for r in out])
        for r, pct in zip(out, probs):
            r["ai_percent"] = f"{pct:.1f}"
            if r["label"] == "1" and pct < DISAGREE_LOW:
                r["reason"] = "; ".join(x for x in [r["reason"], f"AI ให้แค่ {pct:.0f}% แต่ติดป้ายว่ามิจฉาชีพ"] if x)
                r["_needs_check"] = True
            elif r["label"] == "0" and pct >= DISAGREE_HIGH:
                r["reason"] = "; ".join(x for x in [r["reason"], f"AI ให้ {pct:.0f}% แต่ติดป้ายว่าปกติ"] if x)
                r["_needs_check"] = True
            if r["decision"] != "check":
                r["decision"] = "check" if r["_needs_check"] else "keep"
    for r in out:
        r.pop("_needs_check", None)
    return out, dropped


def write_csv(path, rows, fields):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields, extrasaction="ignore")
        w.writeheader()
        w.writerows(rows)


def cmd_check(p):
    os.makedirs(p["inbox"], exist_ok=True)
    files = sorted(glob.glob(os.path.join(p["inbox"], "*.csv")))
    if not files:
        print(f"📭 ไม่มีไฟล์ใน {p['inbox']}\n   วาง CSV จากแอป (ส่งออกข้อมูล) หรือจาก Google Sheet ของบอต LINE ไว้ที่นั่นก่อน")
        return 0
    rows = []
    for f in files:
        got = read_csv(f)
        if got and ("text" not in got[0] or "label" not in got[0]):
            print(f"⚠️ ข้าม {os.path.basename(f)}: ไม่มีคอลัมน์ text และ label")
            continue
        for r in got:
            r["_source"] = os.path.basename(f)
        rows += got
    out, dropped = check_rows(rows, load_existing(p), load_model())
    write_csv(p["pending"], out, FIELDS)
    with open(p["sources"], "w", encoding="utf-8") as f:   # ไฟล์ที่อ่านรอบนี้ -> ย้ายไป done/ ตอน --apply
        f.write("\n".join(os.path.basename(x) for x in files))

    n_check = sum(r["decision"] == "check" for r in out)
    print(f"📥 อ่าน {len(files)} ไฟล์ {len(rows)} แถว")
    for why, n in dropped.items():
        print(f"   ตัดทิ้ง {n} แถว: {why}")
    print(f"✅ พร้อมรวม {len(out) - n_check} แถว · 🔍 ต้องให้คนตรวจ {n_check} แถว")
    print(f"📝 {p['pending']}")
    if n_check:
        print("   เปิดไฟล์ แก้ช่อง decision ของแถว check เป็น keep หรือ drop (ดูเหตุผลในช่อง reason)")
        print("   Excel: บันทึกเป็น \"CSV UTF-8\" เพื่อไม่ให้ภาษาไทยเพี้ยน")
    print("   ตรวจด้วยตาอีกรอบว่าไม่มีชื่อคน/ข้อมูลส่วนตัว แล้วรัน: python merge_contributions.py --apply")
    warn_old_originals(p)
    return 0


# ---------------- ด่านตรวจคุณภาพ ----------------

def scores(y, pct):
    y, pct = np.asarray(y), np.asarray(pct)
    return {
        "ความแม่นยำรวม": 100 * np.mean((pct >= 50) == y),
        "จับมิจฉาชีพได้": 100 * np.mean(pct[y == 1] >= 50),
        "ขึ้นเหลืองหรือแดง (มิจฉาชีพ)": 100 * np.mean(pct[y == 1] >= YELLOW_THRESHOLD),
        "ข้อความปกติขึ้นแดง": 100 * np.mean(pct[y == 0] >= RED_THRESHOLD),
    }


LOWER_IS_BETTER = {"ข้อความปกติขึ้นแดง"}


def quality_gate(old_texts, old_labels, new_texts, new_labels, tolerance, folds=5, seed=42):
    """เทียบโมเดลก่อน/หลังเพิ่มข้อมูล บนข้อมูลเดิมชุดเดียวกัน — ข้อมูลใหม่เข้าเฉพาะฝั่งเทรน"""
    from sklearn.model_selection import StratifiedKFold
    from sklearn.pipeline import Pipeline
    from safe_text import make_classifier, make_vectorizer

    X, y = np.asarray(old_texts, dtype=object), np.asarray(old_labels, dtype=int)
    nX, ny = np.asarray(new_texts, dtype=object), np.asarray(new_labels, dtype=int)
    base, cand = np.zeros(len(y)), np.zeros(len(y))
    for i, (tr, te) in enumerate(StratifiedKFold(folds, shuffle=True, random_state=seed).split(X, y), 1):
        print(f"   รอบที่ {i}/{folds}")
        fit = lambda a, b: Pipeline([("v", make_vectorizer()), ("c", make_classifier())]).fit(a, b)
        base[te] = fit(X[tr], y[tr]).predict_proba(X[te])[:, 1] * 100
        cand[te] = fit(np.concatenate([X[tr], nX]), np.concatenate([y[tr], ny])).predict_proba(X[te])[:, 1] * 100
    b, c = scores(y, base), scores(y, cand)
    failed = []
    for name in b:
        worse = (c[name] - b[name]) if name in LOWER_IS_BETTER else (b[name] - c[name])
        if worse > tolerance:
            failed.append(name)
    return b, c, failed


def load_training_set(p):
    """ข้อมูลที่ train_model.py ใช้ (ml/data/*.csv + contributed) ตัดข้อความซ้ำแบบเดียวกัน"""
    files = sorted(glob.glob(os.path.join(p["data"], "*.csv"))) + sorted(glob.glob(os.path.join(p["contributed"], "*.csv")))
    texts, labels, seen = [], [], set()
    for f in files:
        for r in read_csv(f):
            t = (r.get("text") or "").strip()
            lab = str(r.get("label", "")).strip()
            if t and lab in ("0", "1") and t not in seen:
                seen.add(t)
                texts.append(t)
                labels.append(int(lab))
    return texts, labels


def cmd_apply(p, tolerance, train=True):
    if not os.path.exists(p["pending"]):
        sys.exit("❌ ยังไม่มี pending.csv — รัน python merge_contributions.py ก่อน")
    rows = read_csv(p["pending"])
    todo = [r for r in rows if (r.get("decision") or "").strip().lower() not in ("keep", "drop")]
    if todo:
        sys.exit(f"❌ ยังมี {len(todo)} แถวที่ decision ไม่ใช่ keep หรือ drop — ตรวจให้ครบก่อน")
    keep = []
    for r in rows:
        if r["decision"].strip().lower() != "keep":
            continue
        label = str(r.get("label", "")).strip()
        if label not in ("0", "1"):
            sys.exit(f"❌ ป้ายไม่ถูกต้อง (ต้องเป็น 0 หรือ 1): {r['text'][:40]}")
        r["text"] = redact(re.sub(r"\s+", " ", r["text"]).strip())   # ลบซ้ำ เผื่อแก้ข้อความด้วยมือ
        keep.append(r)
    if not keep:
        print("ไม่มีแถวที่ keep — ไม่มีอะไรต้องรวม")
        return 0

    texts, labels = load_training_set(p)
    print(f"🧪 ด่านตรวจคุณภาพ: ข้อมูลเดิม {len(texts)} ประโยค + ใหม่ {len(keep)} ประโยค (ยอมให้แย่ลงไม่เกิน {tolerance} จุด)")
    b, c, failed = quality_gate(texts, labels, [r["text"] for r in keep], [int(r["label"]) for r in keep], tolerance)
    print(f"   {'ตัวชี้วัด (ทดสอบบนข้อมูลเดิม)':<34}{'ก่อน':>8}{'หลัง':>8}")
    for name in b:
        mark = " ❌" if name in failed else ""
        print(f"   {name:<34}{b[name]:>7.1f}%{c[name]:>7.1f}%{mark}")
    if failed:
        print("❌ ไม่ผ่าน — ไม่ได้รวมข้อมูล และไม่ได้แก้ไฟล์ใด ๆ")
        print("   สาเหตุที่พบบ่อย: ติดป้ายผิด หรือมีคนแกล้งส่งข้อมูลผิด → ตรวจแถวใน pending.csv อีกครั้ง")
        return 1

    # ผ่าน -> เขียนข้อมูลแยกตามไฟล์ต้นทาง (ลบตามคำขอเจ้าของได้ง่าย)
    os.makedirs(p["contributed"], exist_ok=True)
    os.makedirs(p["done"], exist_ok=True)
    now = dt.datetime.now().strftime("%Y-%m-%d %H:%M")
    log_rows = []
    by_source = {}
    for r in keep:
        by_source.setdefault(r.get("source_file") or "unknown.csv", []).append(r)
    for src, rs in by_source.items():
        stem = os.path.splitext(os.path.basename(src))[0]
        dest = os.path.join(p["contributed"], f"{stem}.csv")
        n = 2
        while os.path.exists(dest):
            dest = os.path.join(p["contributed"], f"{stem}_{n}.csv")
            n += 1
        write_csv(dest, rs, ["text", "label", "category", "channel"])
        log_rows.append({"merged_at": now, "source_file": src, "contributed_file": os.path.basename(dest), "rows": len(rs)})
        print(f"   ➕ {len(rs)} แถว -> contributed/{os.path.basename(dest)}")
    sources = open(p["sources"], encoding="utf-8").read().split("\n") if os.path.exists(p["sources"]) else []
    for name in sources:
        src = os.path.join(p["inbox"], name)
        if name and os.path.exists(src):
            shutil.move(src, os.path.join(p["done"], name))
    if os.path.exists(p["sources"]):
        os.remove(p["sources"])
    log_exists = os.path.exists(p["log"])
    with open(p["log"], "a", encoding="utf-8-sig" if not log_exists else "utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["merged_at", "source_file", "contributed_file", "rows"])
        if not log_exists:
            w.writeheader()
        w.writerows(log_rows)
    os.replace(p["pending"], os.path.join(p["review"], f"applied_{dt.datetime.now():%Y%m%d_%H%M%S}.csv"))
    print("✅ รวมแล้ว · ต้นฉบับย้ายไป inbox/done/ · บันทึกใน review/merge_log.csv")

    if train:
        print("⚡ เทรนใหม่...")
        subprocess.run([sys.executable, os.path.join(HERE, "train_model.py")], check=True, cwd=HERE)
        print("ต่อไป: รันชุดทดสอบ (Android + node) แล้ว commit เฉพาะ scam_model.json / test vectors — ห้าม commit ข้อมูลผู้ใช้")
    warn_old_originals(p)
    return 0


def warn_old_originals(p):
    cutoff = dt.datetime.now().timestamp() - OLD_ORIGINAL_DAYS * 86400
    old = [f for f in glob.glob(os.path.join(p["done"], "*.csv")) if os.path.getmtime(f) < cutoff]
    if old:
        print(f"🗑️ ต้นฉบับใน inbox/done/ เก่ากว่า {OLD_ORIGINAL_DAYS} วัน ควรลบตามนโยบาย: " + ", ".join(os.path.basename(f) for f in old))


def main(argv=None):
    ap = argparse.ArgumentParser(description="รวมข้อมูลจากผู้ใช้เข้าชุดเทรน (ตรวจ -> คนตรวจ -> ด่านคุณภาพ -> รวม)")
    ap.add_argument("--apply", action="store_true", help="ขั้นที่ 2: ผ่านด่านคุณภาพแล้วรวมเข้า contributed/")
    ap.add_argument("--tolerance", type=float, default=1.0, help="ยอมให้ตัวชี้วัดแย่ลงได้กี่จุด (ค่าเริ่มต้น 1.0)")
    ap.add_argument("--no-train", action="store_true", help="รวมแล้วไม่ต้องเทรนใหม่ทันที")
    args = ap.parse_args(argv)
    p = paths()
    return cmd_apply(p, args.tolerance, train=not args.no_train) if args.apply else cmd_check(p)


if __name__ == "__main__":
    sys.exit(main())
