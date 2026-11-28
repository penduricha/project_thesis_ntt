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
 * Nhóm chat do người dùng tự tạo, xây trên chat nhóm có sẵn của Moodle (core_message).
 *
 * Không có bảng riêng: mỗi nhóm là một dòng trong message_conversations (type = nhóm) với
 * component = local_nttchat và itemid = id CHỦ NHÓM. Nhờ vậy tin nhắn nằm trong ngăn kéo
 * chat quen thuộc, có sẵn đã đọc / yêu thích / tắt thông báo / quyền riêng tư của lõi.
 *
 * Quy ước quyền: chỉ chủ nhóm đổi tên, thêm/xoá thành viên và giải tán; thành viên nào cũng rời được.
 * Chủ nhóm rời đi thì quyền chuyển cho người vào nhóm sớm nhất; hết người thì nhóm bị xoá.
 *
 * @package    local_nttchat
 * @copyright  2026 NTTU
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */
class group_service {
    /**
     * @var string Thành phần sở hữu hội thoại (cột message_conversations.component).
     *
     * Cố ý để itemtype = NULL. Lõi (core_message\api::get_linked_conversation_extra_fields, ghi chú MDL-63814) chỉ
     * xử lý hội thoại có CẢ component lẫn itemtype, và đọc thẳng $linked['core_group']['groups'] không kiểm tra
     * isset: chỉ cần có một hội thoại "component khác core_group, itemtype khác NULL" là mỗi lần mở ngăn kéo chat sẽ
     * sinh PHP Warning "Undefined array key core_group" vào log. Nhóm của plugin không cần xử lý phụ đó.
     */
    public const COMPONENT = 'local_nttchat';

    /** @var int Độ dài tối đa tên nhóm. */
    public const NAME_MAXLENGTH = 100;

    /**
     * Số thành viên tối đa của một nhóm (kể cả chủ nhóm).
     *
     * @return int
     */
    public static function max_members(): int {
        return max(2, (int) (get_config('local_nttchat', 'maxgroupmembers') ?: 50));
    }

    /**
     * Số nhóm tối đa một người được làm chủ.
     *
     * @return int
     */
    public static function max_groups_per_user(): int {
        return max(1, (int) (get_config('local_nttchat', 'maxgroupsperuser') ?: 20));
    }

    /**
     * Ném lỗi nếu site tắt tính năng nhắn tin.
     */
    protected static function require_messaging(): void {
        global $CFG;
        if (empty($CFG->messaging)) {
            throw new \moodle_exception('messagingdisabled', 'local_nttchat');
        }
    }

    /**
     * Làm sạch và kiểm tra tên nhóm.
     *
     * @param string $name
     * @return string
     */
    public static function clean_name(string $name): string {
        $name = trim(preg_replace('/\s+/u', ' ', clean_param($name, PARAM_TEXT)));
        $length = \core_text::strlen($name);
        if ($length < 1 || $length > self::NAME_MAXLENGTH) {
            throw new \moodle_exception('invalidname', 'local_nttchat', '', self::NAME_MAXLENGTH);
        }
        return $name;
    }

    /**
     * Chuẩn hoá danh sách id người dùng: số nguyên dương, không trùng, bỏ các id trong $exclude.
     *
     * @param array $userids
     * @param int[] $exclude
     * @return int[]
     */
    protected static function normalise_userids(array $userids, array $exclude = []): array {
        $ids = array_filter(array_map('intval', $userids), function ($id) {
            return $id > 0;
        });
        return array_values(array_diff(array_unique($ids), $exclude));
    }

