# =============================================================================
#  Moodle 5.3 — PHP-FPM 8.3
# =============================================================================
#  QUAN TRỌNG — ĐỌC TRƯỚC KHI SỬA FILE NÀY:
#
#  Mã nguồn Moodle KHÔNG được COPY vào image. Nó được BIND MOUNT từ
#  ../php_moodle_503/moodle-latest-503/moodle vào /var/www/moodle.
#
#  Lý do: anh đang sửa theme liên tục. Nếu COPY vào image thì mỗi lần sửa
#  CSS/Mustache phải `docker compose build` lại (vài phút) — không dùng được.
#  Bind mount cho phép sửa file rồi chỉ cần `php admin/cli/purge_caches.php`.
#
#  Hệ quả cần biết:
#   - Image này KHÔNG chứa mã Moodle, chỉ chứa PHP + extension + cấu hình.
#     Vì vậy nó build rất nhanh (vài giây) và không bao giờ "cũ".
#   - Quyền file trên đĩa phải cho phép user trong container ghi vào
#     (moodledata, cache). Xem deploy/README.md mục "Phân quyền Moodle".
# =============================================================================

FROM php:8.3-fpm-alpine

# -----------------------------------------------------------------------------
# Extension PHP bắt buộc của Moodle
# -----------------------------------------------------------------------------
# Danh sách theo run.txt của project (mục 1): mbstring, xml, curl, zip, gd,
# intl, mysqli/pdo_mysql, soap, opcache.
#
# Giải thích từng nhóm:
#   - mbstring: xử lý chuỗi UTF-8 (tiếng Việt) — KHÔNG có thì Moodle báo lỗi
#     ngay ở trang cài đặt.
#   - intl: định dạng ngày/tiền tệ theo ngôn ngữ — cần cho giao diện tiếng Việt.
#   - gd: cắt ảnh thumbnail avatar, ảnh khoá học.
#   - zip: sao lưu/phục hồi khoá học (.mbz là file zip).
#   - pdo_mysql: kết nối MariaDB.
#   - soap: một số plugin (điểm thi, LTI) cần.
#   - opcache: tăng tốc đáng kể, bắt buộc cho production.
RUN apk add --no-cache \
        libpng-dev \
        libjpeg-turbo-dev \
        freetype-dev \
        libzip-dev \
        icu-dev \
        libxml2-dev \
        oniguruma-dev \
    && docker-php-ext-configure gd --with-freetype --with-jpeg \
    && docker-php-ext-install -j"$(nproc)" \
        pdo_mysql \
        mbstring \
        intl \
        gd \
        zip \
        soap \
        opcache \
        xml \
    && apk del libpng-dev libjpeg-turbo-dev freetype-dev libzip-dev icu-dev libxml2-dev oniguruma-dev

# -----------------------------------------------------------------------------
# Cấu hình PHP cho Moodle
# -----------------------------------------------------------------------------
RUN { \
      echo 'memory_limit = 256M'; \
      echo 'upload_max_filesize = 512M'; \
      echo 'post_max_size = 512M'; \
      echo 'max_execution_time = 300'; \
      echo 'max_input_vars = 5000'; \
      echo 'display_errors = Off'; \
      echo 'log_errors = On'; \
      echo 'error_log = /proc/self/fd/2'; \
      # Múi giờ mặc định của PHP. Moodle có cài đặt riêng nhưng PHP khởi tạo
      # cần một giá trị hợp lệ, nếu không sẽ cảnh báo ở mọi trang.
      echo 'date.timezone = Asia/Ho_Chi_Minh'; \
    } > /usr/local/etc/php/conf.d/zz-moodle.ini

