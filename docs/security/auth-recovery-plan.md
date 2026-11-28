# Kế hoạch khôi phục phiên và 2FA — triển khai một phần, chưa được phát hành

**Trạng thái:** đã có thay đổi thử nghiệm cho staged key và một số race 2FA; toàn bộ nhóm
chưa hoàn tất, vẫn chặn rollout. Chưa chạy compile/JUnit, Flutter hoặc kiểm thử tích hợp.
Không áp schema lên DB thật trong lúc phát triển.

## Tiến độ hiện tại

- Mobile ghi khóa chờ riêng theo admin và credentialId rồi đọc kiểm tra trước confirm;
  staging từ chối nếu đã có khóa khác hoặc legacy; promotion/disable đọc lại storage và
  không ghi đè/xóa khóa khác trong cùng isolate. Lượt 401 retry kiểm admin trước và sau
  refresh; không fallback sang token cũ nếu refresh trả null. Các thay đổi này chưa chạy
  Flutter test, không tạo transaction liên isolate, không bind cùng session cryptographic
  và không giải quyết logout xảy ra sau lần kiểm tra; cần test thiết bị và race thực tế.
  Dữ liệu khóa khác lọt vào key riêng của admin làm storage fail-closed thay vì bị xóa.
- Mobile confirm mới gửi ID và kiểm nội dung response. Khi mở lại màn hình, có thể đối chiếu
  credential hiện tại bằng phiên đăng nhập hợp lệ và mã TOTP sinh từ khóa chờ, có giới hạn
  thử, trước khi khôi phục khóa khi chưa có khóa khác. Đây là đối chiếu trạng thái + bằng
  chứng TOTP, **không phải receipt idempotent ghi cùng transaction**, không khôi phục được
  nếu không còn phiên đăng nhập hoặc không mở được màn Authenticator. Khóa chờ không bị
  xóa khi kết quả mơ hồ; nhập sai một khóa đã lưu chờ có thể phải bắt đầu enrollment mới.
- Java khóa hàng admin trong transaction activate/cancel/confirm/disable, kiểm lại credential
  và thời hạn khi confirm, xóa đúng generation khi abort/disable; lượt thử confirm theo ID.
  Hủy pending DB giờ yêu cầu credentialId, so sánh khi xóa; Vue gửi ID đã hiển thị và tải lại
  trạng thái sau hủy. Client cũ không gửi ID nhận HTTP 428 và phải nâng cấp. Dọn Redis sau
  activate và hủy phiên sinh khóa mới dùng generation + Lua, chi tiết bên dưới; login chưa
  đồng bộ với thay đổi 2FA, SSE còn có thể gửi sự kiện cũ. Không coi #3 đã xong.
- Đã thêm credentialId vào khóa mobile mới và request disable; khóa legacy thiếu ID vẫn
  chưa có cách đối chiếu chắc chắn. Confirm không gửi credentialId nay bị từ chối HTTP 428:
  client cũ phải nâng cấp, không thể hứa tương thích hoàn toàn. ID không chứng minh client đã
  lưu khóa; cần chiến lược version/rollout, receipt idempotent và kiểm thử nâng cấp.
- Mã khôi phục: đã thêm migration đề xuất (chưa áp) và primitive server nội bộ tạo bộ mã
  ngẫu nhiên chỉ lưu hash, kích hoạt sau ACK, giới hạn thử và tiêu thụ một lần bằng DB.
  Chưa có endpoint, chưa nối vào thiết lập/login, chưa có UI cấp/hiển thị/ACK/khôi phục,
  audit đầy đủ, hay test DB/Redis; vì vậy người dùng **chưa sử dụng được mã khôi phục**.
  Không áp migration lên CSDL thật hoặc bật tính năng khi chưa hoàn thành các bước đó.
- Các API 2FA mobile và web nay kiểm tra loại client trong ACCESS/session trước khi gọi
  service, đồng thời vẫn kiểm tra token đang hoạt động và trạng thái tài khoản. Client sai
  loại nhận 401. Đây là phân tách client, **không phải sender binding**; mã Java/JUnit chưa chạy.
- Đã thêm Lua thử nghiệm so sánh session cũ và consume challenge khi đổi slot mobile,
  chưa nối API hay tạo challenge. 50 race trên Redis standalone đạt; takeover hoàn chỉnh
  và refresh nguyên tử/idempotent có sender binding **chưa triển khai**. Không dùng các
  thay đổi một phần như bằng chứng có thể deploy công khai.


