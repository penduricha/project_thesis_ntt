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

namespace local_nttchat\external;

use core_external\external_api;
use core_external\external_function_parameters;
use core_external\external_multiple_structure;
use core_external\external_single_structure;
use core_external\external_value;
use local_nttchat\group_service;
use local_nttchat\redis_store;

defined('MOODLE_INTERNAL') || die();

/**
 * Các hàm web service (chỉ gọi qua AJAX của trình duyệt) cho trang "Nhóm chat" và việc báo tin mới.
 *
 * Mọi hàm chỉ thao tác trên CHÍNH người đang đăng nhập: không có tham số "userid", nên không ai thao tác thay người khác.
 * Quyền sở hữu nhóm do {@see group_service} kiểm tra.
 *
 * @package    local_nttchat
 * @copyright  2026 NTTU
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */
class chat_api extends external_api {
    /**
     * Kiểm tra ngữ cảnh + quyền chung cho mọi hàm.
     */
    protected static function require_access(): void {
        $context = \context_system::instance();
        self::validate_context($context);
        require_capability('local/nttchat:creategroup', $context);
    }

    /**
     * Cấu trúc trả về của một người dùng gọn.
     *
     * @return external_single_structure
     */
    protected static function user_structure(): external_single_structure {
        return new external_single_structure([
            'id' => new external_value(PARAM_INT, 'Id người dùng'),
            'fullname' => new external_value(PARAM_TEXT, 'Họ tên'),
            'profileimageurlsmall' => new external_value(PARAM_RAW, 'Ảnh đại diện nhỏ'),
        ]);
    }

    /**
     * Cấu trúc trả về của một nhóm.
     *
     * @return external_single_structure
     */
    protected static function group_structure(): external_single_structure {
        return new external_single_structure([
            'id' => new external_value(PARAM_INT, 'Id hội thoại'),
            'name' => new external_value(PARAM_TEXT, 'Tên nhóm'),
            'ownerid' => new external_value(PARAM_INT, 'Id chủ nhóm'),
            'isowner' => new external_value(PARAM_BOOL, 'Người gọi có phải chủ nhóm'),
            'iscourseroom' => new external_value(PARAM_BOOL, 'Là phòng chat tự động của khoá học'),
            'courseid' => new external_value(PARAM_INT, 'Id khoá học (0 nếu không phải phòng khoá học)'),
            'membercount' => new external_value(PARAM_INT, 'Số thành viên'),
            'timemodified' => new external_value(PARAM_INT, 'Lần hoạt động cuối'),
            'members' => new external_multiple_structure(new external_single_structure([
                'id' => new external_value(PARAM_INT, 'Id người dùng'),
                'fullname' => new external_value(PARAM_TEXT, 'Họ tên'),
                'profileimageurlsmall' => new external_value(PARAM_RAW, 'Ảnh đại diện nhỏ'),
                'isowner' => new external_value(PARAM_BOOL, 'Là chủ nhóm'),
            ])),
        ]);
    }

    /**
     * Kết quả chung của các hàm ghi.
     *
     * @return external_single_structure
     */
    protected static function status_structure(): external_single_structure {
        return new external_single_structure([
            'status' => new external_value(PARAM_BOOL, 'Thành công'),
            'id' => new external_value(PARAM_INT, 'Id hội thoại liên quan (0 nếu đã xoá)'),
        ]);
    }

    // ---------------------------------------------------------------------------------------------
    // Danh sách nhóm.
    // ---------------------------------------------------------------------------------------------

    /**
     * @return external_function_parameters
     */
    public static function get_groups_parameters(): external_function_parameters {
        return new external_function_parameters([]);
    }

    /**
     * Các nhóm chat người gọi đang ở trong.
     *
     * @return array
     */
    public static function get_groups(): array {
        global $USER;
        self::validate_parameters(self::get_groups_parameters(), []);
        self::require_access();
        return ['groups' => group_service::list_for_user((int) $USER->id)];
    }

