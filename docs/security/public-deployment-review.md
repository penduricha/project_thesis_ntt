# Rà soát an toàn trước khi công khai — chưa đủ điều kiện phát hành

Phạm vi: rà tĩnh checkout hiện tại của backend Java, Flutter, Vue và hai AI worker;
không quét hay kiểm thử hệ thống đang chạy. Không phải chứng nhận ATTT hoặc pentest.
Không sửa `deploy/`, không deploy và không áp thay đổi lên CSDL thật.

## Các chặn phát hành

1. **Auth/khôi phục chưa hoàn chỉnh.** Xem `auth-recovery-plan.md`: đã bắt đầu staged key,
   đối chiếu credential bằng TOTP và khóa DB, nhưng receipt idempotent và đồng bộ đầy đủ
   confirm/cancel/disable chưa hoàn tất; mã khôi phục, takeover và refresh gắn bằng chứng sở hữu
   chưa triển khai. Chạy test cạnh tranh, lỗi từng bước, restart, mất response, replay,
   upgrade và phục hồi mất thiết bị trước khi cho người dùng thật. Bản vá kiểm tra cờ
   `totpEnabled` khi không có credential là fail-closed, không giải quyết race giữa các giao dịch.
   Cờ `null` không có credential cũng bị từ chối: kiểm kê dữ liệu legacy và xử lý qua quy trình
   xác minh danh tính được phê duyệt, không tự đặt lại cờ hàng loạt hay xóa 2FA.
2. **Bảo vệ thông tin bí mật.** Tạo khóa JWT, khóa mã hóa TOTP và internal token ngẫu nhiên
   riêng từng môi trường; lưu bằng secret manager, không nhúng vào Vue, Flutter, image hay log.
   `.env` Flutter được đóng gói vào ứng dụng và không phải kho bí mật. Bất kỳ credential từng
   được chia sẻ hay xuất hiện trong Git cần được coi là có thể lộ và thu hồi/rotate nếu là thật;
   xóa khỏi source hiện tại không xóa lịch sử. Lập kế hoạch rotate khóa mã hóa TOTP có version,
   backup và kiểm tra giải mã trước khi bỏ khóa cũ; rotate JWT có thể làm mất phiên. Vue đã
   bỏ log đối tượng lỗi auth/2FA (có thể chứa header/body) và redacted `AdminAccount.toString`;
   có test canary cho các đường xử lý đó. Đây không phải kiểm kê toàn bộ log hay chứng nhận
   không còn rò rỉ bí mật ở các thành phần khác.
3. **Transport và mạng.** Chỉ công khai HTTPS/WSS với chứng chỉ hợp lệ; chuyển HTTP sang HTTPS,
   cấu hình HSTS sau khi xác nhận toàn bộ miền/subdomain hỗ trợ HTTPS. Không mở trực tiếp Redis,
   MariaDB, worker, endpoint demo hay cổng nội bộ ra Internet; áp dụng firewall/segmentation,
   ACL và tài khoản DB tối thiểu. Worker/backend liên lạc qua mạng tin cậy hoặc TLS/mTLS; token
   nội bộ không bảo vệ tính bí mật khi truyền qua HTTP trên mạng không tin cậy.
4. **Mặc định không phù hợp production.** `application.yaml` backend và cả hai worker có
   `JPA_DDL_AUTO:update`; worker còn có thông số DB demo và tự seed câu demo khi bảng trống.
   Cần chọn một worker chính thức, rà các profile, tắt seed demo cả `DemoQuestionSeeder` lẫn
   `AI_DEMO_SEED_ISSUE_TYPES` và triển khai migration được duyệt; dùng `validate` sau migration.
   Chỉ đặt `AI_DEMO_SEED_ISSUE_TYPES=false` không tắt `DemoQuestionSeeder`. Đã sửa đường dẫn
   `.env` của `be-server-ai` và ưu tiên environment hơn `.env` ở ba module; cần test thứ tự
   cấu hình thực tế trước rollout, đặc biệt nếu môi trường cũ dựa vào cách ghi đè trước đây.
