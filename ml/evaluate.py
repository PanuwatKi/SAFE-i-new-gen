# -*- coding: utf-8 -*-
"""
evaluate.py — ทดสอบ SAFE แบบละเอียด แล้วสร้างกราฟสำหรับนำเสนอ / โปสเตอร์

ทุกตัวเลขมาจากการทดสอบจริงบนข้อมูลใน ml/data/ (Cross-Validation 5 รอบ = ทุกประโยคถูกใช้ทดสอบ
ตอนที่โมเดลไม่เคยเห็นมันมาก่อน) ผลลัพธ์:
  docs/report/metrics.json   ตัวเลขทั้งหมด
  docs/report/*.png          กราฟ (ความละเอียดพอสำหรับสไลด์และโปสเตอร์)

วิธีใช้:
    cd ml
    python evaluate.py
"""
import glob
import json
import math
import os
import random
import re
import sys
import time
from collections import defaultdict

import joblib
import matplotlib
import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics import confusion_matrix, f1_score, precision_score, recall_score, roc_auc_score
from sklearn.model_selection import StratifiedKFold
from sklearn.pipeline import Pipeline

from safe_text import RED_THRESHOLD, YELLOW_THRESHOLD, make_classifier, make_vectorizer, normalize_text

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "docs", "report")
SEED = 42

# ---------- สีและตัวอักษร (ชุดสีที่ผ่านการตรวจตาบอดสีแล้ว จาก dataviz reference palette) ----------
C_SAFE = "#2a78d6"      # series 1: SAFE
C_BASE = "#eb6834"      # series 2: ตัวเปรียบเทียบ
C_RED, C_YELLOW, C_GREEN = "#c62828", "#eda100", "#2e7d32"  # สถานะ 3 สีของแอป (มีป้ายกำกับเสมอ)
INK, INK2, GRID, SURFACE = "#0b0b0b", "#52514e", "#e4e3df", "#fcfcfb"

plt.rcParams.update({
    "font.family": ["Leelawadee UI", "Tahoma", "Noto Sans Thai", "DejaVu Sans"],
    "font.size": 13,
    "axes.edgecolor": GRID, "axes.labelcolor": INK2, "axes.titlecolor": INK,
    "axes.titlesize": 16, "axes.titleweight": "bold", "axes.titlelocation": "left", "axes.titlepad": 14,
    "xtick.color": INK2, "ytick.color": INK2, "axes.grid": True, "grid.color": GRID, "grid.linewidth": 0.8,
    "axes.spines.top": False, "axes.spines.right": False, "axes.spines.left": False,
    "figure.facecolor": SURFACE, "axes.facecolor": SURFACE, "savefig.facecolor": SURFACE,
    "legend.frameon": False,
})

# คำที่ "ระบบตรวจคำต้องห้ามแบบง่าย" ใช้ — ตัวเปรียบเทียบที่ไม่มี AI (เตือนเมื่อเจอ ≥ 2 คำ)
KEYWORDS = ["โอน", "บัญชี", "otp", "รหัส", "ลิงก์", "อายัด", "ตำรวจ", "คดี", "ฟอกเงิน", "ค่าธรรมเนียม",
            "ติดตั้งแอป", "ยืนยันตัวตน", "รางวัล", "ลงทุน", "ด่วน", "ภาษี", "พัสดุ"]


def load():
    files = sorted(glob.glob(os.path.join(HERE, "data", "*.csv")))
    df = pd.concat([pd.read_csv(f, encoding="utf-8-sig").assign(file=os.path.basename(f)) for f in files], ignore_index=True)
    df = df.dropna(subset=["text", "label"])
    df["text"] = df["text"].astype(str).str.strip()
    df["label"] = df["label"].astype(int)
    df["category"] = df["category"].fillna("ไม่ระบุ").astype(str).map(merge_category)
    return df.drop_duplicates(subset=["text"]).reset_index(drop=True)


# ชื่อหมวดใน Dataset ต่างไฟล์เขียนไม่เหมือนกัน -> รวมให้เป็นชื่อเดียวกันตอนรายงาน (ไม่แก้ไฟล์ข้อมูล)
CATEGORY_MERGE = {
    "ตำรวจ": "ตำรวจ/หน่วยงานรัฐ", "หน่วยงาน": "ตำรวจ/หน่วยงานรัฐ", "ปปง.": "ตำรวจ/หน่วยงานรัฐ",
    "โอนเงินผิด": "โอนผิดบัญชี", "บัญชี/บัตร": "ธนาคาร", "ปลอมเป็นธนาคาร": "ธนาคาร",
    "พัสดุตกค้าง": "พัสดุ", "romance": "หลอกรัก (romance)",
}


