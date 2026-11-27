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

namespace local_nttchat;

use core\hook\output\before_footer_html_generation;

defined('MOODLE_INTERNAL') || die();

/**
 * Hook gắn mã JS nhận tin mới vào mọi trang của người dùng đã đăng nhập.
 *
 * @package    local_nttchat
 * @copyright  2026 NTTU
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */
class hook_callbacks {
    /** @var int Chu kỳ hỏi tin mới mặc định (giây). */
    public const DEFAULT_INTERVAL = 4;

    /**
     * Nạp JS nhận tin mới. Không làm gì nếu: chưa đăng nhập / khách, tắt nhắn tin, tắt realtime, chưa cấu hình Redis.
     *
     * Chỉ kiểm tra cấu hình, KHÔNG kết nối Redis ở đây để trang tải không bị chậm khi Redis hỏng.
     *
     * @param before_footer_html_generation $hook
     */
    public static function before_footer_html_generation(before_footer_html_generation $hook): void {
        global $CFG, $PAGE, $USER;
        if (
            during_initial_install() || !isloggedin() || isguestuser() || empty($CFG->messaging)
            || !get_config('local_nttchat', 'realtime') || !redis_store::is_configured()
            || in_array($PAGE->pagelayout, ['embedded', 'frametop', 'popup', 'print'], true)
        ) {
            return;
        }
        $interval = (int) get_config('local_nttchat', 'pollinterval');
        $interval = max(2, min(60, $interval ?: self::DEFAULT_INTERVAL));
        $PAGE->requires->strings_for_js(['newmessagefrom', 'newgroupmessage', 'openchat'], 'local_nttchat');
        $PAGE->requires->js_call_amd('local_nttchat/realtime', 'init', [[
            'interval' => $interval,
            'userid' => (int) $USER->id,
        ]]);
    }
}
