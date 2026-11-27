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

defined('MOODLE_INTERNAL') || die();

/**
 * Hộp thư báo tin mới trên Redis: mỗi người dùng một Redis Stream ngắn hạn.
 *
 * Tin nhắn thật luôn nằm trong MariaDB do core_message quản lý. Redis chỉ giữ "thông báo có tin mới"
 * (ai gửi, đoạn xem trước, id hội thoại) tối đa {@see self::MAXLEN} mục và {@see self::TTL} giây để trình duyệt hỏi
 * nhanh mà không phải truy vấn MariaDB. Redis hỏng hoặc chưa cấu hình thì mọi hàm ở đây trả rỗng, không ném lỗi,
 * chat vẫn chạy bình thường bằng cơ chế nạp định kỳ của lõi.
 *
 * Cấu hình đặt trong config.php (KHÔNG commit, vì có mật khẩu):
 *
 *   $CFG->local_nttchat_redis = [
 *       'host' => '127.0.0.1', 'port' => 6379,
 *       'user' => 'default', 'pass' => '...',   // user để trống nếu Redis chỉ dùng requirepass
 *       'db' => 3, 'prefix' => 'nttchat:', 'timeout' => 0.3,
 *   ];
 *
 * @package    local_nttchat
 * @copyright  2026 NTTU
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */
class redis_store {
    /** @var int Số mục tối đa giữ trong stream của mỗi người dùng. */
    public const MAXLEN = 100;

    /** @var int Thời gian sống của stream (giây) tính từ lần ghi cuối: 1 ngày. */
    public const TTL = 86400;

    /** @var self|null Kết nối dùng chung trong một request. */
    private static ?self $instance = null;

    /** @var bool Đã thử kết nối và thất bại trong request này (khỏi thử lại, tránh chờ timeout nhiều lần). */
    private static bool $failed = false;

    /** @var \Redis */
    private \Redis $redis;

    /** @var string */
    private string $prefix;

    /**
     * Đã khai báo cấu hình Redis chưa (không kết nối, nên gọi được ở mọi trang mà không tốn thời gian).
     *
     * @return bool
     */
    public static function is_configured(): bool {
        global $CFG;
        return class_exists(\Redis::class) && !empty($CFG->local_nttchat_redis['host']);
    }

    /**
     * Kết nối dùng chung, hoặc null nếu chưa cấu hình / Redis không dùng được.
     *
     * @return self|null
     */
    public static function instance(): ?self {
        global $CFG;
        if (self::$instance !== null) {
            return self::$instance;
        }
        if (self::$failed || !self::is_configured()) {
            return null;
        }
        $config = $CFG->local_nttchat_redis;
        try {
            $redis = new \Redis();
            $redis->connect(
                (string) $config['host'],
                (int) ($config['port'] ?? 6379),
                (float) ($config['timeout'] ?? 0.3)
            );
            $user = (string) ($config['user'] ?? '');
            $pass = (string) ($config['pass'] ?? '');
            if ($pass !== '') {
                // Redis 6+ có ACL: user khác "default" phải gửi cả tên. "default" + mật khẩu tương đương requirepass.
                $redis->auth($user !== '' && $user !== 'default' ? [$user, $pass] : $pass);
            }
            $redis->select((int) ($config['db'] ?? 0));
            $redis->setOption(\Redis::OPT_READ_TIMEOUT, (float) ($config['timeout'] ?? 0.3) * 4);
            self::$instance = new self($redis, (string) ($config['prefix'] ?? 'nttchat:'));
        } catch (\Throwable $e) {
            self::$failed = true;
            debugging('local_nttchat: không kết nối được Redis: ' . $e->getMessage(), DEBUG_DEVELOPER);
            return null;
        }
        return self::$instance;
    }

    /**
     * Bỏ kết nối đang giữ (dùng khi kiểm thử hoặc sau khi đổi cấu hình).
     */
    public static function reset(): void {
        self::$instance = null;
        self::$failed = false;
    }

    /**
     * @param \Redis $redis
     * @param string $prefix
     */
    private function __construct(\Redis $redis, string $prefix) {
        $this->redis = $redis;
        $this->prefix = $prefix;
    }

    /**
     * Khoá stream của một người dùng.
     *
     * @param int $userid
     * @return string
     */
    private function key(int $userid): string {
        return $this->prefix . 'inbox:' . $userid;
    }

    /**
     * Cộng 1 vào phần thứ tự của id stream, để đọc "sau id này" mà Redis cũ (< 6.2) cũng hiểu.
     *
     * @param string $id Dạng "mili-giây-thứ tự".
     * @return string
     */
    private static function after(string $id): string {
        if (!preg_match('/^(\d+)-(\d+)$/', $id, $m)) {
            return '0-0';
        }
        return $m[1] . '-' . ((int) $m[2] + 1);
    }

    /**
     * Ghi một thông báo vào hộp thư của người dùng.
     *
     * @param int $userid Người nhận.
     * @param string[] $fields Chỉ chuỗi (Redis Stream không có kiểu khác).
     * @return string|null Id mục vừa ghi, null nếu Redis lỗi.
     */
    public function push(int $userid, array $fields): ?string {
        try {
            $key = $this->key($userid);
            $id = $this->redis->xAdd($key, '*', array_map('strval', $fields), self::MAXLEN, true);
            $this->redis->expire($key, self::TTL);
            return $id ?: null;
        } catch (\Throwable $e) {
            debugging('local_nttchat: ghi Redis lỗi: ' . $e->getMessage(), DEBUG_DEVELOPER);
            return null;
        }
    }

    /**
     * Id mục mới nhất, hoặc '0-0' nếu hộp thư trống.
     *
     * @param int $userid
     * @return string|null null nếu Redis lỗi.
     */
    public function latest_id(int $userid): ?string {
        try {
            $rows = $this->redis->xRevRange($this->key($userid), '+', '-', 1);
            return $rows ? (string) array_key_first($rows) : '0-0';
        } catch (\Throwable $e) {
            return null;
        }
    }

    /**
     * Các mục có id lớn hơn $afterid, cũ trước.
     *
     * @param int $userid
     * @param string $afterid
     * @param int $count
     * @return array[]|null Mỗi phần tử: ['id' => ..., 'fields' => [...]]; null nếu Redis lỗi.
     */
    public function read(int $userid, string $afterid, int $count = 50): ?array {
        try {
            $rows = $this->redis->xRange($this->key($userid), self::after($afterid), '+', $count);
        } catch (\Throwable $e) {
            return null;
        }
        $result = [];
        foreach ($rows ?: [] as $id => $fields) {
            $result[] = ['id' => (string) $id, 'fields' => $fields];
        }
        return $result;
    }

    /**
     * Gửi lệnh PING để kiểm tra kết nối (dùng ở trang cài đặt và CLI).
     *
     * @return bool
     */
    public function ping(): bool {
        try {
            $reply = $this->redis->ping();
            return $reply === true || (is_string($reply) && stripos($reply, 'PONG') !== false) || $reply === '+PONG';
        } catch (\Throwable $e) {
            return false;
        }
    }
}