    /**
     * @return external_single_structure
     */
    public static function get_groups_returns(): external_single_structure {
        return new external_single_structure([
            'groups' => new external_multiple_structure(self::group_structure()),
        ]);
    }

    // ---------------------------------------------------------------------------------------------
    // Tạo nhóm.
    // ---------------------------------------------------------------------------------------------

    /**
     * @return external_function_parameters
     */
    public static function create_group_parameters(): external_function_parameters {
        return new external_function_parameters([
            'name' => new external_value(PARAM_TEXT, 'Tên nhóm'),
            'memberids' => new external_multiple_structure(new external_value(PARAM_INT, 'Id người dùng'), 'Thành viên (không gồm bạn)'),
        ]);
    }

    /**
     * Tạo nhóm chat mới; người gọi là chủ nhóm.
     *
     * @param string $name
     * @param int[] $memberids
     * @return array
     */
    public static function create_group(string $name, array $memberids): array {
        global $USER;
        $params = self::validate_parameters(self::create_group_parameters(), ['name' => $name, 'memberids' => $memberids]);
        self::require_access();
        $conversation = group_service::create((int) $USER->id, $params['name'], $params['memberids']);
        return ['status' => true, 'id' => (int) $conversation->id];
    }

    /**
     * @return external_single_structure
     */
    public static function create_group_returns(): external_single_structure {
        return self::status_structure();
    }

    // ---------------------------------------------------------------------------------------------
    // Đổi tên.
    // ---------------------------------------------------------------------------------------------

    /**
     * @return external_function_parameters
     */
    public static function rename_group_parameters(): external_function_parameters {
        return new external_function_parameters([
            'groupid' => new external_value(PARAM_INT, 'Id hội thoại nhóm'),
            'name' => new external_value(PARAM_TEXT, 'Tên mới'),
        ]);
    }

    /**
     * Đổi tên nhóm (chỉ chủ nhóm).
     *
     * @param int $groupid
     * @param string $name
     * @return array
     */
    public static function rename_group(int $groupid, string $name): array {
        global $USER;
        $params = self::validate_parameters(self::rename_group_parameters(), ['groupid' => $groupid, 'name' => $name]);
        self::require_access();
        group_service::rename($params['groupid'], (int) $USER->id, $params['name']);
        return ['status' => true, 'id' => $params['groupid']];
    }

    /**
     * @return external_single_structure
     */
    public static function rename_group_returns(): external_single_structure {
        return self::status_structure();
    }

    // ---------------------------------------------------------------------------------------------
    // Thêm thành viên.
    // ---------------------------------------------------------------------------------------------

    /**
     * @return external_function_parameters
     */
    public static function add_members_parameters(): external_function_parameters {
        return new external_function_parameters([
            'groupid' => new external_value(PARAM_INT, 'Id hội thoại nhóm'),
            'userids' => new external_multiple_structure(new external_value(PARAM_INT, 'Id người dùng')),
        ]);
    }

    /**
     * Thêm thành viên (chỉ chủ nhóm).
     *
     * @param int $groupid
     * @param int[] $userids
     * @return array
     */
    public static function add_members(int $groupid, array $userids): array {
        global $USER;
        $params = self::validate_parameters(self::add_members_parameters(), ['groupid' => $groupid, 'userids' => $userids]);
        self::require_access();
        group_service::add_members($params['groupid'], (int) $USER->id, $params['userids']);
        return ['status' => true, 'id' => $params['groupid']];
    }

    /**
     * @return external_single_structure
     */
    public static function add_members_returns(): external_single_structure {
        return self::status_structure();
    }

    // ---------------------------------------------------------------------------------------------
    // Xoá thành viên.
    // ---------------------------------------------------------------------------------------------

    /**
     * @return external_function_parameters
     */
    public static function remove_member_parameters(): external_function_parameters {
        return new external_function_parameters([
            'groupid' => new external_value(PARAM_INT, 'Id hội thoại nhóm'),
            'userid' => new external_value(PARAM_INT, 'Người bị xoá khỏi nhóm'),
        ]);
    }