# opcache: bật cho production. validate_timestamps=1 để khi sửa mã PHP
# (anh đang sửa theme) là có hiệu lực ngay, không phải restart container.
# Đánh đổi: kiểm tra timestamp mỗi request tốn một chút CPU. Nếu muốn tối ưu
# tuyệt đối thì đặt validate_timestamps=0 và restart container sau khi sửa.
RUN { \
      echo 'opcache.enable = 1'; \
      echo 'opcache.enable_cli = 1'; \
      echo 'opcache.memory_consumption = 192'; \
      echo 'opcache.interned_strings_buffer = 16'; \
      echo 'opcache.max_accelerated_files = 20000'; \
      echo 'opcache.validate_timestamps = 1'; \
      echo 'opcache.revalidate_freq = 2'; \
    } > /usr/local/etc/php/conf.d/zz-opcache.ini

# -----------------------------------------------------------------------------
# PHP-FPM: cho phép NGINX (container khác) kết nối tới
# -----------------------------------------------------------------------------
# Mặc định PHP-FPM chỉ nghe trên 127.0.0.1, nghĩa là chỉ NGINX nằm CÙNG
# container mới gọi được. Ở đây nginx là container riêng nên phải đổi
# listen thành 0.0.0.0 (trong mạng Docker nội bộ).
RUN sed -i 's/^listen = 127.0.0.1:9000/listen = 0.0.0.0:9000/' /usr/local/etc/php-fpm.d/www.conf \
    && { \
         # Header chuyển tiếp: PHP-FPM PHẢI được phép đọc các header này,
         # nếu không Moodle không biết IP thật của sinh viên.
         echo '# Header chuyển tiếp từ reverse proxy'; \
       } >> /usr/local/etc/php-fpm.d/www.conf

# -----------------------------------------------------------------------------
# Công cụ phụ trợ
# -----------------------------------------------------------------------------
# curl: cho admin/cli/* và một số plugin; tini: xử lý tín hiệu đúng cách.
RUN apk add --no-cache curl

# tini làm PID 1: nhận SIGTERM và chuyển cho php-fpm, tránh container
# mất 10 giây mỗi lần `docker compose stop` vì tín hiệu không được xử lý.
RUN apk add --no-cache tini

# -----------------------------------------------------------------------------
# Thư mục làm việc
# -----------------------------------------------------------------------------
# /var/www/moodle  : mã nguồn (bind mount từ repo)
# /var/www/moodledata : dữ liệu (volume riêng, KHÔNG nằm trong webroot)
WORKDIR /var/www/moodle

RUN mkdir -p /var/www/moodledata && chown -R www-data:www-data /var/www/moodledata

# -----------------------------------------------------------------------------
# UID/GID khớp với file trên đĩa host — RẤT QUAN TRỌNG
# -----------------------------------------------------------------------------
# Mã nguồn Moodle được bind mount từ repo. Trên máy dev, thư mục đó thuộc
# user đang đăng nhập (ví dụ uid=1001), KHÔNG phải www-data (uid 82).
#
# Nếu để nguyên www-data: container chạy uid 82, không có quyền ghi vào
# public/theme/boost/style/ -> Moodle KHÔNG COMPILE ĐƯỢC THEME và trang web
# hiện không có CSS (hoặc báo lỗi "Cannot write to theme cache").
#
# Cách xử lý: đặt lại uid/gid của www-data trong container cho khớp với
# chủ sở hữu file trên host, truyền qua build-arg (xem docker-compose.yml,
# có ghi chú cách lấy số đúng).
#
#   MOODLE_UID / MOODLE_GID mặc định 1000 (phổ biến trên Linux desktop).
#   Trên macOS/Windows, Docker Desktop tự ánh xạ quyền nên không cần quan tâm.
ARG MOODLE_UID=1000
ARG MOODLE_GID=1000
RUN deluser www-data 2>/dev/null || true; \
    addgroup -g "${MOODLE_GID}" -S www-data 2>/dev/null || true; \
    adduser -u "${MOODLE_UID}" -G www-data -S -D -H -s /sbin/nologin www-data 2>/dev/null || true; \
    chown -R www-data:www-data /var/www/moodledata

EXPOSE 9000

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["php-fpm", "-F"]
