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
 * Hàm web service của plugin local_nttchat. Chỉ dùng qua AJAX của trình duyệt (phiên đăng nhập + sesskey),
 * không đưa vào dịch vụ bên ngoài.
 *
 * @package    local_nttchat
 * @copyright  2026 NTTU
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */

defined('MOODLE_INTERNAL') || die();

$class = 'local_nttchat\external\chat_api';

$functions = [
    'local_nttchat_get_groups' => [
        'classname' => $class,
        'methodname' => 'get_groups',
        'description' => 'Các nhóm chat người dùng đang ở trong.',
        'type' => 'read',
        'ajax' => true,
        'readonlysession' => true,
        'capabilities' => 'local/nttchat:creategroup',
    ],
    'local_nttchat_create_group' => [
        'classname' => $class,
        'methodname' => 'create_group',
        'description' => 'Tạo nhóm chat mới.',
        'type' => 'write',
        'ajax' => true,
        'capabilities' => 'local/nttchat:creategroup',
    ],
    'local_nttchat_rename_group' => [
        'classname' => $class,
        'methodname' => 'rename_group',
        'description' => 'Đổi tên nhóm chat (chủ nhóm).',
        'type' => 'write',
        'ajax' => true,
        'capabilities' => 'local/nttchat:creategroup',
    ],
    'local_nttchat_add_members' => [
        'classname' => $class,
        'methodname' => 'add_members',
        'description' => 'Thêm thành viên vào nhóm chat (chủ nhóm).',
        'type' => 'write',
        'ajax' => true,
        'capabilities' => 'local/nttchat:creategroup',
    ],
    'local_nttchat_remove_member' => [
        'classname' => $class,
        'methodname' => 'remove_member',
        'description' => 'Xoá thành viên khỏi nhóm chat (chủ nhóm).',
        'type' => 'write',
        'ajax' => true,
        'capabilities' => 'local/nttchat:creategroup',
    ],
    'local_nttchat_leave_group' => [
        'classname' => $class,
        'methodname' => 'leave_group',
        'description' => 'Rời nhóm chat.',
        'type' => 'write',
        'ajax' => true,
        'capabilities' => 'local/nttchat:creategroup',
    ],
    'local_nttchat_delete_group' => [
        'classname' => $class,
        'methodname' => 'delete_group',
        'description' => 'Giải tán nhóm chat và xoá tin nhắn (chủ nhóm).',
        'type' => 'write',
        'ajax' => true,
        'capabilities' => 'local/nttchat:creategroup',
    ],
    'local_nttchat_search_users' => [
        'classname' => $class,
        'methodname' => 'search_users',
        'description' => 'Tìm người để nhắn tin hoặc thêm vào nhóm.',
        'type' => 'read',
        'ajax' => true,
        'readonlysession' => true,
        'capabilities' => 'local/nttchat:creategroup',
    ],
    'local_nttchat_get_updates' => [
        'classname' => $class,
        'methodname' => 'get_updates',
        'description' => 'Tin nhắn mới kể từ một mốc, đọc từ hộp thư Redis.',
        'type' => 'read',
        'ajax' => true,
        'readonlysession' => true,
        'capabilities' => 'local/nttchat:creategroup',
    ],
];
