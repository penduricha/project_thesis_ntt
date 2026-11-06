import 'dart:math' as math;
import 'dart:typed_data';

// Cần thêm vào pubspec.yaml:  crypto: ^3.0.3
import 'package:crypto/crypto.dart';

// ============================================================================
//  TOTP - Time-based One-Time Password (RFC 6238)
//  Cấu hình dùng trong ứng dụng: HMAC-SHA1 | 6 chữ số | chu kỳ 30 giây
// ============================================================================
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
//  - Khi kích hoạt (quét mã QR hoặc nhập khóa Base32), server và ứng dụng cùng
//    giữ một KHÓA BÍ MẬT K dùng chung (shared secret). Đây là lần duy nhất khóa
//    được truyền đi.
//  - Sau đó mỗi bên TỰ TÍNH mã một cách độc lập từ (K, thời gian hiện tại).
//    Cùng K + cùng thời điểm thì cho cùng kết quả, nên server chỉ cần so sánh
//    mã người dùng nhập với mã mình tự tính. Ứng dụng sinh mã hoàn toàn OFFLINE,
//    không cần mạng, không gửi gì lên server khi sinh mã.
//  - Vì mã gắn với bước thời gian, mã cũ hết hiệu lực sau mỗi chu kỳ 30 giây;
//    kẻ nghe lén có được mã cũng không dùng lại được về sau.
//
//  3. CÔNG THỨC
//  ----------------------------------------------------------------------------
//      T    = floor( (Thời_gian_Unix_hiện_tại - T0) / X )
//      TOTP = Truncate( HMAC-SHA1(K, T) )  mod  10^d
//
//    K  : khóa bí mật dùng chung (giải mã từ Base32 ra mảng byte)
//    T0 : mốc bắt đầu đếm, = 0 (Unix epoch: 00:00:00 UTC ngày 01/01/1970)
//    X  : độ dài một bước thời gian, = 30 giây
//    T  : số bước thời gian đã trôi qua kể từ T0 (đóng vai trò "counter")
//    d  : số chữ số của mã, = 6
//
//    Ví dụ: Thời gian Unix = 1.700.000.000 giây
//           -> T = floor(1.700.000.000 / 30) = 56.666.666
//           Mọi thời điểm trong cùng cửa sổ 30 giây đều có chung T, do đó
//           chung một mã.
//
//  4. CÁC BƯỚC TÍNH (tương ứng với code bên dưới)
//  ----------------------------------------------------------------------------
//  Bước 1. Giải mã khóa bí mật từ Base32 (RFC 4648) ra mảng byte K.
//          Bảng chữ cái Base32: A-Z và 2-7, mỗi ký tự mang 5 bit.
//  Bước 2. Tính T = floor(giây Unix / 30). Giây Unix tính theo UTC nên KHÔNG
//          phụ thuộc múi giờ của thiết bị (chỉ phụ thuộc đồng hồ chính xác).
//  Bước 3. Đổi T thành chuỗi 8 byte, thứ tự big-endian (byte cao đứng trước).
//  Bước 4. Tính HMAC-SHA1(K, 8 byte ở bước 3) -> kết quả 160 bit = 20 byte.
//          HMAC được định nghĩa trong RFC 2104; SHA-1 trong FIPS 180-4.
//  Bước 5. Dynamic Truncation (RFC 4226, mục 5.3) - rút 20 byte về 1 số nguyên:
//            a) offset = 4 bit thấp của byte cuối cùng (giá trị 0..15)
//            b) lấy 4 byte liên tiếp bắt đầu từ vị trí offset
//            c) xóa bit cao nhất của byte đầu (phép & 0x7F) để ra số nguyên
//               31 bit không âm, tránh khác biệt số có dấu/không dấu giữa các
//               nền tảng
//  Bước 6. Lấy số đó chia dư cho 10^6, rồi thêm số 0 ở đầu cho đủ 6 chữ số
//          (vd: 4821 -> "004821").
//
//  5. VÌ SAO CHỌN 30 GIÂY?
//  ----------------------------------------------------------------------------
//  Đây là điểm cân bằng giữa an toàn và tiện dùng: đủ ngắn để mã bị lộ nhanh
//  mất giá trị, đủ dài để người dùng kịp đọc và nhập. Phía server thường chấp
//  nhận thêm cửa sổ +-1 bước để bù lệch đồng hồ giữa hai bên (RFC 6238, mục 5.2),
//  và nên từ chối mã đã dùng rồi trong cùng bước để chống dùng lại (replay).
//
//  6. VÌ SAO VẪN DÙNG SHA-1?
//  ----------------------------------------------------------------------------
//  Các tấn công va chạm đã công bố nhắm vào SHA-1 (ví dụ SHAttered, 2017) không
//  áp dụng trực tiếp cho HMAC-SHA1, vì độ an toàn của HMAC không dựa vào tính
//  chống va chạm của hàm băm; đến nay chưa có tấn công thực tế nào vào HMAC-SHA1
//  trong ngữ cảnh TOTP. RFC 6238 cũng cho phép HMAC-SHA256 và HMAC-SHA512,
//  nhưng hầu hết ứng dụng (Google Authenticator, Microsoft Authenticator...)
//  mặc định SHA-1 / 6 số / 30 giây để tương thích với mọi server. Vì vậy các
//  tham số này được giữ làm mặc định ở đây.
//
//  7. LƯU Ý BẢO MẬT
//  ----------------------------------------------------------------------------
//  - Toàn bộ độ an toàn nằm ở khóa bí mật K: lộ K là kẻ khác tự sinh được mã.
//    Cần lưu K ở nơi an toàn (vd: flutter_secure_storage), không ghi log.
//  - RFC 4226 yêu cầu K dài ít nhất 128 bit và khuyến nghị 160 bit
//    (tương đương 26 và 32 ký tự Base32).
//  - TOTP không chống được lừa đảo theo thời gian thực (phishing): nếu người
//    dùng nhập mã vào trang giả, kẻ tấn công có thể chuyển tiếp mã đó ngay
//    trong 30 giây.
//
//  8. KIỂM THỬ - VECTOR CHUẨN TRONG RFC 6238 (PHỤ LỤC B)
//  ----------------------------------------------------------------------------
//  Khóa (chuỗi ASCII): "12345678901234567890"
//  Dạng Base32 của khóa: GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ
//  Thuật toán HMAC-SHA1, mã 8 chữ số:
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
//        'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ',
//        digits: 8,
//        at: DateTime.fromMillisecondsSinceEpoch(59 * 1000, isUtc: true),
//      );  // -> "94287082"
//  (Với 6 chữ số, mã là 6 chữ số cuối của mã 8 chữ số: "287082".)
//
//  TÀI LIỆU THAM KHẢO
//  ----------------------------------------------------------------------------
//  [1] RFC 6238 - TOTP: Time-Based One-Time Password Algorithm (2011)
//  [2] RFC 4226 - HOTP: An HMAC-Based One-Time Password Algorithm (2005)
//  [3] RFC 2104 - HMAC: Keyed-Hashing for Message Authentication (1997)
//  [4] RFC 4648 - The Base16, Base32, and Base64 Data Encodings (2006)
//  [5] FIPS PUB 180-4 - Secure Hash Standard (SHS)
// ============================================================================

