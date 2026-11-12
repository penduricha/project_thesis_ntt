import 'dart:math' as math;
import 'dart:typed_data';

// Cần thêm vào pubspec.yaml:  crypto: ^3.0.3
// Yêu cầu Dart SDK >= 3.0 (file dùng switch expression).
import 'package:crypto/crypto.dart';

// ============================================================================
//  TOTP - Time-based One-Time Password (RFC 6238)
//  Cấu hình mặc định: HMAC-SHA256 | 6 chữ số | chu kỳ 30 giây
//  (KHỚP với server Spring Boot: TotpVerifier + bảng ntt_totp_credentials)
// ============================================================================
//
//  0. GHÉP VỚI API SPRING BOOT
//  ----------------------------------------------------------------------------
//  Server KHÔNG gửi khóa bí mật cho app. Người dùng đọc khóa Base32 (32 ký tự)
//  trên web rồi nhập vào app; app lưu khóa ở flutter_secure_storage. Các thông
//  số còn lại app lấy từ API:
//
//    GET  /api/admin/mobile/2fa/enrollment/pending      (Bearer accessToken)
//      200: { data: { algorithm: "HmacSHA256", digits: 6,
//                     periodSeconds: 30, expiresInSeconds: 598 } }
//      404: web chưa bấm "Kích hoạt"      410: quá thời hạn xác nhận
//
//    POST /api/admin/mobile/2fa/enrollment/confirm      Body: { "code": "123456" }
//      200: đã bật 2FA   422: mã sai   429: nhập sai quá số lần (phải cài lại)
//      400: mã không đủ 6 chữ số
//
//  Cách sinh mã để server chấp nhận (dùng ĐÚNG thông số của API pending, không
//  gán cứng 30 giây hay 6 số, vì admin có thể chọn chu kỳ 15/30/45/60 giây):
//
//      final code = TotpGenerator.generate(
//        secret,
//        period: pending.periodSeconds,
//        digits: pending.digits,
//        algorithm: TotpGenerator.algorithmFromServer(pending.algorithm),
//      );
//
//  Lưu ý: mỗi lần gửi mã SAI (kể cả do nhập sai khóa) server tính 1 lượt trong
//  số lần tối đa. Nên kiểm tra khóa bằng [isValidSecret] trước khi gọi confirm.
//
//  1. KHÁI NIỆM
//  ----------------------------------------------------------------------------
//  TOTP là mã xác thực dùng một lần, phụ thuộc vào THỜI GIAN. Thuật toán được
//  chuẩn hóa trong RFC 6238 (IETF, 2011) và là phần mở rộng của HOTP
//  (HMAC-based One-Time Password, RFC 4226):
//    - HOTP: mã sinh ra từ một BỘ ĐẾM (counter) tăng dần sau mỗi lần dùng.
//    - TOTP: thay bộ đếm đó bằng SỐ BƯỚC THỜI GIAN tính từ đồng hồ hệ thống.
//  Nhờ vậy mã tự đổi theo thời gian mà hai bên không cần trao đổi thêm gì.
//
//  2. NGUYÊN LÝ HOẠT ĐỘNG
//  ----------------------------------------------------------------------------
//  - Khi kích hoạt (nhập khóa Base32 hiển thị trên web), server và ứng dụng
//    cùng giữ một KHÓA BÍ MẬT K dùng chung (shared secret). Đây là lần duy nhất
//    khóa được truyền đi (từ web tới người dùng, rồi từ người dùng vào app).
//  - Sau đó mỗi bên TỰ TÍNH mã một cách độc lập từ (K, thời gian hiện tại).
//    Cùng K + cùng thời điểm + cùng thuật toán thì cho cùng kết quả, nên server
//    chỉ cần so sánh mã người dùng nhập với mã mình tự tính. Ứng dụng sinh mã
//    hoàn toàn OFFLINE, không cần mạng, không gửi gì lên server khi sinh mã.
//  - Vì mã gắn với bước thời gian, mã cũ hết hiệu lực sau mỗi chu kỳ; kẻ nghe
//    lén có được mã cũ cũng không dùng lại được về sau.
//  - Hai bên PHẢI thống nhất cả 4 thông số: thuật toán, số chữ số, chu kỳ và
//    khóa K. Lệch bất kỳ thông số nào thì mã ra khác hẳn nhau (ví dụ: app dùng
//    SHA-1 còn server dùng SHA-256 thì mọi mã đều bị từ chối).
//
//  3. CÔNG THỨC
//  ----------------------------------------------------------------------------
//      T    = floor( (Thời_gian_Unix_hiện_tại - T0) / X )
//      TOTP = Truncate( HMAC-SHA256(K, T) )  mod  10^d
//
//    K  : khóa bí mật dùng chung (giải mã từ Base32 ra mảng byte)
//    T0 : mốc bắt đầu đếm, = 0 (Unix epoch: 00:00:00 UTC ngày 01/01/1970)
//    X  : độ dài một bước thời gian (mặc định 30 giây; lấy từ API pending)
//    T  : số bước thời gian đã trôi qua kể từ T0 (đóng vai trò "counter")
//    d  : số chữ số của mã (mặc định 6; lấy từ API pending)
//
//    Ví dụ: Thời gian Unix = 1.700.000.000 giây, X = 30
//           -> T = floor(1.700.000.000 / 30) = 56.666.666
//           Mọi thời điểm trong cùng cửa sổ 30 giây đều có chung T, do đó
//           chung một mã.
//
//  4. CÁC BƯỚC TÍNH (tương ứng với code bên dưới)
//  ----------------------------------------------------------------------------
//  Bước 1. Giải mã khóa bí mật từ Base32 (RFC 4648) ra mảng byte K.
//          Bảng chữ cái Base32: A-Z và 2-7, mỗi ký tự mang 5 bit.
//          Khóa HMAC là các BYTE đã giải mã, KHÔNG phải chuỗi ký tự Base32.
//  Bước 2. Tính T = floor(giây Unix / period). Giây Unix tính theo UTC nên KHÔNG
//          phụ thuộc múi giờ của thiết bị (chỉ phụ thuộc đồng hồ chính xác).
//  Bước 3. Đổi T thành chuỗi 8 byte, thứ tự big-endian (byte cao đứng trước).
//  Bước 4. Tính HMAC-SHA256(K, 8 byte ở bước 3) -> kết quả 256 bit = 32 byte.
//          (SHA-1 cho 20 byte, SHA-512 cho 64 byte.)
//          HMAC được định nghĩa trong RFC 2104; SHA-2 trong FIPS 180-4.
//  Bước 5. Dynamic Truncation (RFC 4226, mục 5.3) - rút dãy byte về 1 số nguyên:
//            a) offset = 4 bit thấp của byte cuối cùng (giá trị 0..15)
//            b) lấy 4 byte liên tiếp bắt đầu từ vị trí offset
//            c) xóa bit cao nhất của byte đầu (phép & 0x7F) để ra số nguyên
//               31 bit không âm, tránh khác biệt số có dấu/không dấu giữa các
//               nền tảng
//          Các bước này giống hệt với TotpVerifier.generate() phía Java.
//  Bước 6. Lấy số đó chia dư cho 10^d, rồi thêm số 0 ở đầu cho đủ d chữ số
//          (vd: 4821 -> "004821"). Quên đệm số 0 là lỗi rất hay gặp: khoảng
//          1/10 mã sẽ bị sai độ dài và bị server từ chối.
//
//  5. VÌ SAO CHU KỲ NGẮN, VÀ SERVER CHẤP NHẬN LỆCH BAO NHIÊU?
//  ----------------------------------------------------------------------------
//  Đủ ngắn để mã bị lộ nhanh mất giá trị, đủ dài để người dùng kịp đọc và nhập.
//  Server chấp nhận thêm cửa sổ +-1 bước để bù lệch đồng hồ giữa hai bên
//  (RFC 6238, mục 5.2). Nghĩa là với chu kỳ 15 giây, đồng hồ điện thoại chỉ
//  được lệch tối đa khoảng 15 giây so với server; với 30 giây là khoảng 30 giây.
//  Hãy bật giờ tự động trên điện thoại. Server nên từ chối mã đã dùng trong
//  cùng một bước để chống dùng lại (replay), phần này nằm ở bước đăng nhập.
//
//  6. VÌ SAO DÙNG SHA-256 (KHÁC VỚI CẤU HÌNH PHỔ BIẾN SHA-1)?
//  ----------------------------------------------------------------------------
//  - Nhiều ứng dụng Authenticator phổ biến mặc định hoặc chỉ hỗ trợ
//    SHA-1 / 6 số / 30 giây để tương thích với mọi server. Ở đây cả server lẫn
//    app đều do mình phát triển nên tự do chọn thuật toán mạnh hơn.
//  - RFC 6238 cho phép HMAC-SHA1, HMAC-SHA256 và HMAC-SHA512. Đề tài chọn
//    HMAC-SHA256 để có biên an toàn lớn hơn và nhất quán với toàn hệ thống
//    (Spring Boot lưu algorithm = "HmacSHA256" cho từng credential).
//  - Đánh đổi: khóa của app này KHÔNG dùng được với Google Authenticator hay
//    Microsoft Authenticator nếu các app đó chỉ tính bằng SHA-1.
//  - HMAC-SHA1 vẫn được giữ trong [TotpAlgorithm] để kiểm thử với vector chuẩn
//    của RFC và để tương thích nếu cần đổi cấu hình về sau.
//
//  7. LƯU Ý BẢO MẬT
//  ----------------------------------------------------------------------------
//  - Toàn bộ độ an toàn nằm ở khóa bí mật K: lộ K là kẻ khác tự sinh được mã.
//    Cần lưu K ở nơi an toàn (flutter_secure_storage: Android Keystore / iOS
//    Keychain), tuyệt đối không ghi log khóa hay mã.
//  - RFC 4226 yêu cầu K dài ít nhất 128 bit và khuyến nghị 160 bit
//    (tương đương 26 và 32 ký tự Base32). Server hiện sinh khóa 20 byte
//    (160 bit = 32 ký tự Base32) nên đúng khuyến nghị này. RFC 6238 khuyến nghị
//    khóa dài bằng đầu ra hàm băm (SHA-256: 32 byte = 52 ký tự); nếu sau này
//    nâng lên mức đó thì server phải mở rộng cột secret_cipher, và đổi
//    [serverSecretLength] ở dưới.
//  - TOTP không chống được lừa đảo theo thời gian thực (phishing): nếu người
//    dùng nhập mã vào trang giả, kẻ tấn công có thể chuyển tiếp mã đó ngay
//    trong khoảng thời gian của một chu kỳ.
//
//  8. KIỂM THỬ - VECTOR CHUẨN TRONG RFC 6238 (PHỤ LỤC B)
//  ----------------------------------------------------------------------------
//  8a. HMAC-SHA256 (thuật toán đang dùng). Khóa ASCII (32 byte):
//      "12345678901234567890123456789012"
//      Dạng Base32 (52 ký tự):
//      GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA
//
//      Thời gian Unix (giây) |    T      | TOTP 8 số | TOTP 6 số
//      ----------------------+-----------+-----------+----------
//                         59 |         1 |  46119246 |  119246
//                 1111111109 |  37037036 |  68084774 |  084774
//                 1111111111 |  37037037 |  67062674 |  062674
//                 1234567890 |  41152263 |  91819424 |  819424
//                 2000000000 |  66666666 |  90698825 |  698825
//
//      Với chu kỳ khác, tại t = 59: chu kỳ 15 giây -> 975832,
//      chu kỳ 60 giây -> 920136 (6 chữ số, SHA-256, cùng khóa trên).
//
//  8b. HMAC-SHA1. Khóa ASCII (20 byte): "12345678901234567890"
//      Dạng Base32: GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ
//
//      Thời gian Unix (giây) |    T      | TOTP (8 số)
//      ----------------------+-----------+------------
//                         59 |         1 |  94287082
//                 1111111109 |  37037036 |  07081804
//                 1111111111 |  37037037 |  14050471
//                 1234567890 |  41152263 |  89005924
//                 2000000000 |  66666666 |  69279037
//                20000000000 | 666666666 |  65353130
//
//  Ví dụ gọi:
//      TotpGenerator.generate(
//        'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA',
//        algorithm: TotpAlgorithm.sha256,
//        at: DateTime.fromMillisecondsSinceEpoch(59 * 1000, isUtc: true),
//      );  // -> "119246"
//
//  Nên chạy bộ test (totp_generator_test.dart) mỗi khi sửa file này.
//
//  TÀI LIỆU THAM KHẢO
//  ----------------------------------------------------------------------------
//  [1] RFC 6238 - TOTP: Time-Based One-Time Password Algorithm (2011)
//  [2] RFC 4226 - HOTP: An HMAC-Based One-Time Password Algorithm (2005)
//  [3] RFC 2104 - HMAC: Keyed-Hashing for Message Authentication (1997)
//  [4] RFC 4648 - The Base16, Base32, and Base64 Data Encodings (2006)
//  [5] FIPS PUB 180-4 - Secure Hash Standard (SHS)
// ============================================================================

