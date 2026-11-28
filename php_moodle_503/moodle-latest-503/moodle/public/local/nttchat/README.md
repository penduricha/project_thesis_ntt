# local_nttchat — chat đơn và chat nhóm cho Moodle 5.3 (NTTU)

Plugin xây **trên** hệ thống nhắn tin có sẵn của Moodle (`core_message`), không thay thế nó:

| Tính năng | Cách làm |
|---|---|
| Chat đơn (1-1) | Dùng nguyên chat đơn của lõi (ngăn kéo tin nhắn bên phải). Plugin chỉ thêm **thông báo tin mới theo thời gian thực** qua Redis. |
| Chat nhóm do người dùng tự tạo | Trang **Nhóm chat** (mục trong menu tài khoản, `/local/nttchat/index.php`): tạo nhóm, mời người, đổi tên, thêm/xoá thành viên, rời, giải tán. Mỗi nhóm là một hội thoại nhóm của lõi nên tin nhắn nằm trong cùng ngăn kéo chat. |
| Phòng chat theo khoá học (tự động) | Mỗi khoá học có một phòng nhóm, thành viên đi theo danh sách ghi danh (xem mục "Phòng chat tự động theo khoá học"). |
| Thông báo realtime | Khi có tin mới, plugin ghi một dòng vào hộp thư Redis của từng người nhận; trình duyệt hỏi hộp thư mỗi vài giây (mặc định 4s) và hiện thông báo, bấm vào là mở đúng cuộc chat. |

Không có bảng riêng trong MariaDB. Gỡ plugin chỉ cần gỡ thư mục; nâng cấp Moodle không mất dữ liệu chat.

## Redis

Redis chỉ dùng cho **tín hiệu tin mới** (và có thể cho session/cache của Moodle). Nếu Redis tắt hoặc không cấu hình, **gửi và nhận tin vẫn hoạt động bình thường** (chỉ mất thông báo tức thời).

Thêm vào `config.php` **cục bộ** của Moodle, trước dòng `require_once(__DIR__ . '/lib/setup.php');`.
**Không commit mật khẩu** (đặt mật khẩu thật ở máy của bạn):

```php
// Hộp thư thông báo chat (plugin local_nttchat).
$CFG->local_nttchat_redis = [
    'host'    => '127.0.0.1',
    'port'    => 6379,
    'user'    => 'default',
    'pass'    => '<mật-khẩu-redis>',
    'db'      => 3,            // DB 1 = Spring (be-web-admin), DB 2 = ai-worker (deploy/docker-compose.yml): không dùng lại.
    'prefix'  => 'nttchat:',
    'timeout' => 0.3,
];

// (Tuỳ chọn) lưu phiên đăng nhập Moodle trong Redis.
$CFG->session_handler_class = '\core\session\redis';
$CFG->session_redis_host = '127.0.0.1';
$CFG->session_redis_port = 6379;
$CFG->session_redis_database = 4;   // khác DB của Spring (1), ai-worker (2) và chat (3)
$CFG->session_redis_prefix = 'mdlsess_';
$CFG->session_redis_auth = '<mật-khẩu-redis>';
```

Cần PHP extension `redis` (`php -m | grep redis`). Trang cài đặt của plugin
(*Site administration → Plugins → Local plugins → NTT chat groups*) hiện trạng thái kết nối Redis.

## Cài đặt

1. Chép thư mục này vào `public/local/nttchat/`.
2. `php admin/cli/upgrade.php --non-interactive`
3. `php admin/cli/purge_caches.php`
4. Bảo đảm **Site administration → Advanced features → Enable messaging** đang bật.

## Quy ước phân quyền (nhóm do người dùng tạo)

- Chỉ **chủ nhóm** đổi tên, thêm/xoá thành viên, giải tán nhóm. Thành viên nào cũng rời được.
- Chủ nhóm rời đi thì quyền chuyển cho người vào nhóm sớm nhất; hết người thì nhóm bị xoá.
- Chỉ thêm được người mà chính họ cho phép nhận tin (cài đặt quyền riêng tư và danh sách chặn của lõi).
  Ô tìm người dùng đúng bộ tìm kiếm của chat lõi: mặc định chỉ thấy người **cùng khoá học**.
- Quyền `local/nttchat:creategroup` (mặc định cho mọi người dùng đã đăng nhập, trừ khách).
- Giới hạn trong trang cài đặt của plugin: số thành viên/nhóm, số nhóm/người, chu kỳ hỏi tin mới.

## Phòng chat tự động theo khoá học

- Mỗi khoá học có **một** phòng chat nhóm khi có từ **2 người** ghi danh trở lên. Tên phòng = tên khoá học.
- Thành viên = người **ghi danh còn hiệu lực** (không tạm ngưng, chưa hết hạn) và tài khoản đang hoạt động
  (không bị khoá, không bị xoá, không phải khách). Ghi danh, huỷ ghi danh, tạm ngưng, đổi tên hay xoá khoá học
  đều cập nhật phòng ngay (bằng sự kiện).
- Tác vụ định kỳ `local_nttchat\task\sync_course_rooms` (mỗi giờ, phút 23) bắt các trường hợp không có sự kiện:
  ghi danh hết hạn theo thời gian, tài khoản bị khoá/mở khoá; đồng thời dọn phòng mồ côi. Cần cron của Moodle
  chạy (`php admin/cli/cron.php`).
- Phòng **chỉ đọc** với người dùng: không đổi tên, thêm, bớt, giải tán hay rời được; danh sách thành viên do
  ghi danh quyết định. Muốn bớt thông báo thì dùng "Tắt thông báo" của lõi trong ngăn kéo tin nhắn.
- Nhận biết trong CSDL: `message_conversations.component = 'local_nttchat'` và `itemid = 0`; `contextid` là
  ngữ cảnh khoá học. Nhóm do người dùng tạo luôn có `itemid` > 0 nên hai loại không lẫn nhau.
- Tắt: *Site administration → Plugins → Local plugins → NTT chat groups → Automatic course chat rooms*
  (tắt thì phòng đã có giữ nguyên, chỉ ngừng đồng bộ). Cũng tắt nếu site tắt *Enable messaging*.
- Nâng cấp lên 1.1.0 (`php admin/cli/upgrade.php --non-interactive`) tự dựng phòng cho mọi khoá học đã có.

## Kiểm thử đã chạy

Trên Moodle 5.3 + MariaDB + Redis thật: 43 kiểm tra PHP (tạo nhóm, phân quyền, Redis, chat đơn, Redis tắt vẫn gửi được),
56 kiểm tra PHP riêng cho phòng khoá học (ghi danh/huỷ/tạm ngưng/hết hạn, đồng bộ lặp lại, chỉ-đọc, xoá khoá học,
phòng mồ côi, bật/tắt) và 24 kiểm tra trình duyệt (Chromium, hai người dùng, thông báo realtime, mở đúng cuộc chat,
phòng khoá học trên giao diện, không lỗi JS).