5. **Reverse proxy và IP.** Backend mặc định `forward-headers-strategy: native`; không cho phép
   client gửi trực tiếp tới backend hoặc tự quyết định forwarded headers. Proxy phải xóa/ghi đè
   header từ client, chỉ tin IP proxy được cấu hình và test giả mạo X-Forwarded-For. Rate limit
   theo IP có thể dùng chung địa chỉ proxy nếu cấu hình sai; vẫn cần limit theo tài khoản và
   bảo vệ login tại edge. Không mặc định tin mọi proxy nội bộ.
6. **SSE và refresh.** Stream 2FA hiện đăng ký theo admin, chưa kiểm lại phiên khi gửi/heartbeat;
   phiên bị thu hồi vẫn có thể nhận sự kiện cho tới khi stream đóng. Registry cũng chỉ ở một
   instance. Fix binding/revalidation, test revoke và multi-instance trước khi công khai.
   Web lưu bearer access/refresh trong localStorage (`TokenLogin.js`): XSS có thể lấy token;
   CSP nghiêm ngặt và rà XSS giúp giảm nguy cơ nhưng không thay thế thiết kế BFF/HttpOnly hay
   sender binding. Refresh hiện vẫn có rủi ro mất phiên khi mất response.
7. **Mobile release.** Cần cấu hình URL Spring HTTPS thật, không chứa credential trong asset;
   bản vá hiện chặn cleartext Android release và yêu cầu URL HTTPS rõ ràng ở Flutter release.
   Cần build và kiểm tra manifest gộp, Android/iOS thực tế, chứng chỉ, kết nối WSS và hành vi
   lỗi cấu hình. Không tắt xác minh chứng chỉ; TLS trên frontend không chứng minh TLS nội bộ.
8. **CORS, XSS, phân quyền.** Chỉ cho phép origin HTTPS chính xác được vận hành, không wildcard;
   CORS không thay thế xác thực. Kiểm tra cả WebSocket origin và endpoint theo vai trò/ownership.
   Thiết lập và test CSP, chống framing, `nosniff`, referrer policy và cache-control thích hợp
   tại proxy; tránh lưu token/mã TOTP vào URL hoặc log. Kiểm tra upload/import, rendering dữ liệu
   người dùng, kiểm soát kích thước và rate limit. Lưu ý SVG `v-html` chỉ an toàn nếu nguồn thực
   sự là hằng số đáng tin cậy.
9. **Vận hành và sự cố.** Backup mã hóa có kiểm thử restore; log audit không chứa token, OTP,
   khóa, mật khẩu, payload nhạy cảm; cảnh báo đăng nhập bất thường, khóa, 2FA và lỗi Redis/DB.
   Dọn nháp sau DB commit hiện là best-effort và chưa có version/CAS: vẫn cần xử lý nháp mới
   được ghi đồng thời và dọn thất bại, cùng test tích hợp. Thiết lập theo dõi, giới hạn tài nguyên, dependency/SBOM và quét lỗ hổng, rà secrets toàn bộ
   lịch sử Git, kế hoạch vá, thu hồi phiên, rotate khóa, ứng phó sự cố và quản lý truy cập.

## Bằng chứng cần có trước triển khai

- Build Java và chạy JUnit, Spring integration với MariaDB/Redis, kiểm tra rollback, Redis lỗi,
  failover, concurrency, phân quyền, và test các endpoint nội bộ không lộ qua proxy.
- Flutter analyze/test và release builds Android/iOS; test trên thiết bị thật, TLS, secure storage,
  migration, mất mạng, gián đoạn ghi, logout, mất response và 2FA.
- Vue test/build không bật demo; quét dependency, kiểm tra token/session, CSP và multi-tab.
- Kiểm thử staging cấu hình thật nhưng dữ liệu/secret tách biệt: proxy spoof, CORS, bảo mật HTTP,
  giới hạn request, Redis/DB/worker không mở Internet, logs và backup/restore.
- Review độc lập/pentest theo phạm vi được phê duyệt; sửa finding và nghiệm thu trước phát hành.
