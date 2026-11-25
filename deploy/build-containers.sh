#!/usr/bin/env bash
# =============================================================================
#  build-containers.sh — quản lý vòng đời container của đồ án
# =============================================================================
#  CÁCH DÙNG
#      chmod +x build-containers.sh
#
#      ./build-containers.sh up        # build và chạy tất cả container
#      ./build-containers.sh ps        # xem trạng thái
#      ./build-containers.sh logs      # xem log Spring Boot (be_web_admin)
#      ./build-containers.sh down      # dừng, GIỮ dữ liệu
#      ./build-containers.sh clean     # dừng và XOÁ dữ liệu DB + Redis
#
#  CÁC LỆNH PHỤ (thêm vì sẽ cần khi demo)
#      ./build-containers.sh logs nginx      # log service khác
#      ./build-containers.sh logs moodle_php
#      ./build-containers.sh restart <svc>   # khởi động lại 1 service
#      ./build-containers.sh shell <svc>     # vào trong container
#      ./build-containers.sh check           # kiểm tra nhanh mọi cổng
#      ./build-containers.sh rebuild <svc>   # build lại 1 service (sửa code)
#
#  GHI CHÚ VỀ SỐ CONTAINER: cấu hình này có **7 service**, không phải 5:
#      nginx, mariadb, redis, be_web_admin, ai_worker, moodle_php, moodle_cron
#  (moodle_cron chạy cùng image với moodle_php nhưng là container riêng, vì
#  Moodle BẮT BUỘC có cron hoạt động — thiếu nó thì sinh viên nộp bài xong
#  không thấy điểm cập nhật, lỗi rất khó tìm.)
# =============================================================================

set -euo pipefail

# Luôn chạy từ thư mục chứa script, dù gọi từ đâu.
# Nhờ vậy `./build-containers.sh` chạy được kể cả khi đứng ở gốc repo.
cd "$(dirname "$0")"

RED=$'\e[31m'; GREEN=$'\e[32m'; YELLOW=$'\e[33m'
BLUE=$'\e[34m'; BOLD=$'\e[1m'; NC=$'\e[0m'

COMPOSE_FILE="docker-compose.yml"
DC=(docker compose -f "$COMPOSE_FILE")

ok()   { echo "${GREEN}✓${NC} $*"; }
warn() { echo "${YELLOW}!${NC} $*"; }
fail() { echo "${RED}✗${NC} $*" >&2; exit 1; }
info() { echo "${BLUE}→${NC} $*"; }

# =============================================================================
#  Kiểm tra tiên quyết
# =============================================================================
require_docker() {
    command -v docker >/dev/null 2>&1 \
        || fail "Chưa cài docker. Xem https://docs.docker.com/engine/install/"
    docker compose version >/dev/null 2>&1 \
        || fail "Không có 'docker compose' (plugin v2). Bản 'docker-compose' (v1, có gạch nối) KHÔNG dùng được với file này."
    docker info >/dev/null 2>&1 \
        || fail "Docker daemon chưa chạy. Bật Docker Desktop hoặc: sudo systemctl start docker"
}

# Kiểm tra .env và các biến bắt buộc — lặp lại logic của up.sh để script này
# dùng độc lập được (không phải nhớ chạy up.sh trước).
require_env() {
    [[ -f .env ]] \
        || fail "Chưa có deploy/.env.  Chạy:  cp .env.example .env   rồi điền các giá trị trống."

    local missing=() k v hint
    # MARIADB_ROOT_PASSWORD không còn dùng (xem ghi chú trong docker-compose.yml).
    for k in MARIADB_PASSWORD JWT_SECRET TOTP_MASTER_KEY_V2 AI_INTERNAL_TOKEN APP_SEED_DEFAULT_PASSWORD; do
        v="$(grep -E "^${k}=" .env | head -1 | cut -d= -f2- || true)"
        [[ -z "${v// }" ]] && missing+=("$k")
    done

    if [[ ${#missing[@]} -gt 0 ]]; then
        echo
        echo "${RED}Các biến sau đang trống nhưng bắt buộc phải có:${NC}"
        for k in "${missing[@]}"; do
            case "$k" in
                JWT_SECRET)                          hint="openssl rand -base64 48" ;;
                TOTP_MASTER_KEY_V2|AI_INTERNAL_TOKEN) hint="openssl rand -base64 32" ;;
                *)                                    hint="openssl rand -base64 24" ;;
            esac
            echo "   - $k   (gợi ý: $hint)"
        done
        echo
        echo "Sửa file:  nano deploy/.env"
        echo
        exit 1
    fi
}

# Đọc một biến từ .env mà không 'source' (giá trị có ký tự đặc biệt sẽ phá shell).
env_val() { grep -E "^$1=" .env | head -1 | cut -d= -f2- || true; }

