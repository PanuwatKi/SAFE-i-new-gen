# -*- coding: utf-8 -*-
"""
predict.py — เครื่องมือทดสอบโมเดลบนคอมพิวเตอร์ (สำหรับนักพัฒนา ไม่ได้อยู่ในแอป)

โหมดสนทนา: พิมพ์ทีละบรรทัด ระบบสะสมทั้งสายแล้ววิเคราะห์รวม (เหมือนแอป)
    python predict.py                 # พิมพ์ทีละบรรทัด ('จบ' = สายใหม่, 'exit' = ออก)
    python predict.py --single        # วิเคราะห์ทีละประโยค
    python predict.py --wav call.wav  # ถอดเสียงไฟล์ด้วย Whisper แล้ววิเคราะห์ (pip install -r requirements-audio.txt)
"""
import argparse
import math
import os
import sys

import joblib

from safe_text import color_of, normalize_text, scam_percent

try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stdin.reconfigure(encoding="utf-8")
except Exception:
    pass
os.system("")  # เปิดสี ANSI บน Windows terminal

HERE = os.path.dirname(os.path.abspath(__file__))
ANSI = {"RED": "\033[1;97;41m", "YELLOW": "\033[1;30;43m", "GREEN": "\033[1;97;42m", "RESET": "\033[0m"}
LABEL = {"RED": "🔴 เสี่ยงเป็นมิจฉาชีพสูงมาก", "YELLOW": "🟡 น่าสงสัย", "GREEN": "🟢 ปลอดภัย"}
SCAM_PROMPT = ("บทสนทนาทางโทรศัพท์ เกี่ยวกับ ธนาคาร บัญชี โอนเงิน รหัส OTP "
               "ตำรวจ คดี ฟอกเงิน อายัด พัสดุ ศุลกากร กดลิงก์ ลงทุน กำไร รางวัล ภาษี")
RESET_WORDS = {"จบ", "จบสาย", "สายใหม่", "reset", "end"}


def explain_words(text, model, vectorizer, topn=5):
    """คำไหนทำให้ AI สงสัย: ลองตัดทีละคำออก แล้วดูว่า % มิจฉาชีพลดลงเท่าไหร่"""
    from pythainlp.tokenize import word_tokenize

    def logit(t):
        p = min(max(scam_percent(t, model, vectorizer) / 100, 1e-9), 1 - 1e-9)
        return math.log(p / (1 - p))

    base, seen, out = logit(text), set(), []
    for tok in word_tokenize(text, engine="newmm"):
        tok = tok.strip()
        if len(tok) < 2 or tok in seen:
            continue
        seen.add(tok)
        drop = base - logit(text.replace(tok, " "))
        if drop > 0.1:
            out.append((tok, drop))
    return [w for w, _ in sorted(out, key=lambda x: -x[1])[:topn]]


def show(text, model, vectorizer, peak=None):
    pct = scam_percent(text, model, vectorizer)
    color = color_of(pct)
    extra = f" / สูงสุดในสาย {peak:.1f}%" if peak is not None else ""
    print(f"\n{ANSI[color]} {LABEL[color]} ({pct:.1f}%{extra}) {ANSI['RESET']}")
    if color != "GREEN":
        words = explain_words(text, model, vectorizer)
        if words:
            print("   🔍 คำที่ทำให้สงสัย:", "  ".join(words))
    return pct


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--single", action="store_true", help="วิเคราะห์ทีละประโยค")
    ap.add_argument("--wav", help="ไฟล์เสียงที่จะถอดแล้ววิเคราะห์")
    ap.add_argument("--whisper", default="small", help="ขนาด Whisper: tiny/base/small/medium")
    args = ap.parse_args()

    try:
        model = joblib.load(os.path.join(HERE, "model", "scam_model.pkl"))
        vectorizer = joblib.load(os.path.join(HERE, "model", "vectorizer.pkl"))
    except FileNotFoundError:
        sys.exit("❌ ไม่พบโมเดล — รัน  python train_model.py  ก่อน")

    if args.wav:
        from faster_whisper import WhisperModel
        wm = WhisperModel(args.whisper, device="cpu", compute_type="int8")
        segs, _ = wm.transcribe(args.wav, language="th", initial_prompt=SCAM_PROMPT, vad_filter=True)
        text = " ".join(s.text for s in segs).strip()
        print("📝 ถอดเสียงได้:", text)
        show(text, model, vectorizer)
        return

    lines, peak = [], 0.0
    print("พิมพ์สิ่งที่คู่สนทนาพูดทีละบรรทัด | 'จบ' = สายใหม่ | 'exit' = ออก")
    while True:
        line = input("\nคู่สนทนาพูดว่า : ").strip()
        if line.lower() == "exit":
            break
        if not line:
            continue
        if line.lower() in RESET_WORDS:
            lines, peak = [], 0.0
            print("📞 เริ่มสายใหม่")
            continue
        if args.single:
            show(line, model, vectorizer)
            continue
        lines.append(line)
        transcript = " ".join(lines)
        if len(normalize_text(transcript).replace(" ", "")) < 8:
            print(f"\n{ANSI['GREEN']} 🟢 ฟังอยู่... (ข้อมูลยังไม่พอ) {ANSI['RESET']}")
            continue
        pct = scam_percent(transcript, model, vectorizer)
        peak = max(peak, pct)
        show(transcript, model, vectorizer, peak)


if __name__ == "__main__":
    main()