/// Hàm băm dùng trong HMAC. Tên khớp với giá trị `algorithm` mà server lưu
/// ("HmacSHA1" / "HmacSHA256" / "HmacSHA512", xem [TotpGenerator.algorithmFromServer]).
enum TotpAlgorithm { sha1, sha256, sha512 }

/// Bộ sinh mã TOTP (RFC 6238). Chỉ chứa logic thuần Dart, không phụ thuộc
/// Flutter, nên dễ viết unit test và dễ thay đổi tham số/thuật toán về sau.
class TotpGenerator {
  TotpGenerator._();

  /// Chu kỳ mặc định: 30 giây (tham số X trong công thức).
  /// Khi ghép với server, hãy dùng `periodSeconds` từ API pending thay vì giá trị này.
  static const int defaultPeriod = 30;

  /// Số chữ số mặc định: 6 (tham số d trong công thức).
  /// Khi ghép với server, hãy dùng `digits` từ API pending.
  static const int defaultDigits = 6;

  /// Thuật toán mặc định: HMAC-SHA256, khớp với server (algorithm = "HmacSHA256").
  static const TotpAlgorithm defaultAlgorithm = TotpAlgorithm.sha256;

  /// Độ dài chuỗi Base32 mà server sinh ra: 20 byte (160 bit) -> 32 ký tự.
  /// Nếu server nâng lên khóa 32 byte thì đổi thành 52.
  static const int serverSecretLength = 32;

