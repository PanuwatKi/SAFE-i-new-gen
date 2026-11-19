# -*- coding: utf-8 -*-
"""
สร้าง privacy_policy.html จาก docs/PRIVACY_POLICY.md สำหรับวางใน Google Sites (หน้านโยบายสาธารณะที่ Play Store บังคับ)
    python docs/store/build_privacy_html.py
แก้ PRIVACY_POLICY.md เมื่อไหร่ ให้รันใหม่ แล้วคัดลอกไปวางใน Google Sites อีกครั้ง
ไม่ต้องติดตั้งอะไรเพิ่ม · บรรทัดที่ขึ้นต้นด้วย ">" (หมายเหตุสำหรับนักพัฒนา) จะไม่ถูกใส่ในหน้าสาธารณะ
"""
import html
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "PRIVACY_POLICY.md")
OUT = os.path.join(HERE, "privacy_policy.html")


def inline(text):
    t = html.escape(text, quote=False)
    t = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", t)
    t = re.sub(r"`(.+?)`", r"<code>\1</code>", t)
    t = re.sub(r"\[(.+?)\]\((https?://[^)\s]+)\)", r'<a href="\2">\1</a>', t)
    return t


def convert(md):
    out, para, table, lists = [], [], [], []

    def flush_para():
        if para:
            out.append("<p>" + "<br>".join(inline(x) for x in para) + "</p>")
            para.clear()

    def flush_table():
        if table:
            rows = [r for r in table if not re.fullmatch(r"\|[\s:|-]+\|", r)]
            cells = [[c.strip() for c in r.strip("|").split("|")] for r in rows]
            out.append("<table>")
            out.append("<tr>" + "".join(f"<th>{inline(c)}</th>" for c in cells[0]) + "</tr>")
            for r in cells[1:]:
                out.append("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>")
            out.append("</table>")
            table.clear()

    def close_lists(depth=0):
        while len(lists) > depth:
            out.append("</li></ul>")
            lists.pop()

    for line in md.splitlines():
        if line.startswith(">"):
            continue
        m = re.match(r"^( *)- (.*)$", line)
        if m:
            flush_para(); flush_table()
            depth = len(m.group(1)) // 2 + 1
            if len(lists) < depth:
                out.append("<ul><li>")
                lists.append(depth)
            else:
                close_lists(depth)
                out.append("</li><li>")
            out.append(inline(m.group(2)))
            continue
        close_lists()
        if line.startswith("|"):
            flush_para()
            table.append(line.strip())
            continue
        flush_table()
        h = re.match(r"^(#{1,3}) (.*)$", line)
        if h:
            flush_para()
            n = len(h.group(1))
            out.append(f"<h{n}>{inline(h.group(2))}</h{n}>")
        elif line.strip() == "---":
            flush_para()
            out.append("<hr>")
        elif not line.strip():
            flush_para()
        else:
            para.append(line.strip())
    flush_para(); flush_table(); close_lists()
    return "\n".join(out)


def main():
    body = convert(open(SRC, encoding="utf-8").read())
    page = f"""<!doctype html>
<html lang="th">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>นโยบายความเป็นส่วนตัว SAFE / SAFE Privacy Policy</title>
<style>
body {{ margin: 0; background: #F6F4EF; color: #10233F; font: 16px/1.65 Tahoma, "Noto Sans Thai", sans-serif; }}
main {{ max-width: 760px; margin: 0 auto; padding: 32px 16px 64px; }}
h1 {{ font-size: 1.6rem; line-height: 1.3; }} h2 {{ margin-top: 2.2rem; }} h3 {{ margin-top: 1.6rem; }}
table {{ border-collapse: collapse; width: 100%; margin: 1rem 0; background: #FFFFFF; }}
th, td {{ border: 1px solid #CBD3DF; padding: 8px 10px; text-align: left; vertical-align: top; }}
th {{ background: #E6ECF4; }} code {{ background: #E6ECF4; padding: 0 4px; border-radius: 4px; }}
a {{ color: #1F63B8; }} hr {{ border: 0; border-top: 1px solid #CBD3DF; margin: 2rem 0; }}
</style>
</head>
<body>
<main>
{body}
</main>
</body>
</html>
"""
    open(OUT, "w", encoding="utf-8", newline="\n").write(page)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
