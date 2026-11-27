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
 * Trang "Nhóm chat": tạo / quản lý nhóm chat và bắt đầu chat đơn. Mọi thao tác chạy bằng AJAX (xem amd/src/groups.js).
 *
 * @package    local_nttchat
 * @copyright  2026 NTTU
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */

require_once(__DIR__ . '/../../config.php');

require_login(null, false);

$context = context_system::instance();
require_capability('local/nttchat:creategroup', $context);

$PAGE->set_context($context);
$PAGE->set_url(new moodle_url('/local/nttchat/index.php'));
$PAGE->set_pagelayout('standard');
$PAGE->set_title(get_string('pagetitle', 'local_nttchat'));
$PAGE->set_heading(get_string('pagetitle', 'local_nttchat'));

echo $OUTPUT->header();

if (empty($CFG->messaging)) {
    echo $OUTPUT->notification(get_string('messagingdisabled', 'local_nttchat'), \core\output\notification::NOTIFY_WARNING);
} else {
    echo $OUTPUT->render_from_template('local_nttchat/groups', []);
    $PAGE->requires->js_call_amd('local_nttchat/groups', 'init', [[
        'userid' => (int) $USER->id,
    ]]);
}

echo $OUTPUT->footer();
