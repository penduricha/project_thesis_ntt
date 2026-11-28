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

namespace local_nttchat\task;

use local_nttchat\course_room_service;

defined('MOODLE_INTERNAL') || die();

/**
 * Tác vụ định kỳ: đồng bộ phòng chat của mọi khoá học với danh sách ghi danh.
 *
 * Sự kiện ghi danh đã cập nhật phòng ngay lập tức; tác vụ này bắt các trường hợp không có sự kiện (ghi danh hết
 * hạn theo thời gian, dữ liệu nhập bằng CLI/SQL) và dựng phòng cho khoá học có sẵn khi bật tính năng.
 *
 * @package    local_nttchat
 * @copyright  2026 NTTU
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */
class sync_course_rooms extends \core\task\scheduled_task {
    /**
     * @return string
     */
    public function get_name(): string {
        return get_string('tasksynccourserooms', 'local_nttchat');
    }

    /**
     * Chạy đồng bộ.
     */
    public function execute(): void {
        if (!course_room_service::enabled()) {
            mtrace('local_nttchat: phòng chat khoá học đang tắt, bỏ qua.');
            return;
        }
        $total = course_room_service::sync_all();
        mtrace(sprintf(
            'local_nttchat: %d khoá học, tạo %d phòng, thêm %d, bớt %d thành viên, dọn %d phòng mồ côi.',
            $total['courses'],
            $total['created'],
            $total['added'],
            $total['removed'],
            $total['deleted']
        ));
    }
}