    /**
     * Mỗi người được thêm phải là tài khoản thật, đang hoạt động và chủ nhóm được phép nhắn cho họ
     * (theo cài đặt quyền riêng tư và danh sách chặn của chính họ).
     *
     * @param int $ownerid
     * @param int[] $userids
     */
    protected static function assert_can_add(int $ownerid, array $userids): void {
        global $DB;
        if (!$userids) {
            return;
        }
        [$insql, $params] = $DB->get_in_or_equal($userids, SQL_PARAMS_NAMED);
        $users = $DB->get_records_select('user', "id $insql", $params);
        foreach ($userids as $id) {
            $user = $users[$id] ?? null;
            if (!$user || $user->deleted || $user->suspended || !$user->confirmed || isguestuser($user)) {
                throw new \moodle_exception('invaliduser', 'local_nttchat', '', $id);
            }
            if (!messageapi::can_send_message((int) $id, $ownerid)) {
                throw new \moodle_exception('cannotaddmember', 'local_nttchat', '', fullname($user));
            }
        }
    }

    /**
     * Số nhóm người này đang làm chủ.
     *
     * @param int $ownerid
     * @return int
     */
    public static function count_owned(int $ownerid): int {
        global $DB;
        return $DB->count_records('message_conversations', [
            'type' => messageapi::MESSAGE_CONVERSATION_TYPE_GROUP,
            'component' => self::COMPONENT,
            'itemid' => $ownerid,
        ]);
    }

    /**
     * Lấy nhóm theo id; ném lỗi nếu không phải nhóm của plugin này.
     *
     * @param int $conversationid
     * @return \stdClass
     */
    public static function get_group(int $conversationid): \stdClass {
        global $DB;
        $conversation = $DB->get_record('message_conversations', [
            'id' => $conversationid,
            'type' => messageapi::MESSAGE_CONVERSATION_TYPE_GROUP,
            'component' => self::COMPONENT,
        ]);
        if (!$conversation) {
            throw new \moodle_exception('groupnotfound', 'local_nttchat');
        }
        return $conversation;
    }

    /**
     * Nhóm mà $userid là thành viên (không phải thành viên thì coi như không thấy nhóm).
     *
     * @param int $conversationid
     * @param int $userid
     * @return \stdClass
     */
    public static function require_member(int $conversationid, int $userid): \stdClass {
        $conversation = self::get_group($conversationid);
        if (!messageapi::is_user_in_conversation($userid, $conversationid)) {
            throw new \moodle_exception('groupnotfound', 'local_nttchat');
        }
        return $conversation;
    }

    /**
     * Nhóm mà $userid là chủ.
     *
     * @param int $conversationid
     * @param int $userid
     * @return \stdClass
     */
    public static function require_owner(int $conversationid, int $userid): \stdClass {
        $conversation = self::require_member($conversationid, $userid);
        if (course_room_service::is_course_room($conversation)) {
            // Phòng khoá học do hệ thống quản lý theo danh sách ghi danh, không ai (kể cả giáo viên) sửa tay.
            throw new \moodle_exception('courseroomreadonly', 'local_nttchat');
        }
        if ((int) $conversation->itemid !== $userid) {
            throw new \moodle_exception('onlyowner', 'local_nttchat');
        }
        return $conversation;
    }

    /**
     * Tạo nhóm mới. Chủ nhóm tự động là thành viên.
     *
     * @param int $ownerid
     * @param string $name
     * @param int[] $memberids Các thành viên khác (không gồm chủ nhóm).
     * @return \stdClass Hội thoại vừa tạo.
     */
    public static function create(int $ownerid, string $name, array $memberids): \stdClass {
        global $DB;
        self::require_messaging();
        $name = self::clean_name($name);
        $memberids = self::normalise_userids($memberids, [$ownerid]);
        if (!$memberids) {
            throw new \moodle_exception('needmembers', 'local_nttchat');
        }
        if (1 + count($memberids) > self::max_members()) {
            throw new \moodle_exception('toomanymembers', 'local_nttchat', '', self::max_members());
        }
        if (self::count_owned($ownerid) >= self::max_groups_per_user()) {
            throw new \moodle_exception('toomanygroups', 'local_nttchat', '', self::max_groups_per_user());
        }
        self::assert_can_add($ownerid, $memberids);

        $transaction = $DB->start_delegated_transaction();
        $conversation = messageapi::create_conversation(
            messageapi::MESSAGE_CONVERSATION_TYPE_GROUP,
            array_merge([$ownerid], $memberids),
            $name,
            messageapi::MESSAGE_CONVERSATION_ENABLED,
            self::COMPONENT,
            null,
            $ownerid,
            \context_system::instance()->id
        );
        $transaction->allow_commit();
        return $conversation;
    }

