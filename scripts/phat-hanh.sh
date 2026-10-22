#!/usr/bin/env bash
# Dựng bản release, chép vào web/ cùng dữ liệu OTA, sinh cap-nhat.json. Deploy riêng bằng:
#   cd web && npx vercel deploy --prod --yes
# Tăng versionCode/versionName trong app/build.gradle.kts trước khi phát hành bản app mới.
# Ghi chú bản mới (hiện trong app + thông báo): GHI_CHU="..." GHI_CHU_EN="..." scripts/phat-hanh.sh
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
./gradlew assembleRelease -q --console=plain
APK=app/build/outputs/apk/release/app-release.apk
CODE=$(grep -E '^\s*versionCode = ' app/build.gradle.kts | grep -oE '[0-9]+')
NAME=$(grep -E '^\s*versionName = ' app/build.gradle.kts | grep -oE '"[^"]+"' | tr -d '"')
mkdir -p web/ota
# APK nằm trên Cloudflare R2 (bucket cleantv): bản mới nhất + bản theo số. Khoá Cloudflare (CF_API_TOKEN, CF_ACCOUNT_ID)
# đọc từ file $CF_ENV (mặc định .env.cloudflare ở gốc repo, không nằm trong git).
R2=https://pub-925dff61607e4e3da7398d3413ccd1da.r2.dev
CF_ENV="${CF_ENV:-.env.cloudflare}"
[ -f "$CF_ENV" ] && { set -a; . "$CF_ENV"; set +a; }
: "${CF_API_TOKEN:?Thiếu CF_API_TOKEN (xem .env.cloudflare)}"
for k in CleanTV.apk "CleanTV-$NAME.apk"; do
  # R2 thỉnh thoảng trả 502 (21/10/2026): thử lại tối đa 3 lần.
  for lan in 1 2 3; do
    CLOUDFLARE_API_TOKEN=$CF_API_TOKEN CLOUDFLARE_ACCOUNT_ID=$CF_ACCOUNT_ID npx wrangler r2 object put "cleantv/$k" --file "$APK"     --content-type application/vnd.android.package-archive --cache-control "public, max-age=300" --remote >/dev/null 2>&1 && break
    [ $lan = 3 ] && { echo "Không đẩy được $k lên R2" >&2; exit 1; }
    sleep 5
  done
done
cp app/src/main/assets/du-lieu.json web/ota/du-lieu.json
cp app/src/main/assets/tvhome.apk web/ota/tvhome.apk
sha() { shasum -a 256 "$1" | cut -c1-16; }
co() { stat -f%z "$1"; }
python3 -I - "$CODE" "$NAME" "${GHI_CHU:-}" "${GHI_CHU_EN:-}" "$(co "$APK")" "$R2/CleanTV-$NAME.apk" \
  "$(sha web/ota/du-lieu.json)" "$(co web/ota/du-lieu.json)" "$(sha web/ota/tvhome.apk)" "$(co web/ota/tvhome.apk)" <<'PY' > web/cap-nhat.json
import json, sys, datetime
ngay = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=7))).strftime("%Y-%m-%d")
code, name, ghi, ghi_en, co_apk, url_apk, s1, c1, s2, c2 = sys.argv[1:]
print(json.dumps({
  "app": {"versionCode": int(code), "versionName": name, "apk": url_apk, "co": int(co_apk), "ghiChu": ghi, "ghiChuEn": ghi_en, "ngay": ngay},
  "duLieu": {
    "du-lieu.json": {"phienBan": s1, "url": "/ota/du-lieu.json", "co": int(c1)},
    "tvhome.apk": {"phienBan": s2, "url": "/ota/tvhome.apk", "co": int(c2)},
  },
}, ensure_ascii=False, indent=1))
PY
# Nhật ký cập nhật (landing vgs.zone/cleantv + /en đọc web/nhat-ky.json): NHAT_KY / NHAT_KY_EN mỗi ý một dòng,
# để trống thì lấy GHI_CHU / GHI_CHU_EN làm một ý. Cùng số bản thì ghi đè dòng cũ.
python3 -I - "$NAME" "${NHAT_KY:-${GHI_CHU:-}}" "${NHAT_KY_EN:-${GHI_CHU_EN:-}}" <<'PY'
import json, sys, datetime
ban, vi, en = sys.argv[1:]
f = "web/nhat-ky.json"
ds = json.load(open(f, encoding="utf-8"))
ngay = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=7))).strftime("%Y-%m-%d")
dong = {"ban": ban, "ngay": ngay, "vi": [x.strip() for x in vi.splitlines() if x.strip()], "en": [x.strip() for x in en.splitlines() if x.strip()]}
ds = [dong] + [d for d in ds if d["ban"] != ban]
json.dump(ds, open(f, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
PY
echo "Xong: CleanTV $NAME ($CODE), $(du -h "$APK" | cut -f1) → $R2/CleanTV-$NAME.apk"
cat web/cap-nhat.json
