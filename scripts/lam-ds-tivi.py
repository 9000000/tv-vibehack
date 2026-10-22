#!/usr/bin/env python3
"""Danh sách tivi / thiết bị đã thử (từ du-lieu.json = examples/ của CleanYourTV) cho landing: web/tivi.json.
Mỗi dòng: hãng, tên ngắn, chi tiết, loại (tivi / box / may-chieu), bản Android, số gói tắt."""
import json, re, sys
src = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/du-lieu.json"
out = sys.argv[2] if len(sys.argv) > 2 else "web/tivi.json"
d = json.load(open(src, encoding="utf-8"))
TEN_HANG = {"tcl": "TCL", "jvc": "JVC", "mtc": "MTC", "rca": "RCA", "aiwa": "AIWA", "byintek": "BYINTEK", "chiq": "CHiQ",
    "ffalcon": "FFalcon", "eko": "EKO", "jmgo": "JMGO", "xgimi": "XGIMI", "nvidia": "NVIDIA", "onn": "onn.", "vu": "Vu",
    "oneplus": "OnePlus", "prismplus": "PRISM+", "benq": "BenQ", "lg": "LG", "ok": "ok.", "peaq": "PEAQ", "aipro2k": "AiPro",
    "telesystem": "Tele System", "super": "Super General", "coocaa": "Coocaa", "amazon": "Amazon Fire TV"}
MAY_CHIEU = re.compile(r"projector|máy chiếu|xgimi|jmgo|wanbo|epson|nebula|formovie|byintek|gadnic|dangbei|portronics", re.I)
BOX = re.compile(r"\b(box|stick|streaming|chromecast|shield|player|streamer|dongle|tv stick|mibox|mi box|freebox|onn)\b", re.I)
ds = []
for m in d["mau"]:
    hang_id = m["id"].split("-")[0]
    hang = TEN_HANG.get(hang_id, hang_id.capitalize())
    ten = m["ten"].strip().rstrip(",")
    ngan = re.split(r"\s*[\(,]", ten, 1)[0].strip()
    chi = ten[len(ngan):].strip(" ,()")
    if chi.count(")") > chi.count("("): chi = chi.replace(")", "", chi.count(")") - chi.count("("))
    a = re.search(r"(Google TV|Android TV|Android|Fire OS)\s+(\d+(\.\d+)?)\b(?![\dK])", ten + " " + m.get("header", ""))
    loai = "may-chieu" if MAY_CHIEU.search(ten + " " + m["id"]) else "box" if BOX.search(ten + " " + m["id"]) else "tivi"
    ds.append({"hang": hang, "ten": ngan, "chiTiet": chi[:120], "loai": loai,
               "android": f"{a.group(1)} {a.group(2)}" if a else "", "goi": len(m["goi"])})
ds.sort(key=lambda x: (x["hang"].lower(), x["ten"].lower()))
json.dump({"tong": len(ds), "hang": len({x["hang"] for x in ds}), "ds": ds}, open(out, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
print(f"{len(ds)} mẫu, {len({x['hang'] for x in ds})} hãng -> {out}")