## Redis sinh/hủy khóa: lát cắt thử nghiệm

- Khóa v2 riêng cho mỗi admin, không ghi đè phiên READY hoặc CLAIMED. Start trả UUID;
  Vue gửi UUID đó khi activate/cancel. Activate atomically claim đúng UUID và period,
  chỉ chủ claim được dọn sau DB commit; cancel cũ không xóa phiên mới hoặc claim đang chạy.
  Under admin DB lock không thay credential pending còn hạn. Client cũ thiếu UUID nhận 428;
  phiên Redis đời cũ không được kích hoạt bằng API mới, tự hết TTL. Cần nâng cấp đồng bộ
  mọi client web, không rolling upgrade trộn server cũ/mới trước khi có thiết kế tương thích.
- Claim không TTL khi kết quả mơ hồ; đã bổ sung phương án đối soát bằng fence DB và nút
  Vue nhưng chưa chạy Java hoặc test tích hợp. Không tự xóa claim bằng tay; đối soát an toàn
  chỉ là thiết kế và mã thử nghiệm cho đến khi qua cổng kiểm thử bên dưới.
- Lua mới đã chạy Redis standalone 6.2.14 với 100 race start/claim và cancel/claim, cộng
  kiểm tra stale, owner và period. Đây không phải test Spring/JPA hay cross-store fault
  injection. Redis mất dữ liệu/eviction/failover, split-brain, DB commit mơ hồ, cleanup lỗi,
  khôi phục claim và rolling deployment chưa được chứng minh an toàn; vẫn chặn rollout.
  Đặc biệt Redis và DB không có transaction chung, không tuyên bố exactly-once.

### Đối soát claim bằng fence DB (thử nghiệm, chưa kiểm chứng)

- Migration đề xuất `be-web-admin/sql/2026-11-23-totp-enrollment-fences.sql` chưa được áp.
  Thiếu bảng này, start nay probe DB trước khi sinh/hiển thị khóa (trả 503 với lỗi DB),
  activate vẫn probe lại trước khi claim và không thể hoạt động. Probe không đảm bảo schema
  đúng hoàn toàn hoặc DB vẫn sẵn sàng sau đó; cần migration được duyệt, môi trường DB riêng
  để test, cùng kế hoạch tương thích/rollback trước rollout. Thay đổi chưa compile/JUnit.
- Activate khóa admin, kiểm tra fence và ghi marker COMMITTED cùng transaction với credential.
  Endpoint web `/2fa/enrollment/reconcile` có rate limit, yêu cầu access session WEB và đúng
  generation. Nó khóa cùng admin, giữ marker COMMITTED hoặc ghi CANCELLED nếu chưa commit,
  rồi mới dọn Redis bằng so khớp generation và claim. Worker bị trì hoãn phải kiểm tra fence
  dưới khóa trước khi ghi. Có audit best-effort, không chứa khóa/claim/OTP; audit chưa được
  kiểm chứng trong DB thật. Không dùng cùng luồng trong outer transaction.
- Vue lưu **chỉ UUID generation**, tách từng admin/generation trong localStorage trước khi
  activate; không lưu khóa TOTP. Sau lỗi/mất response có nút đối soát và xác nhận rõ rằng
  thao tác có thể hủy activation chưa commit. Nếu đã commit mà khóa hiển thị đã mất, phải
  kiểm tra trạng thái và có thể cần hủy pending để cài lại. Nếu browser storage bị từ chối,
  Vue không gửi activation. Các client khác không tự có marker hoặc UI này.
- Nếu DB commit mơ hồ, không dọn claim và giữ marker để thử lại; nếu Redis không dọn được,
  DB fence vẫn phải giữ. Không tự xóa fence, kể cả khi credential đã bị hủy/tắt 2FA: replay
  cũ, Redis snapshot hoặc worker trì hoãn vẫn có thể tồn tại. Chưa có chính sách retention,
  backup/restore, chống bảng tăng trưởng hoặc khôi phục sau mất/khôi phục DB cũ.
- **Chưa chạy compile/JUnit hoặc MariaDB+Redis integration/fault injection.** Cần xác nhận
  JPA/JDBC thực sự cùng transaction, lock và isolation, DB commit timeout, Redis failover,
  eviction/restore, lỗi từng bước, logout/khóa tài khoản, JPA persistence context và phiên
  client cũ. Cancel READY hiện vẫn chỉ xóa Redis, chưa có fence DB riêng; rolling upgrade,
  server cũ, split-brain và khôi phục DB snapshot có thể phá giả định. Chưa tuyên bố an toàn
  với các trường hợp đó, chưa bật rollout. Vue có unit check marker và build, không thay thế
  test end-to-end.