    /**
     * Xoá một thành viên khỏi nhóm (chỉ chủ nhóm).
     *
     * @param int $groupid
     * @param int $userid
     * @return array
     */
    public static function remove_member(int $groupid, int $userid): array {
        global $USER;
        $params = self::validate_parameters(self::remove_member_parameters(), ['groupid' => $groupid, 'userid' => $userid]);
        self::require_access();
        group_service::remove_member($params['groupid'], (int) $USER->id, $params['userid']);
        return ['status' => true, 'id' => $params['groupid']];
    }

    /**
     * @return external_single_structure
     */
    public static function remove_member_returns(): external_single_structure {
        return self::status_structure();
    }

    // ---------------------------------------------------------------------------------------------
    // Rời nhóm.
    // ---------------------------------------------------------------------------------------------

    /**
     * @return external_function_parameters
     */
    public static function leave_group_parameters(): external_function_parameters {
        return new external_function_parameters([
            'groupid' => new external_value(PARAM_INT, 'Id hội thoại nhóm'),
        ]);
    }

    /**
     * Người gọi rời nhóm.
     *
     * @param int $groupid
     * @return array
     */
    public static function leave_group(int $groupid): array {
        global $USER;
        $params = self::validate_parameters(self::leave_group_parameters(), ['groupid' => $groupid]);
        self::require_access();
        group_service::leave($params['groupid'], (int) $USER->id);
        return ['status' => true, 'id' => 0];
    }

    /**
     * @return external_single_structure
     */
    public static function leave_group_returns(): external_single_structure {
        return self::status_structure();
    }

    // ---------------------------------------------------------------------------------------------
    // Giải tán nhóm.
    // ---------------------------------------------------------------------------------------------

    /**
     * @return external_function_parameters
     */
    public static function delete_group_parameters(): external_function_parameters {
        return new external_function_parameters([
            'groupid' => new external_value(PARAM_INT, 'Id hội thoại nhóm'),
        ]);
    }

    /**
     * Giải tán nhóm và xoá toàn bộ tin nhắn (chỉ chủ nhóm).
     *
     * @param int $groupid
     * @return array
     */
    public static function delete_group(int $groupid): array {
        global $USER;
        $params = self::validate_parameters(self::delete_group_parameters(), ['groupid' => $groupid]);
        self::require_access();
        group_service::delete($params['groupid'], (int) $USER->id);
        return ['status' => true, 'id' => 0];
    }

    /**
     * @return external_single_structure
     */
    public static function delete_group_returns(): external_single_structure {
        return self::status_structure();
    }

    // ---------------------------------------------------------------------------------------------
    // Tìm người.
    // ---------------------------------------------------------------------------------------------

    /**
     * @return external_function_parameters
     */
    public static function search_users_parameters(): external_function_parameters {
        return new external_function_parameters([
            'query' => new external_value(PARAM_TEXT, 'Từ khoá tìm người (ít nhất 2 ký tự)'),
        ]);
    }

    /**
     * Tìm người để nhắn tin hoặc thêm vào nhóm, theo đúng quyền riêng tư của chat lõi.
     *
     * @param string $query
     * @return array
     */
    public static function search_users(string $query): array {
        global $USER;
        $params = self::validate_parameters(self::search_users_parameters(), ['query' => $query]);
        self::require_access();
        return ['users' => group_service::search_users((int) $USER->id, $params['query'])];
    }

    /**
     * @return external_single_structure
     */
    public static function search_users_returns(): external_single_structure {
        return new external_single_structure([
            'users' => new external_multiple_structure(self::user_structure()),
        ]);
    }

    // ---------------------------------------------------------------------------------------------
    // Tin mới (realtime).
    // ---------------------------------------------------------------------------------------------

