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
 * Theme NTT config: theme con của Boost.
 *
 * Không khai báo lại layout, template hay JavaScript: mọi thứ kế thừa từ Boost (kể cả bản drawers.php đã chỉnh trong
 * repo). Theme chỉ bổ sung SCSS (biến màu/phông trước Bootstrap, quy tắc ghi đè sau cùng).
 *
 * @package    theme_ntt
 * @copyright  2026 NTT
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */

defined('MOODLE_INTERNAL') || die();

require_once(__DIR__ . '/lib.php');

$THEME->name = 'ntt';
$THEME->parents = ['boost'];
$THEME->sheets = [];
$THEME->editor_sheets = [];
$THEME->usefallback = true;
$THEME->scss = function($theme) {
    return theme_ntt_get_main_scss_content($theme);
};
// Chạy SAU callback của Boost (Moodle gọi lần lượt theme cha rồi theme con).
$THEME->prescsscallback = 'theme_ntt_get_pre_scss';
$THEME->extrascsscallback = 'theme_ntt_get_extra_scss';
// Nếu biên dịch SCSS lỗi thì quay về CSS dựng sẵn của Boost thay vì trang không có CSS.
$THEME->precompiledcsscallback = 'theme_ntt_get_precompiled_css';
$THEME->rendererfactory = 'theme_overridden_renderer_factory';
$THEME->iconsystem = \core\output\icon_system::FONTAWESOME;
$THEME->haseditswitch = true;
$THEME->usescourseindex = true;
$THEME->activityheaderconfig = [
    'notitle' => true,
];
