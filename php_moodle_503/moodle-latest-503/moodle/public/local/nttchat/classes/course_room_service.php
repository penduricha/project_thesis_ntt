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

use core_message\api as messageapi;

defined('MOODLE_INTERNAL') || die();

/**
 * Phòng chat tự động theo khoá học: mỗi khoá học có MỘT phòng chat nhóm, thành viên là những người đang
 * được ghi danh (ghi danh còn hiệu lực). Ghi danh/huỷ ghi danh/tạm ngưng thì thành viên phòng đổi theo.
 *
 * Như nhóm do người dùng tự tạo (group_service), phòng là một hội thoại nhóm của lõi (core_message) nên nằm
 * chung ngăn kéo chat. Cách nhận biết: component = local_nttchat và itemid = 0 (nhóm tự tạo luôn có itemid =
 * id chủ nhóm > 0). contextid là ngữ cảnh khoá học, từ đó suy ra id khoá học.
 *
 * Thành viên do hệ thống quản lý: không ai đổi tên, thêm, bớt hay rời phòng được (rời thì lần đồng bộ sau
 * sẽ thêm lại, nên chặn ngay từ đầu cho rõ ràng). Muốn ngừng nhận thông báo thì dùng "tắt thông báo" của lõi.
 *
 * @package    local_nttchat
 * @copyright  2026 NTTU
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */
class course_room_service {
    /** @var int Số người tối thiểu để một khoá học có phòng (khoá chỉ có một người thì không cần phòng). */
    public const MIN_MEMBERS = 2;

    /**
     * Tính năng có đang bật không (site bật nhắn tin và quản trị không tắt phòng khoá học).
     *
     * @return bool
     */
    public static function enabled(): bool {
        global $CFG;
        if (empty($CFG->messaging)) {
            return false;
        }
        $setting = get_config('local_nttchat', 'courserooms');
        return $setting === false || (int) $setting === 1;
    }

    /**
     * Hội thoại này có phải phòng khoá học không.
     *
     * @param \stdClass $conversation Dòng message_conversations.
     * @return bool
     */
    public static function is_course_room(\stdClass $conversation): bool {
        return $conversation->component === group_service::COMPONENT && (int) $conversation->itemid === 0;
    }

    /**
     * Id khoá học của một phòng (0 nếu không xác định được).
     *
     * @param \stdClass $conversation
     * @return int
     */
    public static function course_id_of(\stdClass $conversation): int {
        global $DB;
        if (empty($conversation->contextid)) {
            return 0;
        }
        $context = $DB->get_record('context', ['id' => $conversation->contextid], 'contextlevel, instanceid');
        return $context && (int) $context->contextlevel === CONTEXT_COURSE ? (int) $context->instanceid : 0;
    }

    /**
     * Phòng của khoá học, hoặc null nếu chưa có.
     *
     * @param int $courseid
     * @return \stdClass|null
     */
    public static function find_room(int $courseid): ?\stdClass {
        global $DB;
        $context = \context_course::instance($courseid, IGNORE_MISSING);
        if (!$context) {
            return null;
        }
        $rooms = $DB->get_records('message_conversations', [
            'type' => messageapi::MESSAGE_CONVERSATION_TYPE_GROUP,
            'component' => group_service::COMPONENT,
            'itemid' => 0,
            'contextid' => $context->id,
        ], 'id ASC', '*', 0, 1);
        return $rooms ? reset($rooms) : null;
    }

    /**
     * Người dùng có được ở trong phòng không: tài khoản thật, đang hoạt động, không phải khách.
     *
     * @param \stdClass $user Cần các trường deleted, suspended, confirmed, id.
     * @return bool
     */
    protected static function account_ok(\stdClass $user): bool {
        global $CFG;
        return empty($user->deleted) && empty($user->suspended) && !empty($user->confirmed)
            && (int) $user->id !== (int) $CFG->siteguest && (int) $user->id > 0;
    }

    /**
     * Id những người đủ điều kiện ở trong phòng: ghi danh còn hiệu lực (không bị tạm ngưng, chưa hết hạn)
     * và tài khoản hoạt động.
     *
     * @param int $courseid
     * @return int[]
     */
    public static function eligible_userids(int $courseid): array {
        $context = \context_course::instance($courseid, IGNORE_MISSING);
        if (!$context) {
            return [];
        }
        $users = get_enrolled_users($context, '', 0, 'u.id, u.deleted, u.suspended, u.confirmed', null, 0, 0, true);
        $ids = [];
        foreach ($users as $user) {
            if (self::account_ok($user)) {
                $ids[] = (int) $user->id;
            }
        }
        return $ids;
    }

    /**
     * Tên phòng = tên đầy đủ khoá học (đã làm sạch, cắt theo giới hạn tên nhóm).
     *
     * @param \stdClass $course
     * @return string
     */
    public static function room_name(\stdClass $course): string {
        $name = format_string($course->fullname, true, ['context' => \context_course::instance($course->id)]);
        $name = trim(preg_replace('/\s+/u', ' ', clean_param($name, PARAM_TEXT)));
        return \core_text::substr($name !== '' ? $name : $course->shortname, 0, group_service::NAME_MAXLENGTH);
    }