def merge_category(cat):
    if cat.startswith("ค่าน้ำ"):
        return "ค่าน้ำ-ค่าไฟ"
    base = cat.split("-")[0].strip()          # "เงินกู้-เก็บค่าค้ำประกันล่วงหน้า" -> "เงินกู้"
    return CATEGORY_MERGE.get(base, base)


def safe_model():
    return Pipeline([("v", make_vectorizer()), ("c", make_classifier())])


def word_model():
    """โมเดลแบบตัดคำ (แนวทางเดิมก่อนเปลี่ยนเป็นระดับตัวอักษร) — ใช้เทียบความทนต่อคำสะกดผิด"""
    from pythainlp.tokenize import word_tokenize
    vec = TfidfVectorizer(preprocessor=normalize_text, tokenizer=lambda t: word_tokenize(t, engine="newmm"),
                          token_pattern=None, ngram_range=(1, 2), sublinear_tf=True)
    return Pipeline([("v", vec), ("c", make_classifier())])   # ตัวจำแนกเดียวกัน ต่างแค่วิธีแยกข้อความ


def keyword_flag(text):
    t = text.lower()
    return int(sum(k in t for k in KEYWORDS) >= 2)


THAI_CHARS = [chr(c) for c in range(0x0E01, 0x0E2F)] + ["า", "ิ", "ี", "ุ", "ู", "เ", "แ", "่", "้"]


def add_noise(text, rate, rng):
    """จำลองการถอดเสียงผิด: สุ่มลบ / แทนที่ / แทรกตัวอักษรตามอัตราที่กำหนด"""
    out = []
    for ch in text:
        r = rng.random()
        if ch.isspace() or r >= rate:
            out.append(ch)
        elif r < rate / 3:
            continue                                   # ลบ
        elif r < 2 * rate / 3:
            out.append(rng.choice(THAI_CHARS))         # แทนที่
        else:
            out.append(ch + rng.choice(THAI_CHARS))    # แทรก
    return "".join(out)


def pct(x):
    return round(float(x) * 100, 1)


