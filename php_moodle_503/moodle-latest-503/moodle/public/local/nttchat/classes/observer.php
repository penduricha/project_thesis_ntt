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
 * Bắt sự kiện tin nhắn mới của lõi và báo vào hộp thư Redis của từng người nhận.
 *
 * Chạy ngay trong request của người gửi, nên mọi lỗi (Redis tắt, mất mạng) đều bị nuốt: gửi tin không bao giờ
 * được thất bại vì Redis.
 *
 * @package    local_nttchat
 * @copyright  2026 NTTU
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */
class observer {
    /** @var int Độ dài tối đa đoạn xem trước tin nhắn. */
    public const PREVIEW_LENGTH = 120;

    /**
     * Tin nhắn đơn (1-1): người nhận là relateduserid.
     *
     * @param \core\event\message_sent $event
     */
    public static function message_sent(\core\event\message_sent $event): void {
        $recipient = (int) $event->relateduserid;
        $sender = (int) $event->userid;
        if ($recipient <= 0 || $sender <= 0 || $recipient === $sender) {
            return;
        }
        self::notify([$recipient], (int) $event->objectid, 'user');
    }

    /**
     * Tin nhắn nhóm: mọi thành viên trừ người gửi và những người đã tắt thông báo hội thoại này.
     *
     * @param \core\event\group_message_sent $event
     */
    public static function group_message_sent(\core\event\group_message_sent $event): void {
        global $DB;
        $sender = (int) $event->userid;
        $conversationid = (int) $event->other['conversationid'];
        if ($sender <= 0 || $conversationid <= 0 || !redis_store::is_configured()) {
            return;
        }
        $sql = "SELECT m.userid
                  FROM {message_conversation_members} m
             LEFT JOIN {message_conversation_actions} a
                    ON a.conversationid = m.conversationid AND a.userid = m.userid AND a.action = :muted
                 WHERE m.conversationid = :conversationid AND m.userid <> :sender AND a.id IS NULL";
        $recipients = $DB->get_fieldset_sql($sql, [
            'muted' => messageapi::CONVERSATION_ACTION_MUTED,
            'conversationid' => $conversationid,
            'sender' => $sender,
        ]);
        self::notify(array_map('intval', $recipients), (int) $event->objectid, 'group');
    }

    // -----------------------------------------------------------------------------------------------
    // Phòng chat tự động theo khoá học (xem course_room_service). Mọi lỗi đều bị nuốt: ghi danh, sửa hay xoá
    // khoá học không bao giờ được thất bại vì chat.
    // -----------------------------------------------------------------------------------------------

    /**
     * Ghi danh được tạo / đổi (tạm ngưng, hết hạn...) / xoá: đưa người đó vào hoặc ra khỏi phòng khoá học.
     *
     * @param \core\event\base $event user_enrolment_created | user_enrolment_updated | user_enrolment_deleted
     */
    public static function enrolment_changed(\core\event\base $event): void {
        try {
            course_room_service::sync_user((int) $event->courseid, (int) $event->relateduserid);
        } catch (\Throwable $e) {
            debugging('local_nttchat: không cập nhật được phòng khoá học: ' . $e->getMessage(), DEBUG_DEVELOPER);
        }
    }

    /**
     * Khoá học được sửa: đổi tên phòng theo tên mới.
     *
     * @param \core\event\course_updated $event
     */
    public static function course_updated(\core\event\course_updated $event): void {
        try {
            course_room_service::rename_room((int) $event->courseid);
        } catch (\Throwable $e) {
            debugging('local_nttchat: không đổi được tên phòng khoá học: ' . $e->getMessage(), DEBUG_DEVELOPER);
        }
    }

    /**
     * Khoá học bị xoá: xoá phòng và tin nhắn của phòng (không để hội thoại mồ côi).
     *
     * @param \core\event\course_deleted $event
     */
    public static function course_deleted(\core\event\course_deleted $event): void {
        try {
            course_room_service::delete_room((int) $event->objectid, (int) $event->contextid);
        } catch (\Throwable $e) {
            debugging('local_nttchat: không xoá được phòng khoá học: ' . $e->getMessage(), DEBUG_DEVELOPER);
        }
    }

    /**
     * Ghi thông báo cho từng người nhận.
     *
     * @param int[] $recipients
     * @param int $messageid
     * @param string $type 'user' (chat đơn) hoặc 'group' (chat nhóm).
     */
    protected static function notify(array $recipients, int $messageid, string $type): void {
        global $DB;
        if (!$recipients || !redis_store::is_configured() || !get_config('local_nttchat', 'realtime')) {
            return;
        }
        try {
            $store = redis_store::instance();
            if (!$store) {
                return;
            }
            $message = $DB->get_record('messages', ['id' => $messageid], 'id, useridfrom, conversationid, fullmessage, smallmessage, timecreated');
            if (!$message) {
                return;
            }
            $from = $DB->get_record('user', ['id' => $message->useridfrom], implode(',', \core_user\fields::get_name_fields()) . ',id');
            $groupname = '';
            if ($type === 'group') {
                $groupname = (string) $DB->get_field('message_conversations', 'name', ['id' => $message->conversationid]);
            }
            $text = $message->smallmessage !== null && $message->smallmessage !== '' ? $message->smallmessage : $message->fullmessage;
            $text = trim(preg_replace('/\s+/u', ' ', strip_tags((string) $text)));
            $fields = [
                'type' => $type,
                'cid' => (int) $message->conversationid,
                'mid' => (int) $message->id,
                'from' => (int) $message->useridfrom,
                'fromname' => $from ? fullname($from) : '',
                'group' => $groupname,
                'text' => \core_text::substr($text, 0, self::PREVIEW_LENGTH),
                'time' => (int) $message->timecreated,
            ];
            foreach ($recipients as $userid) {
                $store->push($userid, $fields);
            }
        } catch (\Throwable $e) {
            debugging('local_nttchat: không báo được tin mới: ' . $e->getMessage(), DEBUG_DEVELOPER);
        }
    }
}
