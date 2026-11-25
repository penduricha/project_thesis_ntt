#!/usr/bin/env bash
# =============================================================================
#  Thêm tên miền giả vào file hosts
# =============================================================================
#  Chạy MỘT LẦN để qbank.local / admin.local / moodle.local trỏ về máy này.
#
#  Vì sao dùng tên miền giả thay vì localhost:PORT:
#  - production thật không có cổng trong URL; dùng tên miền giả thì cấu hình
#    NGINX, cookie và $CFG->wwwroot của Moodle giống hệt production.
#  - Moodle lưu $CFG->wwwroot vào CSDL. Đổi URL sau khi đã cài đặt phải sửa
#    cả CSDL và purge cache — tránh được ngay từ đầu thì tốt hơn.
#
#  Cách dùng:
#      sudo bash deploy/hosts-setup.sh
# =============================================================================

set -euo pipefail

DOMAINS=("qbank.local" "admin.local" "moodle.local")
MARKER="# project-thesis-ntt (deploy/nginx)"
HOSTS_FILE="/etc/hosts"

if [[ ${EUID} -ne 0 ]]; then
    echo "Cần quyền root. Chạy lại bằng:  sudo bash $0" >&2
    exit 1
fi

# Sao lưu trước khi sửa — nếu sai thì khôi phục được.
BACKUP="${HOSTS_FILE}.bak.$(date +%Y%m%d-%H%M%S)"
cp "$HOSTS_FILE" "$BACKUP"
echo "Đã sao lưu $HOSTS_FILE -> $BACKUP"

added=0
for d in "${DOMAINS[@]}"; do
    # grep -q với dấu cách đầu để không khớp nhầm "qbank.local.example.com".
    # Dùng -F (fixed string) vì tên miền có dấu chấm, không được hiểu là regex.
    if grep -qE "^[^#]*[[:space:]]${d//./\\.}([[:space:]]|$)" "$HOSTS_FILE"; then
        echo "  [bỏ qua] $d đã có"
    else
        echo "127.0.0.1  $d  $MARKER" >> "$HOSTS_FILE"
        echo "  [thêm]   $d"
        added=$((added + 1))
    fi
done

echo
if [[ $added -eq 0 ]]; then
    echo "Không có gì thay đổi."
else
    echo "Đã thêm $added tên miền. Kiểm tra bằng:"
    echo "    getent hosts qbank.local admin.local moodle.local"
fi

echo
echo "Gỡ bỏ (nếu cần):  sudo sed -i '/${MARKER//\//\\/}/d' $HOSTS_FILE"