def main():
    os.makedirs(OUT, exist_ok=True)
    df = load()
    X, y = df["text"].values, df["label"].values
    n_scam, n_ok = int(y.sum()), int((y == 0).sum())
    print(f"ข้อมูล {len(df)} ประโยค (มิจฉาชีพ {n_scam} / ปกติ {n_ok})")

    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=SEED)
    oof = np.zeros(len(df))                 # % มิจฉาชีพ (out-of-fold)
    noise_levels = [0.0, 0.05, 0.10, 0.20]
    noisy_acc = {"char": defaultdict(list), "word": defaultdict(list)}
    early_fracs = [0.25, 0.5, 0.75, 1.0]
    early_red = defaultdict(list)
    early_flag = defaultdict(list)

    for fold, (tr, te) in enumerate(cv.split(X, y), 1):
        m = safe_model().fit(X[tr], y[tr])
        oof[te] = m.predict_proba(X[te])[:, 1] * 100
        w = word_model().fit(X[tr], y[tr])
        rng = random.Random(SEED + fold)
        for lvl in noise_levels:
            noisy = [add_noise(t, lvl, rng) for t in X[te]]
            noisy_acc["char"][lvl].append(np.mean((m.predict_proba(noisy)[:, 1] >= 0.5) == y[te]))
            noisy_acc["word"][lvl].append(np.mean((w.predict_proba(noisy)[:, 1] >= 0.5) == y[te]))
        scam_te = [t for t, lab in zip(X[te], y[te]) if lab == 1]
        for f in early_fracs:
            part = [t[: max(1, int(len(t) * f))] for t in scam_te]
            p = m.predict_proba(part)[:, 1] * 100
            early_red[f].extend(p >= RED_THRESHOLD)
            early_flag[f].extend(p >= YELLOW_THRESHOLD)
        print(f"  รอบที่ {fold}/5 เสร็จ")

    # ---------- ตัวชี้วัดหลัก ----------
    pred = (oof >= 50).astype(int)
    kw = np.array([keyword_flag(t) for t in X])
    tn, fp, fn, tp = confusion_matrix(y, pred).ravel()
    colors = np.where(oof >= RED_THRESHOLD, "RED", np.where(oof >= YELLOW_THRESHOLD, "YELLOW", "GREEN"))

    def metrics(p):
        return {"accuracy": pct(np.mean(p == y)), "precision": pct(precision_score(y, p)),
                "recall": pct(recall_score(y, p)), "f1": pct(f1_score(y, p))}

    m_safe, m_kw = metrics(pred), metrics(kw)
    color_table = {
        "scam": {c: int(np.sum((colors == c) & (y == 1))) for c in ["RED", "YELLOW", "GREEN"]},
        "normal": {c: int(np.sum((colors == c) & (y == 0))) for c in ["RED", "YELLOW", "GREEN"]},
    }

    # ---------- แยกตามประเภท ----------
    cat_rows = []
    for cat, g in df[df.label == 1].groupby("category"):
        if len(g) >= 3:
            cat_rows.append((cat, len(g), pct(np.mean(oof[g.index] >= 50))))
    cat_rows.sort(key=lambda r: (r[2], r[1]))
    hard_neg = df[(df.label == 0) & (df.file == "Dataset9_news.csv")].index
    news_scam = df[(df.label == 1) & (df.file == "Dataset9_news.csv")].index

    # ---------- เกณฑ์สี ----------
    thresholds = list(range(0, 101, 2))
    th_recall = [pct(np.mean(oof[y == 1] >= t)) for t in thresholds]
    th_fpr = [pct(np.mean(oof[y == 0] >= t)) for t in thresholds]

    # ---------- ความเร็วและขนาด ----------
    model = joblib.load(os.path.join(MODEL_DIR, "scam_model.pkl"))
    vec = joblib.load(os.path.join(MODEL_DIR, "vectorizer.pkl"))
    sample = list(X[:200])
    t0 = time.perf_counter()
    for t in sample:
        model.predict_proba(vec.transform([t]))
    py_ms = (time.perf_counter() - t0) / len(sample) * 1000
    model_json = os.path.join(HERE, "..", "android", "app", "src", "main", "assets", "scam_model.json")

    results = {
        "generated_at": time.strftime("%Y-%m-%d"),
        "dataset": {"total": len(df), "scam": n_scam, "normal": n_ok,
                    "scam_categories": int(df[df.label == 1].category.nunique()),
                    "news_rows": int((df.file == "Dataset9_news.csv").sum())},
        "method": "Stratified 5-fold cross-validation; every sentence is scored by a model that never saw it",
        "safe": {**m_safe, "roc_auc": round(float(roc_auc_score(y, oof)), 3),
                 "confusion": {"tn": int(tn), "fp": int(fp), "fn": int(fn), "tp": int(tp)}},
        "keyword_baseline": {**m_kw, "rule": "flag if >= 2 of %d keywords" % len(KEYWORDS)},
        "three_colors": color_table,
        "hard_negatives_false_alarm_pct": pct(np.mean(oof[hard_neg] >= 50)) if len(hard_neg) else None,
        "news_scam_recall_pct": pct(np.mean(oof[news_scam] >= 50)) if len(news_scam) else None,
        "by_category": [{"category": c, "n": n, "recall": r} for c, n, r in cat_rows],
        "thresholds": {"t": thresholds, "scam_caught_pct": th_recall, "false_alarm_pct": th_fpr},
        "noise_robustness": {k: {str(int(l * 100)): pct(np.mean(v[l])) for l in noise_levels} for k, v in noisy_acc.items()},
        "early_warning": {str(int(f * 100)): {"red_pct": pct(np.mean(early_red[f])), "flag_pct": pct(np.mean(early_flag[f]))} for f in early_fracs},
        "speed_size": {"python_ms_per_message": round(py_ms, 2),
                       "model_json_mb": round(os.path.getsize(model_json) / 1e6, 2),
                       "features": len(vec.vocabulary_)},
    }
    with open(os.path.join(OUT, "metrics.json"), "w", encoding="utf-8") as fp_:
        json.dump(results, fp_, ensure_ascii=False, indent=1)

    draw(results)
    print(json.dumps({k: results[k] for k in ["safe", "keyword_baseline", "three_colors", "noise_robustness", "early_warning", "speed_size"]}, ensure_ascii=False, indent=1))
    print(f"✅ บันทึกผลที่ {os.path.normpath(OUT)}")


