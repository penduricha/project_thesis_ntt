#!/usr/bin/env bash
# =============================================================================
#  Khởi động toàn bộ hệ thống bằng một lệnh
# =============================================================================
#      bash deploy/up.sh
#
#  Script này làm 3 việc mà nếu quên sẽ gây lỗi khó hiểu:
#   1. Kiểm tra có deploy/.env chưa (thiếu là Compose dừng ngay).
#   2. Kiểm tra biến BẮT BUỘC đã điền chưa (rỗng thì báo rõ biến nào).
#   3. Cảnh báo nếu $CFG->wwwroot của Moodle không khớp tên miền đang dùng.
# =============================================================================

set -euo pipefail

cd "$(dirname "$0")"

RED=$'\e[31m'; GREEN=$'\e[32m'; YELLOW=$'\e[33m'; NC=$'\e[0m'
fail() { echo "${RED}✗ $*${NC}" >&2; exit 1; }
ok()   { echo "${GREEN}✓ $*${NC}"; }
warn() { echo "${YELLOW}! $*${NC}"; }

# -----------------------------------------------------------------------------
# 1) File .env
# -----------------------------------------------------------------------------
if [[ ! -f .env ]]; then
    fail "Chưa có deploy/.env. Chạy:  cp deploy/.env.example deploy/.env  rồi điền các giá trị trống."
fi
ok "Đã có deploy/.env"

# -----------------------------------------------------------------------------
# 2) Biến bắt buộc
# -----------------------------------------------------------------------------
# Đọc file trực tiếp thay vì "source" để không làm hỏng shell hiện tại
# (giá trị có dấu cách hoặc ký tự đặc biệt sẽ phá script nếu source).
missing=()
for k in MARIADB_ROOT_PASSWORD MARIADB_PASSWORD JWT_SECRET TOTP_MASTER_KEY_V2 AI_INTERNAL_TOKEN APP_SEED_DEFAULT_PASSWORD; do
    v="$(grep -E "^${k}=" .env | head -1 | cut -d= -f2- || true)"
    [[ -z "${v// }" ]] && missing+=("$k")
done

if [[ ${#missing[@]} -gt 0 ]]; then
    echo
    echo "${RED}Các biến sau đang để trống nhưng bắt buộc phải có:${NC}"
    for k in "${missing[@]}"; do
        case "$k" in
            JWT_SECRET)              hint="openssl rand -base64 48" ;;
            TOTP_MASTER_KEY_V2|AI_INTERNAL_TOKEN) hint="openssl rand -base64 32" ;;
            *)                       hint="openssl rand -base64 24" ;;
        esac
        echo "   - $k   (gợi ý: $hint)"
    done
    echo
    exit 1
fi
ok "Đã điền đủ biến bắt buộc"

# -----------------------------------------------------------------------------
# 3) wwwroot của Moodle
# -----------------------------------------------------------------------------
# Moodle lưu wwwroot vào CSDL lúc cài đặt. Nếu config.php trên đĩa ghi một
# URL khác với URL người dùng mở, Moodle sẽ hiện trang lỗi "Site not found"
# hoặc vòng lặp chuyển hướng. Kiểm tra sớm để không mất thời gian debug.
MOODLE_CFG="../php_moodle_503/moodle-latest-503/moodle/config.php"
if [[ -f "$MOODLE_CFG" ]]; then
    DISK_WWWROOT="$(grep -oE "\\\$CFG->wwwroot\s*=\s*'[^']*'" "$MOODLE_CFG" | head -1 | sed "s/.*'\(.*\)'/\1/" || true)"
    ENV_WWWROOT="$(grep -E '^MOODLE_WWWROOT=' .env | head -1 | cut -d= -f2- || true)"
    if [[ -n "$DISK_WWWROOT" && -n "$ENV_WWWROOT" && "$DISK_WWWROOT" != "$ENV_WWWROOT" ]]; then
        warn "wwwroot của Moodle KHÔNG khớp:"
        echo "      config.php trên đĩa : $DISK_WWWROOT"
        echo "      deploy/.env         : $ENV_WWWROOT"
        echo "      Moodle sẽ báo lỗi hoặc chuyển hướng sai. Sửa MỘT trong hai"
        echo "      cho khớp — nhưng ĐỪNG sửa config.php nếu người khác đang"
        echo "      chỉnh theme; hãy sửa MOODLE_WWWROOT trong .env cho khớp"
        echo "      với config.php hiện tại (xem deploy/README.md)."
        echo
    else
        ok "wwwroot của Moodle khớp ($DISK_WWWROOT)"
    fi
fi

# -----------------------------------------------------------------------------
# 4) Khởi động
# -----------------------------------------------------------------------------
echo
echo "Đang build và khởi động các container (lần đầu có thể mất 5-10 phút)..."
echo

docker compose -f docker-compose.yml up -d --build

echo
ok "Đã khởi động. Kiểm tra trạng thái:"
echo
echo "    docker compose -f deploy/docker-compose.yml ps"
echo
echo "Truy cập (CỔNG BẮT BUỘC có trong URL):"
echo "    http://qbank.local:5176    — QBank Console (Vue 3) + API"
echo "    http://admin.local:8085    — fe_web_admin (Vue cũ) + API"
echo "    http://moodle.local:9000   — Moodle"
echo
echo "Kiểm tra nhanh từng cổng:"
echo "    curl -I http://localhost:5176"
echo "    curl -I http://localhost:8085"
echo "    curl -I http://localhost:9000"
echo
echo "Chưa thêm tên miền vào /etc/hosts?  sudo bash deploy/hosts-setup.sh"