    /**
     * Đổi tên nhóm (chỉ chủ nhóm).
     *
     * @param int $conversationid
     * @param int $userid
     * @param string $name
     */
    public static function rename(int $conversationid, int $userid, string $name): void {
        global $DB;
        self::require_owner($conversationid, $userid);
        $DB->update_record('message_conversations', (object) [
            'id' => $conversationid,
            'name' => self::clean_name($name),
            'timemodified' => time(),
        ]);
    }

    /**
     * Thêm thành viên (chỉ chủ nhóm). Người đã ở trong nhóm bị bỏ qua.
     *
     * @param int $conversationid
     * @param int $userid Chủ nhóm.
     * @param int[] $userids
     * @return int Số người thực sự được thêm.
     */
    public static function add_members(int $conversationid, int $userid, array $userids): int {
        global $DB;
        self::require_owner($conversationid, $userid);
        $existing = $DB->get_fieldset_select('message_conversation_members', 'userid', 'conversationid = ?', [$conversationid]);
        $new = self::normalise_userids($userids, array_map('intval', $existing));
        if (!$new) {
            return 0;
        }
        if (count($existing) + count($new) > self::max_members()) {
            throw new \moodle_exception('toomanymembers', 'local_nttchat', '', self::max_members());
        }
        self::assert_can_add($userid, $new);
        messageapi::add_members_to_conversation($new, $conversationid);
        $DB->set_field('message_conversations', 'timemodified', time(), ['id' => $conversationid]);
        return count($new);
    }

    /**
     * Xoá một thành viên khỏi nhóm (chỉ chủ nhóm, không xoá được chính mình: dùng leave()).
     *
     * @param int $conversationid
     * @param int $userid Chủ nhóm.
     * @param int $targetid Người bị xoá.
     */
    public static function remove_member(int $conversationid, int $userid, int $targetid): void {
        self::require_owner($conversationid, $userid);
        if ($targetid === $userid) {
            throw new \moodle_exception('cannotremoveowner', 'local_nttchat');
        }
        if (!messageapi::is_user_in_conversation($targetid, $conversationid)) {
            throw new \moodle_exception('notmember', 'local_nttchat');
        }
        messageapi::remove_members_from_conversation([$targetid], $conversationid);
    }

    /**
     * Rời nhóm. Chủ nhóm rời thì chuyển quyền cho người vào sớm nhất; không còn ai thì xoá nhóm.
     *
     * @param int $conversationid
     * @param int $userid
     */
    public static function leave(int $conversationid, int $userid): void {
        global $DB;
        $conversation = self::require_member($conversationid, $userid);
        if (course_room_service::is_course_room($conversation)) {
            throw new \moodle_exception('courseroomleave', 'local_nttchat');
        }
        $transaction = $DB->start_delegated_transaction();
        if ((int) $conversation->itemid === $userid) {
            $next = $DB->get_records_select(
                'message_conversation_members',
                'conversationid = ? AND userid <> ?',
                [$conversationid, $userid],
                'id ASC',
                'id, userid',
                0,
                1
            );
            if (!$next) {
                messageapi::delete_all_conversation_data($conversationid);
                $transaction->allow_commit();
                return;
            }
            $DB->set_field('message_conversations', 'itemid', reset($next)->userid, ['id' => $conversationid]);
        }
        messageapi::remove_members_from_conversation([$userid], $conversationid);
        $transaction->allow_commit();
    }

    /**
     * Giải tán nhóm và xoá toàn bộ tin nhắn của nhóm (chỉ chủ nhóm).
     *
     * @param int $conversationid
     * @param int $userid
     */
    public static function delete(int $conversationid, int $userid): void {
        self::require_owner($conversationid, $userid);
        messageapi::delete_all_conversation_data($conversationid);
    }

