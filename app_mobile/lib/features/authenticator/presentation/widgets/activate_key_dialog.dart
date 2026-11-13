import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../screens/totp_generator.dart';

// ============================================================================
//  POPUP NHẬP KHÓA BÍ MẬT (Base32) + NÚT ACTIVATE
//  Trả về secret đã chuẩn hóa (String) khi bấm Activate, hoặc null khi bấm Cancel.
//
//  Cách báo lỗi GIỐNG màn Login:
//   - Lúc mới mở, người dùng đang gõ dở -> KHÔNG báo lỗi.
//   - Bấm Activate (hoặc phím Done) lần đầu -> mới kiểm tra, lỗi hiện ngay dưới ô
//     (viền đỏ + icon + chữ đỏ), con trỏ quay lại ô nhập.
//   - Từ đó trở đi lỗi tự cập nhật theo từng ký tự gõ, hết lỗi thì tự biến mất.
//
//  Cách dùng:
//    final secret = await showDialog<String>(
//      context: context,
//      barrierDismissible: false,
//      builder: (_) => ActivateKeyDialog(
//        existingSecrets: existingSecrets,
//        // Gọi server kiểm tra khóa. Trả về câu lỗi (hiện dưới ô nhập) hoặc null nếu thành công.
//        onVerify: (secret) async { ... return null; },
//      ),
//    );
//
//  Trong lúc onVerify chạy: nút Activate quay vòng, ô nhập và nút Cancel bị khóa,
//  nút Back không đóng được popup (tránh gửi trùng / bỏ dở giữa chừng).
// ============================================================================

class ActivateKeyDialog extends StatefulWidget {
  const ActivateKeyDialog({
    super.key,
    required this.existingSecrets,
    required this.onVerify,
  });

  /// Các khóa đã có trong danh sách (đã chuẩn hóa) để chặn thêm trùng
  final Set<String> existingSecrets;

  /// Được gọi sau khi khóa qua kiểm tra định dạng. Trả về câu lỗi nếu server từ chối,
  /// hoặc null nếu kích hoạt thành công (popup tự đóng và trả về secret).
  final Future<String?> Function(String secret) onVerify;

  @override
  State<ActivateKeyDialog> createState() => _ActivateKeyDialogState();
}

class _ActivateKeyDialogState extends State<ActivateKeyDialog> {
  static const Color _brandColor = Color(0xFF004B87); // nút Activate
  static const Color _errorColor = Color(0xFFC61F1F);
  static const Color _fieldFill = Color(0xFFEFEFEF);

  // ---- Quy tắc khóa (khớp EncryptionUtil.generateSecretKey() bên Java) ----
  // Java: 20 byte ngẫu nhiên (160 bit) -> Base32 RFC 4648, KHÔNG padding '='.
  // 160 bit / 5 bit mỗi ký tự = đúng 32 ký tự, bảng chữ cái: A-Z và 2-7.
  static const int _keyLength = 32;
  static final RegExp _base32Pattern = RegExp('^[A-Z2-7]{$_keyLength}\$');
  static final RegExp _invalidChar = RegExp(r'[^A-Z2-7]');

  final TextEditingController _controller = TextEditingController();
  final FocusNode _focusNode = FocusNode();

  String? _error;

  // Chỉ bắt đầu "soi lỗi" sau lần bấm Activate đầu tiên (giống _submitted ở Login).
  bool _submitted = false;

  // Đang chờ server trả lời
  bool _loading = false;

  bool get _hasText => _controller.text.trim().isNotEmpty;

  String get _normalized => TotpGenerator.normalizeSecret(_controller.text);

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  // ---------------------------------------------------------------------------
  // Validator: trả về câu lỗi ngắn gọn (1 dòng) hoặc null nếu hợp lệ.
  // ---------------------------------------------------------------------------
  String? _validate(String secret) {
    if (secret.isEmpty) return 'Enter your secret key';

    if (_invalidChar.hasMatch(secret)) {
      return 'Use only letters A-Z and digits 2-7';
    }

    if (secret.length != _keyLength) {
      return 'Key must be $_keyLength characters (now ${secret.length})';
    }

    // Chốt chặn cuối bằng biểu thức chính quy đầy đủ ^[A-Z2-7]{32}$
    if (!_base32Pattern.hasMatch(secret)) return 'Invalid key format';

    if (widget.existingSecrets.contains(secret)) {
      return 'This key has already been added';
    }
    return null;
  }

