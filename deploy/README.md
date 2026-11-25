# Triển khai — NGINX + Docker Compose

Hạ tầng cho đồ án tốt nghiệp: **một cổng vào duy nhất, tất cả service nằm sau NGINX**.

---

## Vì sao NGINX nằm ở `deploy/` chứ không trong project con

NGINX phục vụ **đồng thời 4 project**:

| Project | Vai trò | Cổng nội bộ |
|---|---|---|
| `qbank-console-vue3` | Console quản trị chính (Vue 3) | tĩnh |
| `fe_web_admin` | Giao diện quản trị cũ (Vue 3) | tĩnh |
| `be-web-admin` | Backend Spring Boot 3 (JDK 21) | 8080 |
| `server-ai-worker` | Worker AI (Spring Boot, JDK 17) | 8082 |
| `php_moodle_503` | Moodle 5.3 (PHP 8.3) | 9000 |

Nó là **hạ tầng dùng chung**, không thuộc sở hữu của project nào. Đặt trong
`be-web-admin/` thì cấu hình web server của Moodle lại nằm trong thư mục
backend — sai về mặt kiến trúc và rất khó giải thích khi bảo vệ.

Nhưng vẫn **tách bạch trách nhiệm**: mỗi project có đúng một file trong
`nginx/conf.d/`. Sửa cấu hình của Moodle thì chỉ mở `moodle.conf`.

---

## Cấu trúc

```
deploy/
├── docker-compose.yml          # định nghĩa toàn bộ service + mạng
├── .env.example                # mẫu biến môi trường (copy thành .env)
├── up.sh                       # khởi động kèm kiểm tra tiên quyết
├── hosts-setup.sh              # thêm qbank.local / admin.local / moodle.local
├── nginx/
│   ├── Dockerfile              # multi-stage: build 2 SPA rồi gói vào image NGINX
│   ├── nginx.conf              # cấu hình chung: log, gzip, rate-limit
│   ├── conf.d/
│   │   ├── 00-default.conf     # server mặc định + /healthz
│   │   ├── qbank.conf          # qbank.local
│   │   ├── admin.conf          # admin.local
│   │   └── moodle.conf         # moodle.local
│   └── snippets/
│       ├── proxy-common.conf   # header X-Forwarded-* dùng chung
│       └── security-headers.conf
├── backend/
│   ├── Dockerfile.be           # be-web-admin (JDK 21)
│   └── Dockerfile.ai           # server-ai-worker (JDK 17)
├── moodle/
│   └── Dockerfile              # PHP-FPM 8.3 + extension
└── mysql-init/
    └── 01-create-databases.sql # tạo 3 CSDL, chạy 1 lần khi volume trống
```

---

## Kiến trúc mạng

```
Internet
    │
    ▼
┌─────────┐  ← cổng DUY NHẤT mở ra ngoài (:80)
│  nginx  │
└────┬────┘
     │  mạng "edge"
     ├──────────────┬───────────────┐
     ▼              ▼               ▼
be_web_admin    moodle_php      (2 SPA tĩnh, cùng image NGINX)
     │              │
     │  mạng "data" (internal: true — KHÔNG có đường ra Internet)
     ├──────────────┼───────────────┐
     ▼              ▼               ▼
  mariadb        redis          ai_worker  ← nginx KHÔNG với tới
```

**Nguyên tắc:** chỉ `nginx` có `ports:`. Mọi service khác **không có** khai báo
`ports:` nên không thể truy cập từ ngoài máy chủ. Kể cả khi backend bị khai
thác, kẻ tấn công vẫn không có đường vào CSDL.

`ai_worker` cố ý **không** nằm trong mạng `edge`: nó chỉ dùng internal-token
(không xác thực người dùng), nếu lộ ra ngoài là ai cũng gọi được AI của đồ án.

---

## Chạy lần đầu

### 1. Thêm tên miền giả

```bash
sudo bash deploy/hosts-setup.sh
```

Kiểm tra:

```bash
getent hosts qbank.local admin.local moodle.local
```

### 2. Tạo file biến môi trường

```bash
cp deploy/.env.example deploy/.env
```

Điền các giá trị **bắt buộc** (để trống là Compose dừng ngay, cố ý như vậy):