    /**
     * @return external_function_parameters
     */
    public static function get_updates_parameters(): external_function_parameters {
        return new external_function_parameters([
            'lastid' => new external_value(PARAM_RAW_TRIMMED, 'Id mục cuối đã nhận; rỗng = lần đầu (chỉ lấy mốc)', VALUE_DEFAULT, ''),
        ]);
    }

    /**
     * Các tin mới kể từ $lastid, đọc từ hộp thư Redis của chính người gọi.
     *
     * Lần đầu (lastid rỗng) chỉ trả về mốc mới nhất để không phát lại lịch sử. Chỉ truy vấn MariaDB khi thật sự có tin mới
     * (để lấy số hội thoại chưa đọc), nên lần hỏi "không có gì" chỉ tốn một lệnh Redis.
     *
     * @param string $lastid
     * @return array
     */
    public static function get_updates(string $lastid = ''): array {
        global $USER;
        $params = self::validate_parameters(self::get_updates_parameters(), ['lastid' => $lastid]);
        self::require_access();

        $empty = ['available' => false, 'lastid' => $params['lastid'], 'unreadcount' => -1, 'events' => []];
        if (empty(get_config('local_nttchat', 'realtime')) || !redis_store::is_configured()) {
            return $empty;
        }
        $store = redis_store::instance();
        if (!$store) {
            return $empty;
        }
        $userid = (int) $USER->id;
        if ($params['lastid'] === '' || !preg_match('/^\d+-\d+$/', $params['lastid'])) {
            $latest = $store->latest_id($userid);
            return $latest === null ? $empty : ['available' => true, 'lastid' => $latest, 'unreadcount' => -1, 'events' => []];
        }
        $rows = $store->read($userid, $params['lastid']);
        if ($rows === null) {
            return $empty;
        }
        $events = [];
        $last = $params['lastid'];
        foreach ($rows as $row) {
            $last = $row['id'];
            $f = $row['fields'];
            $events[] = [
                'id' => $row['id'],
                'type' => (string) ($f['type'] ?? 'user'),
                'conversationid' => (int) ($f['cid'] ?? 0),
                'messageid' => (int) ($f['mid'] ?? 0),
                'fromid' => (int) ($f['from'] ?? 0),
                'fromname' => (string) ($f['fromname'] ?? ''),
                'groupname' => (string) ($f['group'] ?? ''),
                'text' => (string) ($f['text'] ?? ''),
                'time' => (int) ($f['time'] ?? 0),
            ];
        }
        $unread = $events ? \core_message\api::count_unread_conversations($USER) : -1;
        return ['available' => true, 'lastid' => $last, 'unreadcount' => (int) $unread, 'events' => $events];
    }

    /**
     * @return external_single_structure
     */
    public static function get_updates_returns(): external_single_structure {
        return new external_single_structure([
            'available' => new external_value(PARAM_BOOL, 'Redis đang dùng được'),
            'lastid' => new external_value(PARAM_RAW, 'Mốc mới nhất để hỏi tiếp'),
            'unreadcount' => new external_value(PARAM_INT, 'Số hội thoại chưa đọc (-1: không đổi)'),
            'events' => new external_multiple_structure(new external_single_structure([
                'id' => new external_value(PARAM_RAW, 'Id mục trong stream'),
                'type' => new external_value(PARAM_ALPHA, 'user hoặc group'),
                'conversationid' => new external_value(PARAM_INT, 'Id hội thoại'),
                'messageid' => new external_value(PARAM_INT, 'Id tin nhắn'),
                'fromid' => new external_value(PARAM_INT, 'Id người gửi'),
                'fromname' => new external_value(PARAM_TEXT, 'Tên người gửi'),
                'groupname' => new external_value(PARAM_TEXT, 'Tên nhóm (rỗng nếu chat đơn)'),
                'text' => new external_value(PARAM_TEXT, 'Đoạn xem trước'),
                'time' => new external_value(PARAM_INT, 'Thời điểm gửi'),
            ])),
        ]);
    }
}
