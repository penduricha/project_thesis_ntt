#!/bin/bash

# ==============================================================================
# Script cài đặt Moodle tự động dùng PHP 8.4
# Tên cơ sở dữ liệu: moodle_503 (utf8mb4_unicode_ci)
# ==============================================================================

set -e

PHP_VER="8.4"

echo "[1/4] Kiểm tra và thiết lập phiên bản PHP $PHP_VER làm mặc định..."
sudo update-alternatives --set php /usr/bin/php${PHP_VER} || true
php -v

echo "[2/4] Cấu hình max_input_vars = 10000 trong php.ini (PHP $PHP_VER)..."
CLI_INI="/etc/php/$PHP_VER/cli/php.ini"

if [ -f "$CLI_INI" ]; then
    if grep -q "^max_input_vars" "$CLI_INI"; then
        sudo sed -i 's/^max_input_vars =.*/max_input_vars = 10000/' "$CLI_INI"
    elif grep -q "^;max_input_vars" "$CLI_INI"; then
        sudo sed -i 's/^;max_input_vars =.*/max_input_vars = 10000/' "$CLI_INI"
    else
        echo "max_input_vars = 10000" | sudo tee -a "$CLI_INI" > /dev/null
    fi
    echo " -> Đã cập nhật thành công max_input_vars = 10000 tại $CLI_INI"
fi

echo "[3/4] Khởi tạo Cơ sở dữ liệu MariaDB 'moodle_503' (utf8mb4_unicode_ci) và thư mục moodledata..."
sudo systemctl enable mariadb || sudo systemctl enable mysql
sudo systemctl start mariadb || sudo systemctl start mysql

# Kết nối MariaDB với port 3306, user root, pass 1234
mariadb -u root -p'1234' -h 127.0.0.1 -P 3306 -e "CREATE DATABASE IF NOT EXISTS moodle_503 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" || \
mysql -u root -p'1234' -h 127.0.0.1 -P 3306 -e "CREATE DATABASE IF NOT EXISTS moodle_503 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

sudo mkdir -p /var/www/moodledata
sudo chown -R www-data:www-data /var/www/moodledata
sudo chmod -R 777 /var/www/moodledata

echo "[4/4] Tiến hành cài đặt Moodle và tạo config.php tự động qua CLI..."
rm -f config.php

php admin/cli/install.php \
  --agree-license \
  --lang="en" \
  --wwwroot="http://localhost:8000" \
  --dataroot="/var/www/moodledata" \
  --dbtype="mariadb" \
  --dbhost="127.0.0.1" \
  --dbname="moodle_503" \
  --dbuser="root" \
  --dbpass="1234" \
  --dbport="3306" \
  --adminuser="admin" \
  --adminpass='Admin123!@#' \
  --adminemail="admin@example.com" \
  --fullname="Hệ Thống Học Trực Tuyến Moodle 503" \
  --shortname="Moodle503" \
  --non-interactive

php admin/cli/cron.php

php admin/cli/purge_caches.php

echo "=================================================================="
echo " HOÀN TẤT CÀI ĐẶT MOODLE VỚI PHP 8.4 THÀNH CÔNG!"
echo " Bạn có thể khởi động server bằng lệnh:"
echo " php -S localhost:8000 -t public"
echo "=================================================================="