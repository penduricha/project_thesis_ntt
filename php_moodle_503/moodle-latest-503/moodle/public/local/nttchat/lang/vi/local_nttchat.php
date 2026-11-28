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
 * Chuỗi tiếng Việt cho local_nttchat.
 *
 * @package    local_nttchat
 * @copyright  2026 NTTU
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */

defined('MOODLE_INTERNAL') || die();

$string['pluginname'] = 'Nhóm chat NTT';
$string['nttchatgroups'] = 'Nhóm chat';
$string['nttchat:creategroup'] = 'Tạo và quản lý nhóm chat của mình';

// Trang.
$string['pagetitle'] = 'Nhóm chat';
$string['pageintro'] = 'Mỗi khoá học có sẵn một phòng chat tự động. Bạn cũng có thể tự tạo nhóm, thêm hoặc bớt thành viên và trò chuyện ngay trong ngăn kéo tin nhắn. Tin nhắn nhóm là tin nhắn Moodle bình thường.';
$string['creategroup'] = 'Tạo nhóm';
$string['newgroup'] = 'Nhóm chat mới';
$string['groupname'] = 'Tên nhóm';
$string['groupnameplaceholder'] = 'Ví dụ: Nhóm ôn TOEIC Part 5';
$string['searchmembers'] = 'Thêm thành viên';
$string['searchplaceholder'] = 'Gõ ít nhất 2 ký tự của tên';
$string['nosearchresults'] = 'Không tìm thấy ai.';
$string['selectedmembers'] = 'Thành viên đã chọn';
$string['nomembersselected'] = 'Chưa chọn thành viên nào.';
$string['yourgroups'] = 'Nhóm của bạn';
$string['nogroups'] = 'Bạn chưa ở trong nhóm chat nào.';
$string['membercount'] = '{$a} thành viên';
$string['owner'] = 'Chủ nhóm';
$string['openchat'] = 'Mở chat';
$string['manage'] = 'Quản lý';
$string['closemanage'] = 'Đóng';
$string['save'] = 'Lưu';
$string['cancel'] = 'Huỷ';
$string['add'] = 'Thêm';
$string['remove'] = 'Xoá';
$string['members'] = 'Thành viên';
$string['leavegroup'] = 'Rời nhóm';
$string['leaveconfirm'] = 'Rời nhóm này? Bạn sẽ không nhận tin nhắn của nhóm nữa.';
$string['deletegroup'] = 'Giải tán nhóm';
$string['deleteconfirm'] = 'Giải tán nhóm và xoá toàn bộ tin nhắn của nhóm với mọi người? Không hoàn tác được.';
$string['removeconfirm'] = 'Xoá {$a} khỏi nhóm này?';
$string['directmessage'] = 'Nhắn tin riêng';
$string['directmessagehelp'] = 'Tìm một người và bắt đầu cuộc trò chuyện riêng.';
$string['sendmessage'] = 'Nhắn tin';
$string['messagingdisabled'] = 'Trang web đang tắt tính năng nhắn tin.';

// Lỗi.
$string['invalidname'] = 'Tên nhóm phải từ 1 đến {$a} ký tự.';
$string['invaliduser'] = 'Không thể thêm người dùng {$a} vào nhóm.';
$string['cannotaddmember'] = '{$a} không nhận tin nhắn từ bạn nên không thể thêm vào nhóm.';
$string['needmembers'] = 'Hãy chọn ít nhất một thành viên khác.';
$string['toomanymembers'] = 'Một nhóm có tối đa {$a} thành viên.';
$string['toomanygroups'] = 'Bạn chỉ được làm chủ tối đa {$a} nhóm.';
$string['groupnotfound'] = 'Không tìm thấy nhóm.';
$string['onlyowner'] = 'Chỉ chủ nhóm mới làm được việc này.';
$string['cannotremoveowner'] = 'Không thể xoá chủ nhóm. Hãy rời nhóm hoặc giải tán nhóm.';
$string['notmember'] = 'Người này không ở trong nhóm.';

// Báo tin mới theo thời gian thực.
$string['newmessagefrom'] = 'Tin nhắn mới từ {$a}';
$string['newgroupmessage'] = '{$a->from} trong {$a->group}';

// Cài đặt.
$string['settingsrealtime'] = 'Báo tin nhắn mới theo thời gian thực';
$string['settingsrealtime_desc'] = 'Hiện thông báo và cập nhật số tin chưa đọc khi có tin mới. Cần Redis (xem README).';
$string['settingspollinterval'] = 'Chu kỳ kiểm tra (giây)';
$string['settingspollinterval_desc'] = 'Mỗi tab đang mở hỏi tin mới theo chu kỳ này. Khi không có gì mới, mỗi lần hỏi chỉ tốn một lệnh Redis. Từ 2 đến 60.';
$string['settingsmaxgroupmembers'] = 'Số thành viên tối đa mỗi nhóm';
$string['settingsmaxgroupsperuser'] = 'Số nhóm tối đa một người được làm chủ';
$string['redisstatus'] = 'Kết nối Redis';
$string['redisstatus_ok'] = 'Đã kết nối.';
$string['redisstatus_notconfigured'] = 'Chưa cấu hình. Thêm $CFG->local_nttchat_redis vào config.php (xem README của plugin).';
$string['redisstatus_noext'] = 'Chưa cài extension "redis" của PHP.';
$string['redisstatus_failed'] = 'Đã cấu hình nhưng không kết nối được máy chủ. Chat vẫn chạy; thông báo sẽ chậm theo chu kỳ làm mới của lõi.';

// Phòng chat khoá học.
$string['courseroom'] = 'Khoá học';
$string['courseroomhelp'] = 'Mọi người đang ghi danh trong khoá học này tự động ở trong phòng.';
$string['courseroomreadonly'] = 'Phòng chat của khoá học đi theo danh sách ghi danh nên không sửa được ở đây.';
$string['courseroomleave'] = 'Bạn không rời được phòng chat của khoá học khi còn ghi danh. Dùng "Tắt thông báo" trong ngăn kéo tin nhắn nếu không muốn nhận thông báo.';
$string['settingscourserooms'] = 'Phòng chat tự động theo khoá học';
$string['settingscourserooms_desc'] = 'Mỗi khoá học có một phòng chat nhóm, thành viên là những người đang ghi danh còn hiệu lực. Thành viên tự đổi theo ghi danh. Tắt đi thì giữ nguyên các phòng đã có nhưng không cập nhật nữa.';
$string['tasksynccourserooms'] = 'Đồng bộ phòng chat khoá học với danh sách ghi danh';

// Quyền riêng tư.
$string['privacy:metadata'] = 'Plugin không lưu dữ liệu cá nhân của riêng nó. Nhóm chat do hệ thống nhắn tin của lõi lưu. Thông báo tin mới tạm thời (người gửi, đoạn xem trước tối đa 120 ký tự) chỉ nằm trong Redis tối đa một ngày.';
