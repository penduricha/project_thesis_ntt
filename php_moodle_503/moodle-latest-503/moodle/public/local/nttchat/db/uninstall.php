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
 * Gỡ plugin: bỏ mục "Nhóm chat" khỏi menu người dùng. Dữ liệu hội thoại do core_message quản lý nên giữ nguyên.
 *
 * @package    local_nttchat
 * @copyright  2026 NTTU
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */

defined('MOODLE_INTERNAL') || die();

/**
 * Xoá dòng menu đã thêm lúc cài.
 *
 * @return bool
 */
function xmldb_local_nttchat_uninstall() {
    $current = (string) get_config('core', 'customusermenuitems');
    $lines = preg_split('/\R/', $current, -1, PREG_SPLIT_NO_EMPTY);
    $kept = array_filter($lines, function ($line) {
        return strpos(trim($line), 'nttchatgroups,') !== 0;
    });
    set_config('customusermenuitems', implode("\n", $kept));
    return true;
}