### Thử nghiệm lõi takeover (chưa tích hợp)

`mobile-takeover-swap.lua` chỉ là primitive nội bộ, không có endpoint hay Java caller.
Nó yêu cầu challenge còn TTL, khớp proof hash và hai session ID, rồi so sánh slot cũ trước
khi hủy session cũ, consume challenge và công bố slot mới sau cùng. Lua không thể tự chứng
minh đã xác thực mật khẩu/TOTP, đã được người dùng xác nhận, hay client sở hữu khóa; phải
thực hiện các bước này trong luồng ứng dụng trước khi gọi. Không xem proof hash như sender
binding. Chưa xử lý cấp/nhận token mới nguyên tử, retry idempotent khi mất response,
logout/khóa tài khoản, thay đổi 2FA, Redis failover/eviction và audit; do đó tuyệt đối
không gọi primitive từ endpoint hoặc bật takeover cho người dùng ở trạng thái hiện tại.
Không tạo logout tombstone cho phiên bị thay: tombstone hiện chỉ đại diện logout thật.

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

### Prototype server confirm-v2 — tắt mặc định, chưa tích hợp

- Có migration đề xuất `be-web-admin/sql/2026-11-23-totp-confirm-receipts.sql` và
  endpoint `/mobile/admin/2fa/enrollment/confirm-v2` sau cờ
  `app.totp.idempotent-confirm-enabled=true`. **Không bật trong production**: migration
  chưa áp, Java/JUnit chưa chạy, chưa kiểm chứng MariaDB/Redis và mobile chưa gọi API này.
  Fence migration vẫn bắt buộc cho activate web; receipt migration không sửa việc thiếu fence.
- Prototype ghi receipt cùng transaction dự kiến với bật credential và cờ admin; khóa hàng
  admin, kiểm session MOBILE, credential hiện hành và hạn receipt. Retry cùng admin/session,
  credential, requestId và OTP nhận cùng body thành công mà không tiêu thụ thêm lượt OTP.
  RequestId là 32 byte ngẫu nhiên CSPRNG mã hóa lowercase hex; server chỉ kiểm định dạng,
  không thể kiểm định entropy. Payload MAC dùng khóa TOTP giải mã và domain riêng, không
  lưu OTP hay hash OTP không khóa; không ghi requestId dạng rõ. Đây không phải sender binding.
- Mobile **chưa lưu requestId và OTP của lần gửi trong secure storage**, chưa gửi confirm-v2
  hay tự retry. Phải thiết kế xóa dữ liệu và giới hạn thời gian lưu OTP, bảo đảm ghi bền
  trước gửi và test mất response/restart; dữ liệu pending cũ vẫn theo luồng cũ. Các client
  khác vẫn gọi endpoint confirm cũ, có thể bật 2FA mà không tạo receipt; không trộn luồng
  cũ/mới trong rollout cho đến khi có chính sách tương thích. Receipt chỉ hữu ích trong cùng
  session còn sống và TTL 24 giờ; logout/mất phiên/thiết bị cần phương án khôi phục riêng.
- Kiểm tra session ở Redis không nguyên tử với DB commit: logout/takeover có thể xảy ra sau
  lần kiểm tra. Audit/SSE chạy sau commit và không có outbox; event có thể mất hoặc đến sai
  thứ tự. Khi DB commit không rõ kết quả phải thử lại đúng payload, không suy luận rollback.
  Cascade xóa credential sẽ xóa receipt; cần chính sách retention, backup/restore, đồng hồ và
  tương thích schema. Code nhận đúng 6 số, chưa hỗ trợ credential có digits khác 6.
- Test mock mới chỉ mô tả dự kiến; cần compile và chạy, thêm kiểm thử transaction JPA/JDBC
  cùng connection, rollback khi INSERT/commit lỗi, hai retry đồng thời, confirm cũ cạnh tranh
  confirm-v2, disable/re-enroll, cross-admin/session, logout/lock và Redis lỗi/failover. Không
  tuyên bố receipt chính xác hoặc 2FA hoàn tất nếu chưa chạy các kiểm thử thực này.

### Yêu cầu còn lại

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