| Biến | Cách tạo |
|---|---|
| `MARIADB_ROOT_PASSWORD` | `openssl rand -base64 24` |
| `MARIADB_PASSWORD` | `openssl rand -base64 24` |
| `JWT_SECRET` | `openssl rand -base64 48` |
| `TOTP_MASTER_KEY_V2` | `openssl rand -base64 32` |
| `AI_INTERNAL_TOKEN` | `openssl rand -base64 32` |
| `APP_SEED_DEFAULT_PASSWORD` | mật khẩu cho 10 tài khoản admin mẫu |

> **`TOTP_MASTER_KEY_V2` phải là Base64 của ĐÚNG 32 byte.** Nếu sai độ dài,
> ứng dụng không khởi động được và thông báo lỗi nằm sâu trong log.

### 3. Lấy UID/GID của thư mục Moodle

```bash
stat -c '%u %g' php_moodle_503/moodle-latest-503/moodle
```

Ghi hai số đó vào `MOODLE_UID` và `MOODLE_GID` trong `deploy/.env`.
Repo này hiện là `1001 1001` (đã đặt làm mặc định); máy Linux khác thường là
`1000 1000`.

> Bỏ qua bước này thì container chạy uid khác chủ sở hữu thư mục → **không ghi
> được cache theme** → Moodle mất CSS. Đây là lỗi phổ biến nhất khi chạy Moodle
> bằng Docker với bind mount.

### 4. Khởi động

```bash
bash deploy/up.sh
```

Hoặc thủ công:

```bash
docker compose -f deploy/docker-compose.yml up -d --build
```

Lần đầu mất **5–10 phút** (tải base image, build 2 SPA, build 2 Spring Boot).

### 5. Kiểm tra

```bash
docker compose -f deploy/docker-compose.yml ps
```

Tất cả phải ở trạng thái `healthy` (trừ `moodle_php` và `moodle_cron` là `running`).

| URL | Nội dung |
|---|---|
| http://qbank.local | QBank Console |
| http://admin.local | fe_web_admin |
| http://moodle.local | Moodle |
| http://qbank.local/api/... | Backend (cùng origin, không CORS) |

---

## Moodle — phần cần lưu ý nhất

### `$CFG->wwwroot` phải khớp tuyệt đối

Moodle lưu `wwwroot` **vào CSDL** lúc cài đặt, không chỉ trong `config.php`.
Nếu URL trên trình duyệt khác với giá trị đã lưu, Moodle hiện trang lỗi
*"Site not found"* hoặc chuyển hướng vòng.

Trong repo hiện tại `config.php` ghi:

```php
$CFG->wwwroot = 'http://localhost:8000';
```

**Tôi không sửa file này** (anh đang chỉnh theme trong cùng thư mục). Có 3 lựa chọn:

**A. Giữ nguyên, chạy Moodle tay như trước** (không qua Docker)
```bash
cd php_moodle_503/moodle-latest-503/moodle
php -S localhost:8000 -t public router.php
```
`deploy/` không ảnh hưởng gì tới cách này. NGINX chỉ phục vụ Vue + API.

**B. Cho Moodle chạy qua NGINX** — đổi `config.php`:
```php
$CFG->wwwroot = 'http://moodle.local';
```
Rồi xoá cache:
```bash
docker compose -f deploy/docker-compose.yml exec moodle_php \
    php /var/www/moodle/admin/cli/purge_caches.php
```

**C. Đổi tên miền trong NGINX cho khớp `localhost:8000`** — không khuyến nghị,
vì `localhost:8000` không đi qua cổng 80 nên phải map `8000:80` và sẽ vướng
`$CFG->wwwroot` không có cổng.

> Nếu chọn B, **báo tôi biết** trước khi tôi commit: tôi sẽ không tự sửa
> `php_moodle_503/` cho tới khi anh xác nhận đã xong phần theme.

### Phân quyền

```bash
# Container ghi vào đây (cache theme, tệp tạm)
sudo chown -R 1001:1001 php_moodle_503/moodle-latest-503/moodle/public/theme
```

Trong đó `1001` là `MOODLE_UID` đã đặt (lấy bằng `stat -c '%u %g'`). Nếu không đúng, Moodle báo:
*"Cannot write to theme cache directory"*.

### Sửa theme xong thì