  static const String _base32Alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

  // 2^32: dùng để tách bộ đếm 64 bit thành 2 nửa 32 bit (an toàn trên cả Web)
  static const int _twoPow32 = 4294967296;

  /// Chuẩn hóa secret: viết hoa, bỏ khoảng trắng, dấu "-" và "="
  /// (vd: "abcd efgh-ijkl" -> "ABCDEFGHIJKL").
  /// Người dùng hay gõ khóa có dấu cách hoặc viết thường, nên luôn chuẩn hóa
  /// trước khi lưu và trước khi sinh mã.
  static String normalizeSecret(String input) {
    return input.toUpperCase().replaceAll(RegExp(r'[\s=-]'), '');
  }

  /// Kiểm tra khóa người dùng nhập: sau khi chuẩn hóa phải chỉ gồm A-Z, 2-7 và
  /// đúng độ dài [expectedLength]. Gọi hàm này TRƯỚC khi gửi mã lên server, vì
  /// mỗi lần gửi mã sai server tính 1 lượt thử (quá số lần sẽ phải cài lại).
  static bool isValidSecret(String input, {int expectedLength = serverSecretLength}) {
    final clean = normalizeSecret(input);
    return clean.length == expectedLength &&
        RegExp(r'^[A-Z2-7]+$').hasMatch(clean);
  }

