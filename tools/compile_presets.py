#!/usr/bin/env python3
"""Compiles all 213 presets in examples/ into a rich, accurately indexed JSON database."""
import json
import os
import re
from pathlib import Path

EXAMPLES_DIR = Path(__file__).resolve().parent.parent / "examples"
README_FILE = EXAMPLES_DIR / "README.md"
OUTPUT_FILE = Path(__file__).resolve().parent / "presets.json"

PACKAGE_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")

CATEGORY_KEYWORDS = {
    "telemetry": ["telemetry", "analytics", "tracking", "metric", "logger", "logging", "crash", "feedback", "eula", "diagnostic"],
    "ads": ["ad", "ads", "advertising", "alphonso", "samba", "acr", "promo", "sponsor"],
    "bloatware": ["patchwall", "recommendation", "autoinstall", "channel", "store", "shop", "game", "mall", "demo", "retail"],
    "personal_choice": ["assistant", "katniss", "voice", "alexa", "speech", "mediashell", "cast", "milink", "miracast", "airplay", "update", "ota", "music", "photos"],
    "protected": ["setupwraith", "tvsetting", "input", "hardware", "bluetooth", "remote", "system", "framework"]
}

def guess_category(pkg_name, comment=""):
    combined = (pkg_name + " " + comment).lower()
    if "setupwraith" in combined or "tv_input_hardware" in combined or "do not disable" in combined or "protected" in combined:
        return "protected"
    for cat, keywords in CATEGORY_KEYWORDS.items():
        if any(kw in combined for kw in keywords):
            return cat
    return "bloatware"

def parse_readme_index():
    text = README_FILE.read_text(encoding="utf-8", errors="replace")
    index = {}
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("| `") and line.endswith("|"):
            parts = [p.strip() for p in line.split("|")[1:-1]]
            if len(parts) >= 4:
                folder = parts[0].replace("`", "").strip().rstrip("/")
                tv_desc = parts[1]
                os_ver = parts[2]
                notes = parts[3]

                aliases = []
                for paren in re.findall(r"\((.*?)\)", tv_desc):
                    for item in re.split(r"[,;]\s*", paren):
                        item = item.strip()
                        if item and len(item) > 1:
                            item = re.sub(r"^(?:device|product|model|codename|board)\s+", "", item, flags=re.I)
                            aliases.append(item)

                # Determine brand from folder name prefix
                first_part = folder.split("-")[0].capitalize()
                brand_map = {
                    "Mitv": "Xiaomi",
                    "Redmi": "Xiaomi",
                    "Gtv": "Google",
                    "Onn": "Onn",
                    "Ffalcon": "FFalcon",
                    "Aft": "Amazon",
                    "Fire": "Amazon",
                    "Chiq": "CHiQ"
                }
                brand = brand_map.get(first_part, first_part)
                clean_name = re.sub(r"\s*\([^)]*\)", "", tv_desc).strip()

                index[folder] = {
                    "name": clean_name if clean_name else folder,
                    "brand": brand,
                    "os": os_ver,
                    "aliases": aliases,
                    "notes": notes
                }
    return index

def parse_debloat_file(folder_name, file_path, readme_info):
    text = file_path.read_text(encoding="utf-8-sig", errors="replace")
    lines = text.splitlines()

    header_lines = []
    packages = []
    is_header = True
    current_section = "General"

    for line in lines:
        raw_line = line.strip()
        if not raw_line:
            continue
        if raw_line.startswith("#"):
            comment_text = raw_line.lstrip("#").strip()
            if is_header:
                if any(k in comment_text.lower() for k in ["apply:", "undo:", "telemetry", "patchwall", "bloatware"]):
                    is_header = False
                else:
                    header_lines.append(comment_text)
            else:
                if len(comment_text) > 3 and not comment_text.startswith("Apply:") and not comment_text.startswith("Undo:"):
                    current_section = comment_text
            continue

        is_header = False
        parts = line.split("#", 1)
        pkg_name = parts[0].strip().split()[0] if parts[0].strip() else ""
        comment = parts[1].strip() if len(parts) > 1 else ""

        if PACKAGE_RE.match(pkg_name):
            cat = guess_category(pkg_name, comment + " " + current_section)
            safe = cat != "protected"
            default_checked = cat in ["telemetry", "ads", "bloatware"]

            packages.append({
                "package": pkg_name,
                "category": cat,
                "comment": comment,
                "section": current_section,
                "safe_to_disable": safe,
                "default_checked": default_checked
            })

    header_str = " ".join(header_lines)

    # Detect Stock Launcher
    stock_launcher = "com.google.android.tvlauncher"
    if "launcherx" in header_str.lower() or "google tv" in (readme_info.get("os", "")).lower():
        stock_launcher = "com.google.android.apps.tv.launcherx"
    elif "patchwall" in header_str.lower() and "xiaomi" in folder_name.lower():
        stock_launcher = "com.google.android.tvlauncher"

    stock_match = re.search(r"Stock\s+launcher:\s*([A-Za-z0-9_\.]+)", header_str, re.IGNORECASE)
    if stock_match:
        stock_launcher = stock_match.group(1)

    # Aliases and models
    models = list(readme_info.get("aliases", []))
    for m in re.findall(r"(?:model|device|codename)\s+([A-Za-z0-9_\-]+)", header_str, re.I):
        if m not in models:
            models.append(m)

    soc_match = re.search(r"(Amlogic\s*[\w\d]+|MediaTek\s*[\w\d]+|Realtek\s*[\w\d]+|MStar\s*[\w\d]+|Allwinner\s*[\w\d]+)", header_str, re.IGNORECASE)
    soc = soc_match.group(1) if soc_match else ""

    notes = readme_info.get("notes", "")
    if not notes and header_lines:
        notes = " ".join(header_lines[:3])

    return {
        "id": folder_name,
        "name": readme_info.get("name", folder_name.replace("-", " ").title()),
        "brand": readme_info.get("brand", "Generic"),
        "models": models,
        "os": readme_info.get("os", "Android TV"),
        "soc": soc,
        "stock_launcher": stock_launcher,
        "header_notes": notes,
        "packages": packages
    }

