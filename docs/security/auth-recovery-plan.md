# Kế hoạch khôi phục phiên và 2FA — chưa triển khai

**Trạng thái:** bản thiết kế/chặn rollout, không phải mô tả tính năng đã hoạt động.
Các thay đổi cần được kiểm thử tích hợp trước khi phát hành. Không áp schema lên DB thật trong lúc phát triển.

## Quyết định nghiệp vụ

- Một tài khoản chỉ có một slot mobile. Khi xung đột, chỉ sau khi xác thực đầy đủ
  (mật khẩu và TOTP nếu đã bật), người dùng mới được xác nhận thay phiên cũ. Khi chưa
  bật 2FA, mật khẩu đúng và xác nhận là đủ. Thu hồi phải so sánh đúng sessionId cũ để
  không thu hồi nhầm phiên mới trong race.
- Mất thiết bị: dùng mã khôi phục ngẫu nhiên một lần. Server chỉ lưu hash, có giới hạn
  thử, ghi audit và thao tác tiêu thụ nguyên tử. Không triển khai đường bypass bằng mật khẩu.
  Cần xác định UX cấp, hiển thị một lần, lưu an toàn, cấp lại, thu hồi và cách xử lý
  tài khoản đã bật 2FA trước khi có mã; không tuyên bố tính năng hoạt động trước khi đủ các bước.

## 2FA pending và receipt

- Mobile lưu bản chờ trong secure storage trước confirm, gắn admin, credentialId,
  requestId và tham số TOTP. Ghi không thành công thì không confirm; không thay/xóa
  credential đã xác minh. Không tự đánh dấu khóa chờ là hợp lệ.
- Server xác minh đúng credential, expiry và trạng thái dưới khóa nhất quán; ghi receipt
  cùng transaction với bật 2FA. Retry cùng thao tác phải kiểm identity và payload, không
  đếm thêm lần thử hoặc bật lại. Không lưu OTP dạng rõ hay hash không khóa của OTP 6 số.
- Khi mất response, app giữ bản chờ và đối soát đúng receipt/credential. Không suy ra
  khóa đúng chỉ vì tài khoản có 2FA. Thiết kế xử lý phiên hết hạn và mất hẳn thiết bị.
- Phối hợp chính sách chống dùng lại TOTP giữa confirm, login và disable. Redis và DB
  không có transaction chung; cần mô hình trạng thái và test fault injection rõ ràng.
- Migration chỉ bổ sung trong `ntt_university` hiện tại; cần kế hoạch nâng cấp/rollback
  và tương thích client cũ. Không tạo database riêng.

## Refresh

- Bind phiên vào bằng chứng sở hữu client; cân nhắc WebCrypto/BFF cho web, Keystore /
  Keychain cho mobile. Chỉ request ID không đủ chống replay khi token và ID đều bị lộ.
- Lưu request ID trước request; chuyển trạng thái token cũ/mới và kết quả retry có hạn
  bằng một thiết kế server-side nguyên tử; cùng ID và bằng chứng hợp lệ chỉ nhận cùng
  kết quả, không phát cặp mới. Logout, khóa tài khoản và khác client/session phải chặn retry.
- Bảo vệ kết quả có token trong Redis, TTL ngắn, không ghi log. Không giả định Redis Lua
  rollback các lệnh đã thực hiện trước runtime error; kiểm thử lỗi từng bước, failover và TTL.
- Client lưu trạng thái/version nhất quán, không ghi dở từng token; bảo vệ logout và
  migration, hợp nhất đường refresh khi khởi động và khi chạy. Web xử lý nhiều tab cả
  khi không có Web Locks. Không hứa chống XSS hay máy đã bị chiếm quyền hoàn toàn.

## Cổng kiểm thử trước rollout

Chưa chạy được trong môi trường hiện tại: JUnit/Spring với MariaDB và Redis thật, cùng
Flutter/device thật. Cần test đồng thời, lỗi lưu trữ, lỗi từng bước, mất response, restart,
replay, logout, thu hồi/khóa tài khoản, đổi thiết bị, cancel/confirm/disable song song,
clock skew TOTP, nâng cấp client cũ và khả năng khôi phục sau mất máy. Không deploy khi
chưa hoàn thành kiểm thử và review bảo mật.
