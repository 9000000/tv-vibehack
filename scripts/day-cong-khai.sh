#!/usr/bin/env bash
# Đẩy bản hiện tại (HEAD) lên repo công khai github.com/vgscity/vgs-cleantv: bỏ og/ logo/ (công cụ thiết kế, thương hiệu),
# tác giả là email ẩn danh của GitHub, không mang lịch sử commit nội bộ. Dùng: scripts/day-cong-khai.sh "Ghi chú commit"
set -euo pipefail
cd "$(dirname "$0")/.."
MSG="${1:-Cập nhật CleanTV $(grep -E '^\s*versionName = ' app/build.gradle.kts | grep -oE '"[^"]+"' | tr -d '"')}"
TAM=$(mktemp -d)
git clone -q https://github.com/vgscity/vgs-cleantv "$TAM/repo"
find "$TAM/repo" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
git archive HEAD | tar -x -C "$TAM/repo"
rm -rf "$TAM/repo/og" "$TAM/repo/logo"
# Mẫu ghép lúc chạy để chính dòng này không tự khớp.
MAU="/Use""rs/|thanlan""cutduoi"
if grep -rIlE "$MAU" "$TAM/repo" --exclude-dir=.git; then echo "Có đường dẫn máy / email riêng ở các file trên, dừng." >&2; exit 1; fi
cd "$TAM/repo"
git add -A
if git diff --cached --quiet; then echo "Không có gì mới."; exit 0; fi
AI="VGS"; AE="88012563+vgscity@users.noreply.github.com"
GIT_AUTHOR_NAME=$AI GIT_AUTHOR_EMAIL=$AE GIT_COMMITTER_NAME=$AI GIT_COMMITTER_EMAIL=$AE git commit -q -m "$MSG"
git push -q origin main
echo "Đã đẩy: $(git log -1 --format='%h %s')"