    /**
     * Khoá để hai tiến trình không cùng tạo phòng cho một khoá học (bảng không có ràng buộc duy nhất).
     *
     * @param int $courseid
     * @return \core\lock\lock|null
     */
    protected static function lock(int $courseid): ?\core\lock\lock {
        $factory = \core\lock\lock_config::get_lock_factory('local_nttchat_courseroom');
        $lock = $factory->get_lock('course' . $courseid, 10);
        return $lock ?: null;
    }

    /**
     * Đồng bộ thành viên phòng của một khoá học với danh sách ghi danh: tạo phòng nếu đã đủ người, thêm người
     * còn thiếu, bớt người không còn ghi danh.
     *
     * @param int $courseid
     * @return array ['created' => bool, 'added' => int, 'removed' => int]
     */
    public static function sync_course(int $courseid): array {
        global $DB;
        $result = ['created' => false, 'added' => 0, 'removed' => 0];
        if (!self::enabled() || $courseid <= SITEID) {
            return $result;
        }
        $course = $DB->get_record('course', ['id' => $courseid]);
        if (!$course) {
            return $result;
        }
        $lock = self::lock($courseid);
        if (!$lock) {
            return $result;
        }
        try {
            $eligible = self::eligible_userids($courseid);
            $room = self::find_room($courseid);
            if (!$room) {
                if (count($eligible) < self::MIN_MEMBERS) {
                    return $result;
                }
                $room = messageapi::create_conversation(
                    messageapi::MESSAGE_CONVERSATION_TYPE_GROUP,
                    $eligible,
                    self::room_name($course),
                    messageapi::MESSAGE_CONVERSATION_ENABLED,
                    group_service::COMPONENT,
                    null,
                    0,
                    \context_course::instance($courseid)->id
                );
                $result['created'] = true;
                $result['added'] = count($eligible);
                return $result;
            }

            $current = array_map('intval', $DB->get_fieldset_select(
                'message_conversation_members',
                'userid',
                'conversationid = ?',
                [$room->id]
            ));
            $toadd = array_values(array_diff($eligible, $current));
            $toremove = array_values(array_diff($current, $eligible));
            if ($toadd) {
                messageapi::add_members_to_conversation($toadd, (int) $room->id);
            }
            if ($toremove) {
                messageapi::remove_members_from_conversation($toremove, (int) $room->id);
            }
            $result['added'] = count($toadd);
            $result['removed'] = count($toremove);
            if ($room->name !== self::room_name($course)) {
                $DB->set_field('message_conversations', 'name', self::room_name($course), ['id' => $room->id]);
            }
        } finally {
            $lock->release();
        }
        return $result;
    }

    /**
     * Cập nhật phòng sau khi MỘT người đổi ghi danh (rẻ hơn đồng bộ cả khoá).
     *
     * @param int $courseid
     * @param int $userid
     */
    public static function sync_user(int $courseid, int $userid): void {
        global $DB;
        if (!self::enabled() || $courseid <= SITEID || $userid <= 0) {
            return;
        }
        $room = self::find_room($courseid);
        if (!$room) {
            // Có thể đây là người thứ hai của khoá: đồng bộ cả khoá để tạo phòng khi đã đủ người.
            self::sync_course($courseid);
            return;
        }
        $context = \context_course::instance($courseid, IGNORE_MISSING);
        $user = $DB->get_record('user', ['id' => $userid], 'id, deleted, suspended, confirmed');
        $shouldbein = $context && $user && self::account_ok($user) && is_enrolled($context, $userid, '', true);
        $isin = messageapi::is_user_in_conversation($userid, (int) $room->id);
        if ($shouldbein && !$isin) {
            messageapi::add_members_to_conversation([$userid], (int) $room->id);
        } else if (!$shouldbein && $isin) {
            messageapi::remove_members_from_conversation([$userid], (int) $room->id);
        }
    }

    /**
     * Đổi tên phòng theo tên khoá học (khi khoá học được sửa).
     *
     * @param int $courseid
     */
    public static function rename_room(int $courseid): void {
        global $DB;
        $room = self::find_room($courseid);
        $course = $DB->get_record('course', ['id' => $courseid], 'id, fullname, shortname');
        if ($room && $course && $room->name !== self::room_name($course)) {
            $DB->set_field('message_conversations', 'name', self::room_name($course), ['id' => $room->id]);
        }
    }