  // ---------------------------------------------------------------------------
  // Sự kiện
  // ---------------------------------------------------------------------------

  // Bấm Activate hoặc phím Done trên bàn phím
  Future<void> _onActivate() async {
    if (_loading) return;

    final secret = _normalized;
    final error = _validate(secret);

    setState(() {
      _submitted = true; // từ giờ ô nào sửa là lỗi tự cập nhật
      _error = error;
    });

    if (error != null) {
      HapticFeedback.lightImpact();
      _focusNode.requestFocus();
      return;
    }

    // Định dạng đúng -> nhờ server đối chiếu khóa (DB + Redis)
    setState(() => _loading = true);
    String? serverError;
    try {
      serverError = await widget.onVerify(secret);
    } catch (_) {
      serverError = 'Something went wrong. Please try again.';
    }
    if (!mounted) return;

    if (serverError == null) {
      Navigator.pop(context, secret); // server đã bật 2FA xong
      return;
    }

    setState(() {
      _loading = false;
      _error = serverError;
    });
    HapticFeedback.lightImpact();
    _focusNode.requestFocus();
  }

  // Người dùng gõ: trước lần Activate đầu thì im lặng, sau đó cập nhật lỗi trực tiếp
  void _onChanged(String _) {
    setState(() {
      if (_submitted) _error = _validate(_normalized);
    });
  }

  // Dán nhanh từ clipboard (khóa bí mật thường dài, gõ tay dễ sai)
  Future<void> _paste() async {
    if (_loading) return;
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text?.trim();
    if (text == null || text.isEmpty || !mounted) return;
    setState(() {
      _controller.value = TextEditingValue(
        text: text.toUpperCase(),
        selection: TextSelection.collapsed(offset: text.length),
      );
      _error = _submitted ? _validate(_normalized) : null;
    });
    _focusNode.requestFocus();
  }

  // Xóa sạch: người dùng chủ động làm lại nên không báo lỗi "trống" ngay
  void _clear() {
    if (_loading) return;
    setState(() {
      _controller.clear();
      _error = null;
    });
    _focusNode.requestFocus();
  }