  /// Đổi chuỗi `algorithm` mà server trả (API pending) sang [TotpAlgorithm].
  /// Ném [FormatException] nếu tên không được hỗ trợ.
  static TotpAlgorithm algorithmFromServer(String name) => switch (name) {
    'HmacSHA1' => TotpAlgorithm.sha1,
    'HmacSHA256' => TotpAlgorithm.sha256,
    'HmacSHA512' => TotpAlgorithm.sha512,
    _ => throw FormatException('Thuật toán không hỗ trợ: $name'),
  };

  /// Chiều ngược lại: dùng khi lưu thông số cùng khóa vào secure storage.
  static String algorithmToServer(TotpAlgorithm algorithm) => switch (algorithm) {
    TotpAlgorithm.sha1 => 'HmacSHA1',
    TotpAlgorithm.sha256 => 'HmacSHA256',
    TotpAlgorithm.sha512 => 'HmacSHA512',
  };

  /// Sinh mã TOTP tại thời điểm [at] (mặc định: bây giờ).
  ///
  /// - [period]: chu kỳ đổi mã (giây), lấy từ API pending (15/30/45/60).
  /// - [digits]: số chữ số, từ 6 đến 9.
  /// - [algorithm]: mặc định SHA-256 để khớp server.
  ///
  /// Ném [FormatException] nếu [base32Secret] chứa ký tự ngoài bảng Base32.
  /// Ném [ArgumentError] nếu [period] hoặc [digits] không hợp lệ (dùng exception
  /// thay vì assert vì assert bị bỏ qua ở bản release, mà các giá trị này đến
  /// từ JSON của server).
  static String generate(
      String base32Secret, {
        int period = defaultPeriod,
        int digits = defaultDigits,
        TotpAlgorithm algorithm = defaultAlgorithm,
        DateTime? at,
      }) {
    if (period <= 0) {
      throw ArgumentError.value(period, 'period', 'phải > 0');
    }
    if (digits < 6 || digits > 9) {
      throw ArgumentError.value(digits, 'digits', 'phải trong khoảng 6..9');
    }

    // Bước 1: giải mã khóa Base32 -> mảng byte K
    final key = _base32Decode(base32Secret);

    // Bước 2: T = floor(giây Unix / period). T0 = 0 nên không cần trừ gì thêm.
    final unixSeconds = (at ?? DateTime.now()).millisecondsSinceEpoch ~/ 1000;
    final counter = unixSeconds ~/ period;

    // Bước 3 -> 6: phần còn lại giống hệt HOTP (RFC 4226)
    return _hotp(key, counter, digits, algorithm);
  }

