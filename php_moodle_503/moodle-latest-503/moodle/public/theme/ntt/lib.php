<?php
// This file is part of Moodle - http://moodle.org/
//
// Moodle is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// Moodle is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with Moodle.  If not, see <http://www.gnu.org/licenses/>.

/**
 * Theme NTT lib: ghép SCSS (preset Boost + phần của NTT).
 *
 * @package    theme_ntt
 * @copyright  2026 NTT
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */

defined('MOODLE_INTERNAL') || die();

/**
 * SCSS chính: preset mặc định của Boost, rồi tới scss/post.scss của NTT (đè lên sau cùng).
 *
 * Đường dẫn @import của trình biên dịch gồm theme/ntt/scss rồi theme/boost/scss (Moodle tự thêm khi theme có parents).
 *
 * @param theme_config $theme
 * @return string
 */
function theme_ntt_get_main_scss_content($theme) {
    global $CFG;

    $scss = file_get_contents($CFG->dirroot . '/theme/boost/scss/preset/default.scss');
    $scss .= "\n" . file_get_contents($CFG->dirroot . '/theme/ntt/scss/post.scss');
    return $scss;
}

/**
 * SCSS đặt TRƯỚC mọi thứ: bảng màu, phông chữ và biến Bootstrap (scss/pre.scss).
 * Callback của Boost (màu thương hiệu, SCSS thô trong trang cài đặt) đã chạy trước callback này.
 *
 * @param theme_config $theme
 * @return string
 */
function theme_ntt_get_pre_scss($theme) {
    global $CFG;

    return file_get_contents($CFG->dirroot . '/theme/ntt/scss/pre.scss');
}

/**
 * SCSS thêm vào cuối (sau cả phần của Boost): hiện chưa cần, để chỗ cho các tinh chỉnh theo trang cài đặt.
 *
 * @param theme_config $theme
 * @return string
 */
function theme_ntt_get_extra_scss($theme) {
    global $CFG;

    $file = $CFG->dirroot . '/theme/ntt/scss/extra.scss';
    return is_readable($file) ? file_get_contents($file) : '';
}

/**
 * CSS dựng sẵn dùng khi biên dịch SCSS lỗi: dùng lại của Boost.
 *
 * @return string
 */
function theme_ntt_get_precompiled_css() {
    global $CFG;

    return file_get_contents($CFG->dirroot . '/theme/boost/style/moodle.css');
}
