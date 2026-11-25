# =============================================================================
#  Image NGINX chứa luôn bản build của 2 ứng dụng Vue
# =============================================================================
#  Vì sao gộp chứ không tách container web riêng cho từng SPA:
#  - Hai SPA chỉ là file tĩnh, không cần runtime. Gộp vào cùng image NGINX
#    giảm số container phải quản lý và giảm một tầng proxy.
#  - Vẫn tách bạch về cấu hình: mỗi SPA có một server block riêng trong
#    conf.d/ nên vẫn đúng nguyên tắc "mỗi project một cấu hình".
#
#  Toàn bộ build dùng multi-stage:
#    1. node:22-alpine  -> build qbank-console-vue3
#    2. node:22-alpine  -> build fe_web_admin
#    3. nginx:1.27-alpine -> chỉ copy dist + conf, KHÔNG copy node_modules.
#  Nhờ vậy image cuối chỉ vài chục MB thay vì hàng trăm MB.
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1 — QBank Console (Vue 3, hash router)
# -----------------------------------------------------------------------------
FROM node:22-alpine AS build-qbank

WORKDIR /app

# Copy riêng package*.json trước để tận dụng cache của Docker:
# khi chỉ sửa mã nguồn, bước npm ci không chạy lại.
COPY qbank-console-vue3/package.json qbank-console-vue3/package-lock.json ./

# npm ci (không dùng npm install): cài đúng phiên bản trong lock file và
# KHÔNG tự nâng cấp -> bản build ở máy và trong Docker giống hệt nhau.
RUN npm ci --no-audit --no-fund

COPY qbank-console-vue3/ ./

# Đường dẫn API tương đối. Vì trang web và API cùng origin (đều qua NGINX),
# KHÔNG cần http://localhost:8080 trong bản production: trình duyệt tự ghép
# "/api/web" vào host hiện tại. Đây chính là lợi ích lớn nhất của reverse proxy.
ARG VITE_API_BASE_URL=/api/web
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm run build

# -----------------------------------------------------------------------------
# Stage 2 — fe_web_admin (Vue 3, history router)
# -----------------------------------------------------------------------------
FROM node:22-alpine AS build-admin

WORKDIR /app

COPY fe_web_admin/package.json fe_web_admin/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY fe_web_admin/ ./

ARG VITE_API_BASE_URL=/api/web
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm run build

# -----------------------------------------------------------------------------
# Stage 3 — image cuối
# -----------------------------------------------------------------------------
FROM nginx:1.27-alpine

# Bản build tĩnh
COPY --from=build-qbank /app/dist /usr/share/nginx/html/qbank
COPY --from=build-admin /app/dist /usr/share/nginx/html/admin

# Cấu hình
COPY deploy/nginx/nginx.conf              /etc/nginx/nginx.conf
COPY deploy/nginx/conf.d/                 /etc/nginx/conf.d/
COPY deploy/nginx/snippets/               /etc/nginx/snippets/

# Không dùng file default.conf của image: nó sẽ chiếm server block mặc định
# và trả trang "Welcome to nginx" khi truy cập bằng IP.
RUN rm -f /etc/nginx/conf.d/default.conf

# Kiểm tra cú pháp NGAY LÚC BUILD: sai cấu hình thì build thất bại, không
# phải đợi tới khi chạy container mới phát hiện.
RUN nginx -t

EXPOSE 80

# healthcheck: dùng để docker compose biết khi nào NGINX sẵn sàng.
# --spider: chỉ kiểm tra kết nối, không tải toàn bộ trang.
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://127.0.0.1/healthz || exit 1