  /// Số giây còn lại của mã hiện tại (1..period), dùng cho thanh đếm ngược trên UI.
  /// Mã đổi đúng lúc giây Unix chia hết cho [period].
  static int secondsRemaining({int period = defaultPeriod, DateTime? at}) {
    if (period <= 0) {
      throw ArgumentError.value(period, 'period', 'phải > 0');
    }
    final unixSeconds = (at ?? DateTime.now()).millisecondsSinceEpoch ~/ 1000;
    return period - (unixSeconds % period);
  }

  /// HOTP(K, C): lõi dùng chung của HOTP và TOTP. Với TOTP, C chính là T.
  static String _hotp(
      Uint8List key,
      int counter,
      int digits,
      TotpAlgorithm algorithm,
      ) {
    // Bước 3: counter -> 8 byte big-endian.
    // Ghi làm 2 nửa 32 bit (cao rồi thấp) thay vì setUint64 vì setUint64
    // không được hỗ trợ khi chạy trên Web.
    final message = Uint8List(8);
    final view = ByteData.view(message.buffer);
    view.setUint32(0, counter ~/ _twoPow32); // 32 bit cao
    view.setUint32(4, counter % _twoPow32); // 32 bit thấp

    // Bước 4: HMAC(K, message) -> 20 / 32 / 64 byte tùy thuật toán.
    // Thuật toán PHẢI trùng với cột algorithm của server, nếu không mọi mã đều sai.
    final Hash hashFunction = switch (algorithm) {
      TotpAlgorithm.sha1 => sha1,
      TotpAlgorithm.sha256 => sha256,
      TotpAlgorithm.sha512 => sha512,
    };
    final hash = Hmac(hashFunction, key).convert(message).bytes;

    // Bước 5: Dynamic Truncation. Offset lấy từ byte CUỐI của hash
    // (hash.last), đúng cho mọi độ dài hash và khớp với bên Java.
    final offset = hash.last & 0x0f; // 4 bit thấp của byte cuối: 0..15
    final binary = ((hash[offset] & 0x7f) << 24) | // xóa bit cao nhất
    ((hash[offset + 1] & 0xff) << 16) |
    ((hash[offset + 2] & 0xff) << 8) |
    (hash[offset + 3] & 0xff);

    // Bước 6: lấy d chữ số cuối, đệm số 0 phía trước cho đủ độ dài
    final otp = binary % math.pow(10, digits).toInt();
    return otp.toString().padLeft(digits, '0');
  }

  /// Giải mã Base32 (RFC 4648): mỗi ký tự = 5 bit, gom đủ 8 bit thì xuất 1 byte.
  /// Các bit còn dư ở cuối (không đủ 8) bị bỏ, giống TotpVerifier.base32Decode phía Java.
  static Uint8List _base32Decode(String input) {
    final clean = normalizeSecret(input);

    final bytes = <int>[];
    var buffer = 0;
    var bits = 0;
    for (final ch in clean.split('')) {
      final value = _base32Alphabet.indexOf(ch);
      if (value < 0) {
        // Không đưa cả khóa vào thông báo lỗi để tránh lộ khóa trong log;
        // chỉ nêu ký tự sai.
        throw FormatException('Secret key không hợp lệ: "$ch"');
      }
      buffer = (buffer << 5) | value;
      bits += 5;
      if (bits >= 8) {
        bits -= 8;
        bytes.add((buffer >> bits) & 0xFF);
        buffer &= (1 << bits) - 1; // giữ buffer nhỏ, tránh tràn số
      }
    }
    return Uint8List.fromList(bytes);
  }
}