# =============================================================================
#  up — build và chạy
# =============================================================================
cmd_up() {
    require_docker
    require_env

    local build_flag=""
    # --build chỉ khi có --rebuild hoặc lần đầu. Mặc định vẫn build để chắc
    # chắn image khớp mã nguồn: build lại rất nhanh nhờ layer cache.
    build_flag="--build"

    echo
    echo "${BOLD}Build và khởi động 7 container...${NC}"
    echo "  Lần đầu mất 5-10 phút (tải base image + build 2 SPA + 2 Spring Boot)."
    echo "  Lần sau chỉ vài giây nhờ cache."
    echo

    "${DC[@]}" up -d $build_flag

    echo
    echo "${BOLD}Đang chờ các service healthy...${NC}"
    # Chờ tối đa 180s. MariaDB + 2 Spring Boot khởi động chậm, nhất là lần đầu
    # khi Hibernate tạo bảng và seed 500 câu hỏi.
    local waited=0 limit=180
    while (( waited < limit )); do
        local started healthy
        started=$("${DC[@]}" ps --services --filter status=running 2>/dev/null | wc -l || echo 0)
        if "${DC[@]}" ps --format '{{.Name}} {{.Health}}' 2>/dev/null | grep -q "starting"; then
            printf '\r  đang khởi động... %ds' "$waited"
            sleep 5; waited=$((waited + 5)); continue
        fi
        break
    done
    echo -e "\r                              "

    cmd_ps
    cmd_urls
}

# =============================================================================
#  ps — trạng thái
# =============================================================================
cmd_ps() {
    require_docker
    echo
    echo "${BOLD}Trạng thái container${NC}"
    "${DC[@]}" ps
    echo
    # Cảnh báo nếu có container thoát — 'ps' một mình không đủ nổi bật.
    local exited
    exited=$("${DC[@]}" ps -a --filter status=exited --format '{{.Name}}' 2>/dev/null || true)
    if [[ -n "$exited" ]]; then
        warn "Có container đã thoát:"
        echo "$exited" | sed 's/^/     /'
        echo "     Xem log:  ./build-containers.sh logs <tên_service>"
    fi
}

cmd_urls() {
    local qbank admin moodle
    qbank="$(env_val QBANK_HOST_PORT 2>/dev/null || true)"; qbank="${qbank:-5176}"
    admin="$(env_val ADMIN_HOST_PORT 2>/dev/null || true)"; admin="${admin:-8085}"
    moodle="$(env_val MOODLE_HOST_PORT 2>/dev/null || true)"; moodle="${moodle:-8001}"
    local mariadb_p redis_p
    mariadb_p="$(env_val MARIADB_HOST_PORT 2>/dev/null || true)"; mariadb_p="${mariadb_p:-3308}"
    redis_p="$(env_val REDIS_HOST_PORT 2>/dev/null || true)"; redis_p="${redis_p:-6381}"

    echo
    echo "${BOLD}Địa chỉ truy cập${NC}"
    echo "   ${GREEN}http://qbank.local:${qbank}${NC}     QBank Console + API"
    echo "   ${GREEN}http://admin.local:${admin}${NC}     fe_web_admin + API"
    echo "   ${GREEN}http://moodle.local:${moodle}${NC}    Moodle"
    echo
    echo "   MariaDB 127.0.0.1:${mariadb_p} (user root)   Redis 127.0.0.1:${redis_p}"
    echo
    echo "   Chưa thêm tên miền?  sudo bash hosts-setup.sh"
}

# =============================================================================
#  logs — mặc định be_web_admin (Spring Boot)
# =============================================================================
cmd_logs() {
    require_docker
    local svc="${1:-be_web_admin}"
    info "Log của ${BOLD}${svc}${NC} — Ctrl+C để thoát"
    echo
    "${DC[@]}" logs -f --tail=100 "$svc"
}

# =============================================================================
#  down — dừng, GIỮ dữ liệu
# =============================================================================
cmd_down() {
    require_docker
    info "Dừng container (dữ liệu CSDL và Redis được GIỮ lại)"
    "${DC[@]}" down
    ok "Đã dừng. Dữ liệu vẫn còn — chạy 'up' để tiếp tục với dữ liệu cũ."
}

# =============================================================================
#  clean — dừng và XOÁ dữ liệu
# =============================================================================
cmd_clean() {
    require_docker
    echo
    echo "${RED}${BOLD}THAO TÁC NÀY XOÁ VĨNH VIỄN:${NC}"
    echo "   - CSDL ntt_university, moodle_503, ai_worker"
    echo "   - Toàn bộ dữ liệu Redis"
    echo "   - Dữ liệu Moodle (moodledata: bài làm, file tải lên, cache)"
    echo
    echo "   Mã nguồn trong repo KHÔNG bị ảnh hưởng."
    echo
    read -r -p "Gõ chính xác 'xoa' để xác nhận: " answer
    echo
    if [[ "$answer" != "xoa" ]]; then
        warn "Đã huỷ. Không xoá gì."
        exit 0
    fi

    info "Dừng và xoá volume..."
    "${DC[@]}" down -v
    ok "Đã xoá sạch. Lần 'up' sau sẽ tạo CSDL mới từ đầu (chạy mysql-init)."
}

