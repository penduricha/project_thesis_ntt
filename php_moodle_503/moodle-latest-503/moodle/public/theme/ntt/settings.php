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
 * Theme NTT settings: màu thương hiệu và SCSS thô (callback của Boost tự đọc ba khoá này cho theme con).
 *
 * @package    theme_ntt
 * @copyright  2026 NTT
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */

defined('MOODLE_INTERNAL') || die();

if ($ADMIN->fulltree) {
    // Màu thương hiệu: để trống thì dùng xanh #004b87 của bộ nhận diện.
    $setting = new admin_setting_configcolourpicker('theme_ntt/brandcolor',
        get_string('brandcolor', 'theme_ntt'), get_string('brandcolor_desc', 'theme_ntt'), '');
    $setting->set_updatedcallback('theme_reset_all_caches');
    $settings->add($setting);

    $setting = new admin_setting_scsscode('theme_ntt/scsspre',
        get_string('rawscsspre', 'theme_ntt'), get_string('rawscsspre_desc', 'theme_ntt'), '', PARAM_RAW);
    $setting->set_updatedcallback('theme_reset_all_caches');
    $settings->add($setting);

    $setting = new admin_setting_scsscode('theme_ntt/scss',
        get_string('rawscss', 'theme_ntt'), get_string('rawscss_desc', 'theme_ntt'), '', PARAM_RAW);
    $setting->set_updatedcallback('theme_reset_all_caches');
    $settings->add($setting);
}
