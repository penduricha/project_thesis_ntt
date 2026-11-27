# local_nttchat — chat đơn và chat nhóm cho Moodle 5.3 (NTTU)

Plugin xây **trên** hệ thống nhắn tin có sẵn của Moodle (`core_message`), không thay thế nó:

| Tính năng | Cách làm |
|---|---|
| Chat đơn (1-1) | Dùng nguyên chat đơn của lõi (ngăn kéo tin nhắn bên phải). Plugin chỉ thêm **thông báo tin mới theo thời gian thực** qua Redis. |
| Chat nhóm do người dùng tự tạo | Trang **Nhóm chat** (mục trong menu tài khoản, `/local/nttchat/index.php`): tạo nhóm, mời người, đổi tên, thêm/xoá thành viên, rời, giải tán. Mỗi nhóm là một hội thoại nhóm của lõi nên tin nhắn nằm trong cùng ngăn kéo chat. |
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
    'db'      => 3,            // KHÔNG dùng DB 1: Spring Boot đang dùng DB 1.
    'prefix'  => 'nttchat:',
    'timeout' => 0.3,
];

// (Tuỳ chọn) lưu phiên đăng nhập Moodle trong Redis.
$CFG->session_handler_class = '\core\session\redis';
$CFG->session_redis_host = '127.0.0.1';
$CFG->session_redis_port = 6379;
$CFG->session_redis_database = 2;
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

## Kiểm thử đã chạy

Trên Moodle 5.3 + MariaDB + Redis thật: 43 kiểm tra PHP (tạo nhóm, phân quyền, Redis, chat đơn, Redis tắt vẫn gửi được)
và 14 kiểm tra trình duyệt (Chromium, hai người dùng, thông báo realtime, mở đúng cuộc chat, không lỗi JS).