    /**
     * Xoá phòng và toàn bộ tin nhắn của phòng (khi khoá học bị xoá).
     *
     * @param int $courseid
     * @param int|null $contextid Ngữ cảnh khoá học (khi khoá đã bị xoá thì không tra lại được).
     */
    public static function delete_room(int $courseid, ?int $contextid = null): void {
        global $DB;
        if ($contextid === null) {
            $room = self::find_room($courseid);
            $roomids = $room ? [(int) $room->id] : [];
        } else {
            $roomids = array_map('intval', $DB->get_fieldset_select('message_conversations', 'id', 'type = ? AND component = ? AND itemid = 0 AND contextid = ?', [
                messageapi::MESSAGE_CONVERSATION_TYPE_GROUP,
                group_service::COMPONENT,
                $contextid,
            ]));
        }
        foreach ($roomids as $roomid) {
            self::purge_room($roomid);
        }
    }

    /**
     * Xoá hẳn một phòng: hội thoại, thành viên, tin nhắn và thao tác kèm theo.
     *
     * Khi khoá học đã bị xoá (sự kiện course_deleted đến SAU khi ngữ cảnh khoá học bị xoá) thì
     * messageapi::delete_all_conversation_data() ném lỗi "Can't find data record in database table context"
     * vì nó tra lại ngữ cảnh của hội thoại; lúc đó phải xoá tay, theo đúng các bước của hàm lõi.
     * Còn ngữ cảnh thì dùng hàm lõi để hưởng mọi cập nhật sau này của Moodle.
     *
     * @param int $roomid
     */
    protected static function purge_room(int $roomid): void {
        global $DB;
        $conversation = $DB->get_record('message_conversations', ['id' => $roomid], 'id, contextid');
        if (!$conversation) {
            return;
        }
        if (empty($conversation->contextid) || $DB->record_exists('context', ['id' => $conversation->contextid])) {
            messageapi::delete_all_conversation_data($roomid);
            return;
        }
        $transaction = $DB->start_delegated_transaction();
        $DB->delete_records('message_conversations', ['id' => $roomid]);
        $DB->delete_records('message_conversation_members', ['conversationid' => $roomid]);
        $DB->delete_records('message_conversation_actions', ['conversationid' => $roomid]);
        $messageids = $DB->get_fieldset_select('messages', 'id', 'conversationid = ?', [$roomid]);
        foreach (array_chunk($messageids, 1000) as $chunk) {
            [$insql, $inparams] = $DB->get_in_or_equal($chunk);
            $DB->delete_records_select('message_user_actions', "messageid $insql", $inparams);
        }
        $DB->delete_records('messages', ['conversationid' => $roomid]);
        // Không còn ngữ cảnh để lọc: xoá mọi mục yêu thích của hội thoại này.
        \core_favourites\service_factory::get_service_for_component('core_message')
            ->delete_favourites_by_type_and_item('message_conversations', $roomid);
        $transaction->allow_commit();
    }

    /**
     * Xoá những phòng mồ côi: khoá học (ngữ cảnh) đã không còn mà phòng vẫn tồn tại, ví dụ khi bộ quan sát
     * course_deleted bị lỗi giữa chừng.
     *
     * @return int Số phòng đã xoá.
     */
    public static function cleanup_orphans(): int {
        global $DB;
        $sql = "SELECT c.id
                  FROM {message_conversations} c
             LEFT JOIN {context} ctx ON ctx.id = c.contextid
                 WHERE c.type = :type AND c.component = :component AND c.itemid = 0
                       AND c.contextid IS NOT NULL AND ctx.id IS NULL";
        $ids = $DB->get_fieldset_sql($sql, [
            'type' => messageapi::MESSAGE_CONVERSATION_TYPE_GROUP,
            'component' => group_service::COMPONENT,
        ]);
        foreach ($ids as $id) {
            self::purge_room((int) $id);
        }
        return count($ids);
    }

    /**
     * Đồng bộ mọi khoá học (dùng cho tác vụ định kỳ, lúc cài/nâng cấp plugin, và để bắt các trường hợp không có
     * sự kiện như ghi danh hết hạn theo thời gian).
     *
     * @return array ['courses' => int, 'created' => int, 'added' => int, 'removed' => int, 'deleted' => int]
     *         ('deleted' là số phòng mồ côi đã dọn).
     */
    public static function sync_all(): array {
        global $DB;
        $total = ['courses' => 0, 'created' => 0, 'added' => 0, 'removed' => 0, 'deleted' => 0];
        if (!self::enabled()) {
            return $total;
        }
        try {
            $total['deleted'] = self::cleanup_orphans();
        } catch (\Throwable $e) {
            debugging('local_nttchat: không dọn được phòng mồ côi: ' . $e->getMessage(), DEBUG_DEVELOPER);
        }
        foreach ($DB->get_fieldset_select('course', 'id', 'id <> ?', [SITEID]) as $courseid) {
            try {
                $one = self::sync_course((int) $courseid);
            } catch (\Throwable $e) {
                debugging('local_nttchat: không đồng bộ được phòng khoá ' . $courseid . ': ' . $e->getMessage(), DEBUG_DEVELOPER);
                continue;
            }
            $total['courses']++;
            $total['created'] += $one['created'] ? 1 : 0;
            $total['added'] += $one['added'];
            $total['removed'] += $one['removed'];
        }
        return $total;
    }
}
