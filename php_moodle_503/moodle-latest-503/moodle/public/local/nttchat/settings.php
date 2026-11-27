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
 * Cài đặt quản trị của local_nttchat (Site administration > Plugins > Local plugins > NTT chat groups).
 *
 * @package    local_nttchat
 * @copyright  2026 NTTU
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */

defined('MOODLE_INTERNAL') || die();

if ($hassiteconfig) {
    $settings = new admin_settingpage('local_nttchat', get_string('pluginname', 'local_nttchat'));
    $ADMIN->add('localplugins', $settings);

    if ($ADMIN->fulltree) {
        // Trạng thái Redis: chỉ đọc, giúp quản trị thấy ngay cấu hình đúng hay chưa.
        if (!class_exists(\Redis::class)) {
            $status = get_string('redisstatus_noext', 'local_nttchat');
        } else if (!\local_nttchat\redis_store::is_configured()) {
            $status = get_string('redisstatus_notconfigured', 'local_nttchat');
        } else {
            $store = \local_nttchat\redis_store::instance();
            $status = $store && $store->ping()
                ? get_string('redisstatus_ok', 'local_nttchat')
                : get_string('redisstatus_failed', 'local_nttchat');
        }
        $settings->add(new admin_setting_description(
            'local_nttchat/redisstatus',
            get_string('redisstatus', 'local_nttchat'),
            s($status)
        ));

        $settings->add(new admin_setting_configcheckbox(
            'local_nttchat/realtime',
            get_string('settingsrealtime', 'local_nttchat'),
            get_string('settingsrealtime_desc', 'local_nttchat'),
            1
        ));
        $settings->add(new admin_setting_configtext(
            'local_nttchat/pollinterval',
            get_string('settingspollinterval', 'local_nttchat'),
            get_string('settingspollinterval_desc', 'local_nttchat'),
            4,
            PARAM_INT
        ));
        $settings->add(new admin_setting_configtext(
            'local_nttchat/maxgroupmembers',
            get_string('settingsmaxgroupmembers', 'local_nttchat'),
            '',
            50,
            PARAM_INT
        ));
        $settings->add(new admin_setting_configtext(
            'local_nttchat/maxgroupsperuser',
            get_string('settingsmaxgroupsperuser', 'local_nttchat'),
            '',
            20,
            PARAM_INT
        ));
    }
}
