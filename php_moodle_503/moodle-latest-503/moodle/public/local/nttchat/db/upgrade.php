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
 * Nâng cấp local_nttchat.
 *
 * @package    local_nttchat
 * @copyright  2026 NTTU
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */

defined('MOODLE_INTERNAL') || die();

/**
 * @param int $oldversion
 * @return bool
 */
function xmldb_local_nttchat_upgrade($oldversion) {
    if ($oldversion < 2026112800) {
        // 1.1.0: phòng chat tự động theo khoá học. Dựng phòng cho các khoá học đã có; lỗi (ví dụ chưa có dữ liệu)
        // không được làm hỏng nâng cấp, tác vụ định kỳ sẽ làm lại.
        try {
            \local_nttchat\course_room_service::sync_all();
        } catch (\Throwable $e) {
            debugging('local_nttchat: chưa dựng được phòng khoá học khi nâng cấp: ' . $e->getMessage(), DEBUG_DEVELOPER);
        }
        upgrade_plugin_savepoint(true, 2026112800, 'local', 'nttchat');
    }
    return true;
}