def main():
    readme_index = parse_readme_index()
    print(f"Loaded {len(readme_index)} items from README index.")

    presets = []
    for debloat_file in sorted(EXAMPLES_DIR.glob("*/debloat.txt")):
        folder_name = debloat_file.parent.name
        info = readme_index.get(folder_name, {})
        preset = parse_debloat_file(folder_name, debloat_file, info)
        presets.append(preset)

    print(f"Parsed {len(presets)} presets successfully.")

    generic_presets = [
        {
            "id": "generic-google-tv",
            "name": "Google TV (Mẫu chung cho mọi hãng)",
            "brand": "Google",
            "models": ["google-tv", "chromecast", "launcherx"],
            "os": "Google TV (Android 11-14)",
            "soc": "*",
            "stock_launcher": "com.google.android.apps.tv.launcherx",
            "header_notes": "Quy tắc an toàn chung cho Google TV: tắt quảng cáo gợi ý, thu thập dữ liệu Alphonso/Samba TV nhưng bảo vệ Wi-Fi setupwraith.",
            "packages": [
                {"package": "com.google.android.feedback", "category": "telemetry", "comment": "Google feedback reporting", "section": "Telemetry", "safe_to_disable": True, "default_checked": True},
                {"package": "com.android.tv.feedbackconsent", "category": "telemetry", "comment": "Feedback consent prompt", "section": "Telemetry", "safe_to_disable": True, "default_checked": True},
                {"package": "com.google.android.tvrecommendations", "category": "bloatware", "comment": "Home sponsored recommendations", "section": "Recommendations", "safe_to_disable": True, "default_checked": True},
                {"package": "tv.alphonso.alphonso_eula", "category": "ads", "comment": "Alphonso ACR ad tracking", "section": "Ads", "safe_to_disable": True, "default_checked": True},
                {"package": "com.google.android.katniss", "category": "personal_choice", "comment": "Google Assistant voice search", "section": "Voice", "safe_to_disable": True, "default_checked": False},
                {"package": "com.google.android.apps.mediashell", "category": "personal_choice", "comment": "Chromecast built-in", "section": "Cast", "safe_to_disable": True, "default_checked": False},
                {"package": "com.google.android.tungsten.setupwraith", "category": "protected", "comment": "Wi-Fi credential guard - KHÔNG ĐƯỢC TẮT", "section": "Protected", "safe_to_disable": False, "default_checked": False}
            ]
        },
        {
            "id": "generic-android-tv",
            "name": "Android TV (Mẫu chung cho Android 8, 9, 10, 11)",
            "brand": "Generic",
            "models": ["android-tv", "leanback", "tvlauncher"],
            "os": "Android TV (Android 8-11)",
            "soc": "*",
            "stock_launcher": "com.google.android.tvlauncher",
            "header_notes": "Quy tắc an toàn chung cho Android TV Leanback: tắt quảng cáo đối tác, báo cáo lỗi và các kênh đề xuất.",
            "packages": [
                {"package": "com.google.android.feedback", "category": "telemetry", "comment": "Google feedback", "section": "Telemetry", "safe_to_disable": True, "default_checked": True},
                {"package": "com.google.android.tv.bugreportsender", "category": "telemetry", "comment": "Bug report sender", "section": "Telemetry", "safe_to_disable": True, "default_checked": True},
                {"package": "com.google.android.tvrecommendations", "category": "bloatware", "comment": "Recommendation channel rows", "section": "Recommendations", "safe_to_disable": True, "default_checked": True},
                {"package": "tv.alphonso.alphonso_eula", "category": "ads", "comment": "Alphonso ad tracking", "section": "Ads", "safe_to_disable": True, "default_checked": True},
                {"package": "com.google.android.katniss", "category": "personal_choice", "comment": "Google Assistant voice search", "section": "Voice", "safe_to_disable": True, "default_checked": False},
                {"package": "com.google.android.apps.mediashell", "category": "personal_choice", "comment": "Chromecast built-in", "section": "Cast", "safe_to_disable": True, "default_checked": False}
            ]
        }
    ]

    all_data = {
        "version": 2,
        "count": len(presets) + len(generic_presets),
        "generic": generic_presets,
        "presets": presets
    }

    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(all_data, f, ensure_ascii=False, indent=2)

    # Also copy to manager assets
    target_asset = Path(__file__).resolve().parent.parent / "launcher" / "manager" / "src" / "main" / "assets" / "presets.json"
    with open(target_asset, "w", encoding="utf-8") as f:
        json.dump(all_data, f, ensure_ascii=False, indent=2)

    print(f"Written {len(all_data['presets'])} rich presets to {OUTPUT_FILE} and {target_asset}")

if __name__ == "__main__":
    main()