  // ---------------------------------------------------------------------------
  // UI
  // ---------------------------------------------------------------------------
  static OutlineInputBorder _border(Color color, [double width = 1]) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: BorderSide(color: color, width: width),
    );
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: !_loading, // đang gọi server thì không cho Back
      child: Dialog(
        backgroundColor: Colors.white,
        surfaceTintColor: Colors.transparent,
        insetPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 24),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 400),
          // Cuộn được để không bị overflow khi bàn phím mở hoặc màn hình xoay ngang
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(24, 28, 24, 24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Center(
                  child: Container(
                    width: 60,
                    height: 60,
                    decoration: const BoxDecoration(
                      color: Color(0xFFE6EEF5),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.vpn_key_outlined,
                      color: _brandColor,
                      size: 28,
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                const Text(
                  'Enter secret key',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w700,
                    color: Colors.black87,
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Enter or paste the 32-character secret key you were given to activate your 6-digit verification code.',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 14, height: 1.4, color: Colors.black54),
                ),
                const SizedBox(height: 20),
                _buildKeyField(),
                _FieldError(_error), // dòng lỗi tự vẽ, y hệt màn Login
                const SizedBox(height: 12),
                _buildFooter(),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildKeyField() {
    final hasError = _error != null;

    return TextField(
      controller: _controller,
      focusNode: _focusNode,
      autofocus: true, // mở bàn phím ngay khi popup hiện
      readOnly: _loading,
      minLines: 1,
      maxLines: 3, // khóa dài sẽ xuống dòng để nhìn được toàn bộ
      // Bàn phím ký tự thuần: tắt gợi ý/tự sửa. Chữ gõ vào tự viết HOA.
      keyboardType: TextInputType.visiblePassword,
      textCapitalization: TextCapitalization.characters,
      inputFormatters: [_UpperCaseFormatter()],
      autocorrect: false,
      enableSuggestions: false,
      textInputAction: TextInputAction.done,
      cursorColor: _brandColor,
      onChanged: _onChanged,
      onSubmitted: (_) => _onActivate(), // phím Done = bấm Activate
      style: const TextStyle(fontSize: 16, letterSpacing: 1),
      decoration: InputDecoration(
        hintText: 'Enter secret key',
        hintStyle: const TextStyle(
          color: Colors.grey,
          fontSize: 16,
          letterSpacing: 0,
        ),
        filled: true,
        fillColor: _fieldFill,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        // Rỗng -> nút Paste; có chữ -> nút Clear
        suffixIcon: _hasText
            ? IconButton(
          icon: const Icon(Icons.close, color: Colors.grey, size: 20),
          tooltip: 'Clear',
          onPressed: _clear,
        )
            : IconButton(
          icon: const Icon(
            Icons.content_paste_rounded,
            color: _brandColor,
            size: 22,
          ),
          tooltip: 'Paste',
          onPressed: _paste,
        ),
        // Có lỗi -> viền đỏ (kể cả khi đang focus), không lỗi -> không viền
        border: _border(hasError ? _errorColor : Colors.transparent),
        enabledBorder: _border(hasError ? _errorColor : Colors.transparent),
        focusedBorder:
        hasError ? _border(_errorColor, 1.5) : _border(_brandColor, 1.5),
      ),
    );
  }

  // Footer: Cancel (viền) | Activate (nền xanh 0xFF004B87), 2 nút cùng kích thước
  Widget _buildFooter() {
    return Row(
      children: [
        Expanded(
          child: OutlinedButton(
            onPressed: _loading ? null : () => Navigator.pop(context),
            style: OutlinedButton.styleFrom(
              minimumSize: const Size(0, 48),
              foregroundColor: Colors.black87,
              side: const BorderSide(color: Color(0xFFBDBDBD), width: 1.2),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10),
              ),
              textStyle: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w600,
              ),
            ),
            child: const Text('Cancel'),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: ElevatedButton(
            // Luôn cho bấm (để nhận phản hồi lỗi), chỉ khóa khi đang gọi server.
            onPressed: _loading ? null : _onActivate,
            style: ElevatedButton.styleFrom(
              minimumSize: const Size(0, 48),
              backgroundColor: _brandColor,
              foregroundColor: Colors.white,
              // Giữ nguyên màu xanh khi đang tải (mặc định nút bị khóa sẽ chuyển xám)
              disabledBackgroundColor: _brandColor,
              disabledForegroundColor: Colors.white,
              elevation: 0,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10),
              ),
              textStyle: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w600,
              ),
            ),
            child: _loading
                ? const SizedBox(
              width: 22,
              height: 22,
              child: CircularProgressIndicator(
                strokeWidth: 2.5,
                color: Colors.white,
              ),
            )
                : const Text('Activate'),
          ),
        ),
      ],
    );
  }
}

// Tự viết HOA mọi ký tự gõ/dán vào (Base32 chỉ dùng chữ hoa), giữ nguyên vị trí con trỏ.
class _UpperCaseFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(
      TextEditingValue oldValue,
      TextEditingValue newValue,
      ) {
    return newValue.copyWith(text: newValue.text.toUpperCase());
  }
}

// Dòng lỗi dưới ô nhập: sát lề trái, chữ 14, giữ sẵn chỗ để layout không nhảy.
// Cùng kiểu với _FieldError của màn Login (icon tròn + chữ đỏ).
class _FieldError extends StatelessWidget {
  const _FieldError(this.message);

  final String? message;

  static const Color _errorColor = Color(0xFFC61F1F);

  @override
  Widget build(BuildContext context) {
    return AnimatedSize(
      duration: const Duration(milliseconds: 150),
      curve: Curves.easeOut,
      alignment: Alignment.topLeft,
      child: Container(
        width: double.infinity,
        constraints: const BoxConstraints(minHeight: 24), // giữ chỗ 1 dòng
        padding: const EdgeInsets.only(top: 6),
        child: message == null
            ? null
            : Semantics(
          liveRegion: true, // screen reader tự đọc khi lỗi xuất hiện
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Padding(
                padding: EdgeInsets.only(top: 1),
                child: Icon(
                  Icons.error_outline,
                  color: _errorColor,
                  size: 16,
                ),
              ),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  message!,
                  style: const TextStyle(
                    color: _errorColor,
                    fontSize: 14,
                    fontWeight: FontWeight.w500,
                    height: 1.2,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}