```bash
docker compose -f deploy/docker-compose.yml exec moodle_php \
    php /var/www/moodle/admin/cli/purge_caches.php
```

Không cần rebuild image — mã nguồn được bind mount, không copy.

---

## Lệnh thường dùng

```bash
cd deploy

# Trạng thái
docker compose ps

# Xem log một service
docker compose logs -f nginx
docker compose logs -f be_web_admin

# Khởi động lại sau khi sửa cấu hình NGINX
docker compose restart nginx

# Kiểm tra cú pháp cấu hình NGINX (không cần restart)
docker compose exec nginx nginx -t

# Build lại sau khi sửa mã backend
docker compose up -d --build be_web_admin

# Dừng (giữ dữ liệu)
docker compose down

# Dừng và XOÁ SẠCH dữ liệu
docker compose down -v
```

---

## Những lỗi đã lường trước và cách tránh

Đây là phần đáng đọc nhất — toàn bộ đều đã được xử lý trong cấu hình, ghi lại
để hiểu **tại sao** cấu hình lại viết như vậy.

### 1. Không có `spring-boot-actuator`

Repo **không** có dependency actuator, nên `/api/actuator/health` trả 404.
Nếu healthcheck gọi endpoint đó thì nó **thất bại mãi mãi** và
`depends_on: condition: service_healthy` làm `docker compose up` treo vô hạn.

→ Đã đổi healthcheck sang: `wget -S` và chấp nhận **bất kỳ mã HTTP nào**
(kể cả 401/403) là dấu hiệu server đang chạy. Chỉ lỗi kết nối mới là chết.

### 2. WebSocket bị cắt sau 60 giây

`application.yaml` có `chat.ws.idle-timeout-seconds: 50` kèm ghi chú
*"nhỏ hơn mốc 60s của Nginx"*. Mặc định `proxy_read_timeout` của NGINX là 60s
→ hai bên tranh nhau đóng kết nối, log đầy lỗi 499/1006.

→ Đã đặt `proxy_read_timeout 3600s` cho riêng location WebSocket, kèm
`proxy_buffering off` (nếu buffer, tin nhắn chat bị giữ lại và có độ trễ).

### 3. `X-Forwarded-For` và thế đánh đổi bảo mật

Backend bật `forward-headers-strategy: native` để lấy IP thật. Nhưng header
này **giả mạo được**: nếu client gọi thẳng cổng 8080, nó tự đặt
`X-Forwarded-For: 1.2.3.4` và làm sai audit log + vượt rate-limit.

→ Cổng backend **không mở ra ngoài**. Chỉ `nginx` gọi tới được qua mạng nội bộ.

### 4. `add_header` không cộng dồn

Trong NGINX, nếu một `location` con tự khai báo `add_header` thì **toàn bộ**
`add_header` của khối cha bị mất, không phải cộng thêm.

→ Các header bảo mật được gói trong `snippets/security-headers.conf` và
`include` lại ở từng nơi, không khai báo một lần ở `http{}` rồi tưởng là còn.

### 5. Cache `index.html` gây trắng trang

Nếu cache `index.html`, sau mỗi lần deploy người dùng vẫn chạy bản cũ trỏ tới
các file `assets/*.js` đã bị xoá → trắng trang, phải Ctrl+Shift+R.

→ `index.html` đặt `Cache-Control: no-store`; `assets/*` cache 1 năm
(`immutable`) vì Vite sinh tên file có hash nội dung.

### 6. Hai SPA dùng router khác nhau

- `qbank-console-vue3` → `createWebHashHistory()`: phần sau `#` không lên server.
- `fe_web_admin` → `createWebHistory()`: **cần** `try_files ... /index.html`.

→ Mỗi file `conf.d/` xử lý riêng. Dùng chung một mẫu thì một trong hai sẽ trắng
trang khi F5.

### 7. `moodle_cron` chạy bằng root

Image `php-fpm` chạy entrypoint bằng root rồi **tự drop** xuống `www-data`.
Nhưng `moodle_cron` override entrypoint sang `sh -c`, nên **không có bước drop**
→ `php cron.php` chạy bằng root → file cron tạo ra thuộc root → web (www-data)
không ghi/đọc được.

→ Đã thêm `user: www-data` cho service `moodle_cron`.

### 8. `location` lồng nhau