MODEL_DIR = os.path.join(HERE, "model")


def save(fig, name):
    fig.savefig(os.path.join(OUT, name), dpi=220, bbox_inches="tight")
    plt.close(fig)


def label_bars(ax, bars, fmt="{:.1f}%"):
    for b in bars:
        ax.text(b.get_width() + 1, b.get_y() + b.get_height() / 2, fmt.format(b.get_width()),
                va="center", ha="left", color=INK, fontsize=12)


def draw(r):
    # 1) SAFE เทียบระบบคำต้องห้าม
    names = [("accuracy", "ความแม่นยำรวม"), ("recall", "จับมิจฉาชีพได้ (Recall)"),
             ("precision", "เตือนแล้วถูก (Precision)"), ("f1", "F1-score")]
    fig, ax = plt.subplots(figsize=(9, 5))
    yy = np.arange(len(names))
    b1 = ax.barh(yy + 0.2, [r["safe"][k] for k, _ in names], height=0.36, color=C_SAFE, label="SAFE (AI)")
    b2 = ax.barh(yy - 0.2, [r["keyword_baseline"][k] for k, _ in names], height=0.36, color=C_BASE, label="ตรวจคำต้องห้าม (ไม่มี AI)")
    label_bars(ax, b1); label_bars(ax, b2)
    ax.set_yticks(yy, [n for _, n in names]); ax.invert_yaxis()
    ax.set_xlim(0, 110); ax.set_xlabel("%"); ax.grid(axis="y", visible=False)
    ax.set_title("SAFE เทียบกับการตรวจคำต้องห้ามแบบธรรมดา", pad=34)
    ax.legend(loc="lower left", bbox_to_anchor=(0, 1.0), ncol=2)
    save(fig, "01_safe_vs_keywords.png")

    # 2) ผลแบบ 3 สี
    ct = r["three_colors"]
    fig, ax = plt.subplots(figsize=(9, 4.2))
    groups = [("ข้อความมิจฉาชีพ", ct["scam"]), ("ข้อความปกติ", ct["normal"])]
    for i, (g, d) in enumerate(groups):
        total = sum(d.values()); left = 0
        for c, col, lab in [("RED", C_RED, "แดง"), ("YELLOW", C_YELLOW, "เหลือง"), ("GREEN", C_GREEN, "เขียว")]:
            share = d[c] / total * 100
            ax.barh(i, share, left=left, color=col, edgecolor=SURFACE, linewidth=2, height=0.55)
            if share >= 6:
                ax.text(left + share / 2, i, f"{lab} {share:.0f}%", ha="center", va="center", color="white" if c != "YELLOW" else INK, fontsize=12, fontweight="bold")
            left += share
    ax.set_yticks([0, 1], [g for g, _ in groups]); ax.invert_yaxis(); ax.set_xlim(0, 100)
    ax.set_xlabel("% ของข้อความในกลุ่ม"); ax.grid(visible=False)
    from matplotlib.patches import Patch
    ax.legend(handles=[Patch(color=C_RED, label="แดง เสี่ยงสูง"), Patch(color=C_YELLOW, label="เหลือง น่าสงสัย"), Patch(color=C_GREEN, label="เขียว ปลอดภัย")],
              loc="lower left", bbox_to_anchor=(0, 1.0), ncol=3)
    ax.set_title("ไฟ 3 สีของ SAFE: ข้อความแต่ละแบบได้สีอะไร", pad=34)
    save(fig, "02_three_colors.png")

    # 3) เกณฑ์สี: จับได้ vs เตือนผิด
    th = r["thresholds"]
    fig, ax = plt.subplots(figsize=(9, 5))
    ax.plot(th["t"], th["scam_caught_pct"], color=C_SAFE, linewidth=2.2, label="จับมิจฉาชีพได้")
    ax.plot(th["t"], th["false_alarm_pct"], color=C_BASE, linewidth=2.2, label="เตือนผิด (ข้อความปกติ)")
    for t, lab in [(YELLOW_THRESHOLD, "เส้นเหลือง 40%"), (RED_THRESHOLD, "เส้นแดง 70%")]:
        ax.axvline(t, color=INK2, linewidth=1, linestyle="--")
        ax.text(t + 1, 45, lab, color=INK2, fontsize=11)
    i40, i70 = th["t"].index(YELLOW_THRESHOLD), th["t"].index(RED_THRESHOLD)
    for i in (i40, i70):
        for key, col in (("scam_caught_pct", C_SAFE), ("false_alarm_pct", C_BASE)):
            ax.plot(th["t"][i], th[key][i], "o", color=col, markersize=8, markeredgecolor=SURFACE, markeredgewidth=2)
            ax.text(th["t"][i] + 1.5, th[key][i] + 2.5, f"{th[key][i]:.0f}%", color=INK, fontsize=11)
    ax.set_xlabel("เกณฑ์ % ที่ใช้ตัดสิน"); ax.set_ylabel("%"); ax.set_ylim(-2, 105); ax.set_xlim(0, 100)
    ax.set_title("เลือกเส้นแบ่งสีอย่างไร: จับได้มาก แต่เตือนผิดน้อย", pad=34)
    ax.legend(loc="lower left", bbox_to_anchor=(0, 1.0), ncol=2)
    save(fig, "03_thresholds.png")

    # 4) ทนต่อการถอดเสียงผิด
    nr = r["noise_robustness"]; lv = list(nr["char"].keys())
    fig, ax = plt.subplots(figsize=(9, 5))
    ax.plot(lv, [nr["char"][k] for k in lv], marker="o", markersize=8, color=C_SAFE, linewidth=2.2, label="SAFE (ดูระดับตัวอักษร)")
    ax.plot(lv, [nr["word"][k] for k in lv], marker="o", markersize=8, color=C_BASE, linewidth=2.2, label="แบบตัดคำ (แนวทางเดิม)")
    for k in lv:
        c_up = nr["char"][k] >= nr["word"][k]          # ป้ายของเส้นที่อยู่บนไว้ด้านบน อีกเส้นไว้ด้านล่าง
        ax.annotate(f'{nr["char"][k]:.1f}%', (k, nr["char"][k]), textcoords="offset points", xytext=(0, 10 if c_up else -18), ha="center", color=INK, fontsize=11)
        ax.annotate(f'{nr["word"][k]:.1f}%', (k, nr["word"][k]), textcoords="offset points", xytext=(0, -18 if c_up else 10), ha="center", color=INK, fontsize=11)
    ax.set_xticks(range(len(lv)), [f"{k}%" for k in lv])
    ax.set_xlabel("ตัวอักษรที่ถอดเสียงผิด (จำลอง)"); ax.set_ylabel("ความแม่นยำ %"); ax.set_ylim(80, 100.5)
    ax.set_title("ยังแม่นแม้ถอดเสียงผิด")
    ax.legend(loc="lower left")
    save(fig, "04_noise_robustness.png")

    # 5) เตือนได้เร็วแค่ไหน
    ew = r["early_warning"]; fr = list(ew.keys())
    fig, ax = plt.subplots(figsize=(9, 5))
    bars = ax.bar(fr, [ew[f]["flag_pct"] for f in fr], color=C_SAFE, width=0.55)
    for b, f in zip(bars, fr):
        ax.text(b.get_x() + b.get_width() / 2, b.get_height() + 1.5, f'{ew[f]["flag_pct"]:.0f}%', ha="center", color=INK, fontsize=12)
    ax.set_xticks(range(len(fr)), [f"ได้ยิน {f}%" for f in fr])
    ax.set_ylabel("% ข้อความมิจฉาชีพที่ขึ้นเหลืองหรือแดงแล้ว"); ax.set_ylim(0, 110); ax.grid(axis="x", visible=False)
    ax.set_title("เตือนได้ก่อนคุยจบ")
    save(fig, "05_early_warning.png")

    # 6) แยกตามประเภทมิจฉาชีพ
    cats = r["by_category"]
    fig, ax = plt.subplots(figsize=(9, max(4, 0.42 * len(cats) + 1.2)))
    bars = ax.barh([f'{c["category"]} ({c["n"]})' for c in cats], [c["recall"] for c in cats], color=C_SAFE, height=0.6)
    label_bars(ax, bars)
    ax.set_xlim(0, 112); ax.set_xlabel("% ที่จับได้"); ax.grid(axis="y", visible=False)
    ax.set_title("จับได้แค่ไหนในแต่ละมุก (ตัวเลขในวงเล็บ = จำนวนตัวอย่าง)")
    save(fig, "06_by_category.png")


if __name__ == "__main__":
    main()
