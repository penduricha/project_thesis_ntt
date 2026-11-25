# =============================================================================
#  be-web-admin — Spring Boot 3, JDK 21
# =============================================================================
#  Multi-stage: stage 1 dùng JDK đầy đủ + Gradle để build, stage 2 chỉ giữ
#  JRE + file jar. Image cuối nhỏ hơn khoảng 3 lần và không chứa mã nguồn.
#
#  Vì sao dùng `gradle build` chứ không `./gradlew`:
#  gradlew cần tải bản Gradle về (~150MB) mỗi lần build. Dùng image
#  gradle:8.10-jdk21 có sẵn Gradle, và mount cache volume cho ~/.gradle nên
#  lần build thứ hai chỉ mất vài giây.
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1 — build
# -----------------------------------------------------------------------------
FROM gradle:8.10-jdk21 AS build

WORKDIR /build

# Copy riêng file cấu hình build trước để tận dụng layer cache: khi chỉ sửa
# mã Java, bước tải dependency không chạy lại.
# Lưu ý: dự án dùng Kotlin DSL (.gradle.kts) — KHÔNG có bản .gradle.
# Không viết "COPY a* b*" kiểu glob rộng: nếu một mẫu không khớp file nào,
# Docker báo lỗi "no source files were specified".
COPY be-web-admin/settings.gradle.kts be-web-admin/build.gradle.kts /build/

# Cài dependency trước (tách layer để cache).
# `|| true`: nếu bước này lỗi mạng thì vẫn còn cơ hội ở bước build sau,
# thay vì hỏng cả image vì một lần tải dependency thất bại.
RUN gradle dependencies --no-daemon --console=plain -q || true

# Copy toàn bộ mã nguồn rồi build.
COPY be-web-admin/src /build/src

# -x test: bỏ qua test trong bước đóng gói image.
#   Lý do: test cần MariaDB/Redis đã sẵn sàng và mất vài phút; việc chạy
#   test nên làm ở bước CI `./gradlew build` trên máy dev, không phải mỗi
#   lần `docker compose up`. Muốn chạy test trong build thì bỏ tham số này.
RUN gradle bootJar --no-daemon --console=plain -x test

# -----------------------------------------------------------------------------
# Stage 2 — runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# Chạy bằng user riêng, không dùng root: nếu ứng dụng bị chiếm quyền điều
# khiển thì kẻ tấn công cũng không có quyền root trong container.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

WORKDIR /app

# Copy đúng một file jar. Dùng wildcard để không phụ thuộc tên phiên bản.
COPY --from=build /build/build/libs/*.jar app.jar

EXPOSE 8080

# -XX:MaxRAMPercentage thay cho -Xmx cố định: container được cấp bao nhiêu
# RAM thì JVM tự dùng 75% số đó. Đặt -Xmx512m cứng sẽ lãng phí khi máy có
# nhiều RAM và OutOfMemory khi máy ít RAM.
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