/// Bộ sinh mã TOTP (RFC 6238). Chỉ chứa logic thuần Dart, không phụ thuộc
/// Flutter, nên dễ viết unit test và dễ thay đổi tham số/thuật toán về sau.
class TotpGenerator {
  TotpGenerator._();

  /// Chu kỳ mặc định: 30 giây (tham số X trong công thức)
  static const int defaultPeriod = 30;

  /// Số chữ số mặc định: 6 (tham số d trong công thức)
  static const int defaultDigits = 6;

  static const String _base32Alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

  // 2^32: dùng để tách bộ đếm 64 bit thành 2 nửa 32 bit (an toàn trên cả Web)
  static const int _twoPow32 = 4294967296;

  /// Chuẩn hóa secret: viết hoa, bỏ khoảng trắng, dấu "-" và "="
  /// (vd: "abcd efgh-ijkl" -> "ABCDEFGHIJKL")
  static String normalizeSecret(String input) {
    return input.toUpperCase().replaceAll(RegExp(r'[\s=-]'), '');
  }

  /// Sinh mã TOTP tại thời điểm [at] (mặc định: bây giờ).
  /// Ném [FormatException] nếu [base32Secret] chứa ký tự ngoài bảng Base32.
  static String generate(
      String base32Secret, {
        int period = defaultPeriod,
        int digits = defaultDigits,
        DateTime? at,
      }) {
    assert(period > 0, 'period phải > 0');
    assert(digits >= 1 && digits <= 9, 'digits nên trong khoảng 6..8');

    // Bước 1: giải mã khóa Base32 -> mảng byte K
    final key = _base32Decode(base32Secret);

    // Bước 2: T = floor(giây Unix / period). T0 = 0 nên không cần trừ gì thêm.
    final unixSeconds = (at ?? DateTime.now()).millisecondsSinceEpoch ~/ 1000;
    final counter = unixSeconds ~/ period;

    // Bước 3 -> 6: phần còn lại giống hệt HOTP (RFC 4226)
    return _hotp(key, counter, digits);
  }

  /// HOTP(K, C): lõi dùng chung của HOTP và TOTP. Với TOTP, C chính là T.
  static String _hotp(Uint8List key, int counter, int digits) {
    // Bước 3: counter -> 8 byte big-endian.
    // Ghi làm 2 nửa 32 bit (cao rồi thấp) thay vì setUint64 vì setUint64
    // không được hỗ trợ khi chạy trên Web.
    final message = Uint8List(8);
    final view = ByteData.view(message.buffer);
    view.setUint32(0, counter ~/ _twoPow32); // 32 bit cao
    view.setUint32(4, counter % _twoPow32); // 32 bit thấp

    // Bước 4: HMAC-SHA1(K, message) -> 20 byte
    final hash = Hmac(sha1, key).convert(message).bytes;

    // Bước 5: Dynamic Truncation
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
  static Uint8List _base32Decode(String input) {
    final clean = normalizeSecret(input);

    final bytes = <int>[];
    var buffer = 0;
    var bits = 0;
    for (final ch in clean.split('')) {
      final value = _base32Alphabet.indexOf(ch);
      if (value < 0) {
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