# =============================================================================
#  restart / shell / check / rebuild
# =============================================================================
cmd_restart() {
    require_docker
    local svc="${1:-}"
    [[ -n "$svc" ]] || fail "Cần tên service. Ví dụ: ./build-containers.sh restart nginx"
    info "Khởi động lại ${BOLD}${svc}${NC}"
    "${DC[@]}" restart "$svc"
    ok "Xong"
}

cmd_shell() {
    require_docker
    local svc="${1:-}"
    [[ -n "$svc" ]] || fail "Cần tên service. Ví dụ: ./build-containers.sh shell mariadb"
    # mariadb và redis không có bash, dùng sh; các image khác cũng có sh.
    info "Vào shell của ${BOLD}${svc}${NC} (gõ 'exit' để ra)"
    "${DC[@]}" exec "$svc" sh
}

cmd_check() {
    require_docker
    echo
    echo "${BOLD}Kiểm tra cổng${NC}"
    local qbank admin moodle
    qbank="$(env_val QBANK_HOST_PORT 2>/dev/null || true)"; qbank="${qbank:-5176}"
    admin="$(env_val ADMIN_HOST_PORT 2>/dev/null || true)"; admin="${admin:-8085}"
    moodle="$(env_val MOODLE_HOST_PORT 2>/dev/null || true)"; moodle="${moodle:-8001}"

    local code
    for entry in "qbank:$qbank" "admin:$admin" "moodle:$moodle"; do
        local name="${entry%%:*}" port="${entry##*:}"
        # -s: im lặng, -o /dev/null: bỏ nội dung, -w: chỉ in mã HTTP
        # --max-time 5: không treo nếu cổng bị chặn
        code=$(curl -s -o /dev/null --max-time 5 -w '%{http_code}' "http://localhost:${port}/" 2>/dev/null || echo "000")
        if [[ "$code" == "000" ]]; then
            printf '  %s %-8s :%-5s  %s\n' "${RED}✗${NC}" "$name" "$port" "không kết nối được"
        elif [[ "$code" =~ ^[23] ]]; then
            printf '  %s %-8s :%-5s  HTTP %s\n' "${GREEN}✓${NC}" "$name" "$port" "$code"
        else
            printf '  %s %-8s :%-5s  HTTP %s\n' "${YELLOW}!${NC}" "$name" "$port" "$code"
        fi
    done

    echo
    echo "${BOLD}Kiểm tra CSDL / Redis${NC}"
    if "${DC[@]}" exec -T mariadb mariadb -uroot -p"$(env_val MARIADB_PASSWORD)" -e 'SHOW DATABASES;' >/dev/null 2>&1; then
        printf '  %s MariaDB  danh sách CSDL:\n' "${GREEN}✓${NC}"
        "${DC[@]}" exec -T mariadb mariadb -uroot -p"$(env_val MARIADB_PASSWORD)" -N -e 'SHOW DATABASES;' 2>/dev/null \
            | grep -vE '^(information_schema|performance_schema|mysql|sys)$' | sed 's/^/       /'
    else
        printf '  %s MariaDB  không truy vấn được\n' "${RED}✗${NC}"
    fi

    if "${DC[@]}" exec -T redis redis-cli -a "$(env_val REDIS_PASSWORD)" ping 2>/dev/null | grep -q PONG; then
        printf '  %s Redis    PONG\n' "${GREEN}✓${NC}"
        "${DC[@]}" exec -T redis redis-cli -a "$(env_val REDIS_PASSWORD)" info keyspace 2>/dev/null \
            | grep -E '^db[0-9]+:' | sed 's/^/       /' || echo "       (chưa có key nào)"
    else
        printf '  %s Redis    không trả lời\n' "${RED}✗${NC}"
    fi
    echo
}

cmd_rebuild() {
    require_docker
    local svc="${1:-}"
    [[ -n "$svc" ]] || fail "Cần tên service. Ví dụ: ./build-containers.sh rebuild be_web_admin"
    info "Build lại ${BOLD}${svc}${NC} và khởi động lại"
    "${DC[@]}" up -d --build "$svc"
    ok "Xong"
}

# =============================================================================
#  Điều hướng
# =============================================================================
usage() {
    # Lấy phần đầu file (khối comment mô tả) và bỏ dấu "# " ở đầu mỗi dòng.
    # Dải 5..26 là phần thân khối comment mô tả ở đầu file.
    sed -n '5,26p' "$0" | sed -E 's/^# ?//'
}

case "${1:-}" in
    up)       cmd_up ;;
    ps|status) cmd_ps ;;
    logs)     cmd_logs "${2:-}" ;;
    down)     cmd_down ;;
    clean)    cmd_clean ;;
    restart)  cmd_restart "${2:-}" ;;
    shell|exec) cmd_shell "${2:-}" ;;
    check)    cmd_check ;;
    rebuild)  cmd_rebuild "${2:-}" ;;
    -h|--help|help) usage ;;
    "")
        usage
        exit 1
        ;;
    *)
        fail "Không hiểu lệnh '$1'. Chạy './build-containers.sh --help' để xem danh sách."
        ;;
esac