    /**
     * Thành viên của một nhóm.
     *
     * @param int $conversationid
     * @param int $ownerid
     * @return array[] Mỗi phần tử: id, fullname, profileimageurlsmall, isowner.
     */
    public static function members(int $conversationid, int $ownerid): array {
        global $DB, $PAGE;
        $sql = "SELECT u.*
                  FROM {message_conversation_members} m
                  JOIN {user} u ON u.id = m.userid
                 WHERE m.conversationid = :conversationid AND u.deleted = 0
              ORDER BY m.id";
        $result = [];
        foreach ($DB->get_records_sql($sql, ['conversationid' => $conversationid]) as $user) {
            $picture = new \user_picture($user);
            $picture->size = 1;
            $result[] = [
                'id' => (int) $user->id,
                'fullname' => fullname($user),
                'profileimageurlsmall' => $picture->get_url($PAGE)->out(false),
                'isowner' => (int) $user->id === $ownerid,
            ];
        }
        return $result;
    }

    /**
     * Các nhóm mà người dùng đang ở trong, mới hoạt động nhất lên đầu.
     *
     * @param int $userid
     * @return array[]
     */
    public static function list_for_user(int $userid): array {
        global $DB;
        $sql = "SELECT mc.id, mc.name, mc.itemid AS ownerid, mc.contextid, mc.timecreated, mc.timemodified,
                       (SELECT COUNT(1)
                          FROM {message_conversation_members} m2
                         WHERE m2.conversationid = mc.id) AS membercount
                  FROM {message_conversations} mc
                  JOIN {message_conversation_members} me ON me.conversationid = mc.id AND me.userid = :userid
                 WHERE mc.type = :type AND mc.component = :component
              ORDER BY CASE WHEN mc.itemid = 0 THEN 0 ELSE 1 END, mc.timemodified DESC, mc.id DESC";
        $rows = $DB->get_records_sql($sql, [
            'userid' => $userid,
            'type' => messageapi::MESSAGE_CONVERSATION_TYPE_GROUP,
            'component' => self::COMPONENT,
        ]);
        $groups = [];
        foreach ($rows as $row) {
            $ownerid = (int) $row->ownerid;
            $iscourseroom = $ownerid === 0;
            $groups[] = [
                'id' => (int) $row->id,
                'name' => format_string($row->name, true, ['context' => \context_system::instance()]),
                'ownerid' => $ownerid,
                'isowner' => !$iscourseroom && $ownerid === $userid,
                'iscourseroom' => $iscourseroom,
                'courseid' => $iscourseroom ? course_room_service::course_id_of($row) : 0,
                'membercount' => (int) $row->membercount,
                'timemodified' => (int) $row->timemodified,
                // Phòng khoá học có thể hàng trăm người: chỉ đếm, không liệt kê.
                'members' => $iscourseroom ? [] : self::members((int) $row->id, $ownerid),
            ];
        }
        return $groups;
    }

    /**
     * Tìm người để thêm vào nhóm. Dùng đúng bộ tìm kiếm của chat lõi nên tôn trọng cài đặt
     * "ai được nhắn cho tôi" và việc chặn người dùng.
     *
     * @param int $userid Người đang tìm.
     * @param string $query
     * @return array[] Mỗi phần tử: id, fullname, profileimageurlsmall.
     */
    public static function search_users(int $userid, string $query): array {
        self::require_messaging();
        $query = trim($query);
        if (\core_text::strlen($query) < 2) {
            return [];
        }
        [$contacts, $noncontacts] = messageapi::message_search_users($userid, $query, 0, 15);
        $result = [];
        foreach (array_merge($contacts, $noncontacts) as $user) {
            $id = (int) $user->id;
            if ($id === $userid || isset($result[$id])) {
                continue;
            }
            $result[$id] = [
                'id' => $id,
                'fullname' => $user->fullname,
                'profileimageurlsmall' => $user->profileimageurlsmall ?? ($user->profileimageurl ?? ''),
            ];
        }
        return array_values($result);
    }
}