Các `location` con (`= /api/mobile/chat/ws`, regex `^/api/.../auth`) nằm trong
`location /api/` là **cấu hình không hợp lệ** trong NGINX: khi đã khớp vào
location tiền tố, NGINX dừng tìm kiếm, các location con bên trong bị bỏ qua.

→ **Đã sửa**: cả 3 file dùng location phẳng. Xem ghi chú đầu `qbank.conf`.

---

## Kiểm chứng đã làm

Tôi **không có `nginx`, `docker` hay `php`** trong môi trường phát triển nên
chưa chạy được `nginx -t` thật. Những gì đã kiểm tra tự động:

| Hạng mục | Kết quả |
|---|---|
| YAML của `docker-compose.yml` | hợp lệ, parse được |
| Số service có `ports:` | đúng **1** (chỉ nginx) |
| Ngoặc `{}` cân bằng trong 7 file cấu hình | ✓ |
| Mọi `include` snippet trỏ tới file có thật | ✓ |
| Mọi `proxy_pass` / `fastcgi_pass` trỏ tới service có thật | ✓ |
| Không có `location` lồng trong `location` | ✓ |
| `bash -n` cho 2 script | ✓ |
| `qbank-console-vue3` build được | ✓ 613ms |
| `fe_web_admin` build được | ✓ 1.71s |

**Còn phải chạy khi anh có Docker:** `docker compose build` và `nginx -t`.
Đó là bước kiểm chứng cuối cùng tôi không thể làm thay.

---

## Học được gì từ cấu hình này (dùng cho báo cáo)

Các khái niệm DevOps thể hiện được trong đồ án này:

| Khái niệm | Thể hiện ở đâu |
|---|---|
| **Reverse proxy** | NGINX nhận mọi request, chuyển tiếp tới backend |
| **Single origin** | CORS rút từ 6 origin còn 2; WebSocket origin cũng gọn hơn |
| **Static asset serving** | Vue build ra file tĩnh, NGINX phục vụ trực tiếp |
| **Cache control** | `index.html` no-store, `assets/*` immutable 1 năm |
| **Compression** | gzip cho text/CSS/JS/JSON, không nén ảnh |
| **TLS termination** | chỗ để bật HTTPS (xem bên dưới) |
| **Rate limiting** | `limit_req` chặn dò mật khẩu ở tầng proxy (không tốn CPU BCrypt) |
| **Health check** | `/healthz` + healthcheck của từng container |
| **Network segmentation** | 2 mạng `edge` / `data`, `internal: true` cho data |
| **Least privilege** | container chạy user riêng, không root |
| **Zero-downtime reload** | `nginx -s reload` không cắt kết nối đang mở |
| **Multi-stage build** | image cuối chỉ chứa JRE + jar, không có mã nguồn |
| **Layer caching** | copy `package.json`/`build.gradle` trước mã nguồn |

### Muốn ấn tượng hơn: cân bằng tải

Thứ duy nhất **demo trực tiếp được** và thường ăn điểm nhất:

```nginx
upstream be_pool {
    server be_web_admin_1:8080;
    server be_web_admin_2:8080;
}
```

Rồi trong compose chạy 2 instance (cùng image, khác `container_name`), tắt một
cái bằng `docker compose stop be_web_admin_2` và cho thấy hệ thống vẫn chạy.
Đây là **thứ duy nhất chứng minh được bằng demo** — đáng làm nếu còn thời gian.

---

## Bật HTTPS sau này

Khi có tên miền thật:

1. Sửa `nginx.conf`, thêm vào `server` block:
   ```nginx
   listen 443 ssl;
   ssl_certificate     /etc/nginx/certs/fullchain.pem;
   ssl_certificate_key /etc/nginx/certs/privkey.pem;
   ssl_protocols TLSv1.2 TLSv1.3;
   ```
2. Thêm `server` block chuyển hướng:
   ```nginx
   server {
       listen 80;
       server_name qbank.local;
       return 301 https://$host$request_uri;
   }
   ```
3. Thêm header HSTS **sau khi** đã chắc HTTPS chạy ổn:
   ```nginx
   add_header Strict-Transport-Security "max-age=31536000" always;
   ```

Dùng chứng chỉ tự ký cho demo thì trình duyệt cảnh báo; Let's Encrypt chỉ dùng
được với tên miền thật trỏ về máy chủ.
