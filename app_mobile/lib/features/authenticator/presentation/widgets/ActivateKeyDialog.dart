import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../screens/TotpGenerator.dart';

// ============================================================================
//  POPUP NHẬP KHÓA BÍ MẬT + NÚT KÍCH HOẠT
//  Trả về secret đã chuẩn hóa (String) khi bấm Kích hoạt, hoặc null khi bấm Hủy.
//
//  Cách dùng:
//    final secret = await showDialog<String>(
//      context: context,
//      barrierDismissible: false,
//      builder: (_) => ActivateKeyDialog(existingSecrets: existingSecrets),
//    );
// ============================================================================

class ActivateKeyDialog extends StatefulWidget {
  const ActivateKeyDialog({super.key, required this.existingSecrets});

  /// Các khóa đã có trong danh sách (đã chuẩn hóa) để chặn thêm trùng
  final Set<String> existingSecrets;

  @override
  State<ActivateKeyDialog> createState() => _ActivateKeyDialogState();
}

class _ActivateKeyDialogState extends State<ActivateKeyDialog> {
  static const Color _brandColor = Color(0xFF004B87); // nút Kích hoạt
  static const Color _errorColor = Color(0xFFC61F1F);
  static const Color _fieldFill = Color(0xFFEFEFEF);
  static const int _minKeyLength = 16;

  final TextEditingController _controller = TextEditingController();
  String? _error;

  bool get _hasText => _controller.text.trim().isNotEmpty;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  // Dán nhanh từ clipboard (khóa bí mật thường dài, gõ tay dễ sai)
  Future<void> _paste() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text?.trim();
    if (text == null || text.isEmpty || !mounted) return;
    setState(() {
      _controller.value = TextEditingValue(
        text: text,
        selection: TextSelection.collapsed(offset: text.length),
      );
      _error = null;
    });
  }

  void _clear() {
    setState(() {
      _controller.clear();
      _error = null;
    });
  }

  String? _validate(String secret) {
    if (secret.isEmpty) return 'Vui lòng nhập khóa bí mật';
    if (!RegExp(r'^[A-Z2-7]+$').hasMatch(secret)) {
      return 'Khóa chỉ gồm chữ A–Z và số 2–7';
    }
    if (secret.length < _minKeyLength) {
      return 'Khóa phải có ít nhất $_minKeyLength ký tự';
    }
    if (widget.existingSecrets.contains(secret)) {
      return 'Khóa này đã được thêm trước đó';
    }
    return null;
  }

  void _onActivate() {
    final secret = TotpGenerator.normalizeSecret(_controller.text);
    final error = _validate(secret);
    if (error != null) {
      setState(() => _error = error);
      return;
    }
    Navigator.pop(context, secret);
  }

  OutlineInputBorder _border(Color color, [double width = 1]) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: BorderSide(color: color, width: width),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
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
                'Nhập khóa bí mật',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.w700,
                  color: Colors.black87,
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                'Nhập hoặc dán khóa bí mật được cấp để kích hoạt mã xác thực 6 số.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 14, height: 1.4, color: Colors.black54),
              ),
              const SizedBox(height: 20),
              _buildKeyField(),
              const SizedBox(height: 24),
              _buildFooter(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildKeyField() {
    return TextField(
      maxLength: 30,
      controller: _controller,
      autofocus: true, // mở bàn phím ngay khi popup hiện
      minLines: 1,
      maxLines: 3, // khóa dài sẽ xuống dòng để nhìn được toàn bộ
      // Bàn phím ký tự thuần: tắt gợi ý/tự sửa, tự viết hoa
      keyboardType: TextInputType.visiblePassword,
      textCapitalization: TextCapitalization.characters,
      autocorrect: false,
      enableSuggestions: false,
      textInputAction: TextInputAction.done,
      onChanged: (_) => setState(() => _error = null),
      onSubmitted: (_) => _hasText ? _onActivate() : null,
      style: const TextStyle(fontSize: 16, letterSpacing: 1),
      decoration: InputDecoration(
        hintText: 'Nhập khóa bí mật',
        hintStyle: const TextStyle(
          color: Colors.grey,
          fontSize: 16,
          letterSpacing: 0,
        ),
        errorText: _error,
        errorMaxLines: 2,
        filled: true,
        fillColor: _fieldFill,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        // Rỗng -> nút Dán; có chữ -> nút Xóa
        suffixIcon: _hasText
            ? IconButton(
          icon: const Icon(Icons.close, color: Colors.grey, size: 20),
          tooltip: 'Xóa',
          onPressed: _clear,
        )
            : IconButton(
          icon: const Icon(
            Icons.content_paste_rounded,
            color: _brandColor,
            size: 22,
          ),
          tooltip: 'Dán',
          onPressed: _paste,
        ),
        border: _border(Colors.transparent),
        enabledBorder: _border(Colors.transparent),
        focusedBorder: _border(_brandColor, 1.5),
        errorBorder: _border(_errorColor),
        focusedErrorBorder: _border(_errorColor, 1.5),
      ),
    );
  }

  // Footer: Hủy (viền) | Kích hoạt (nền xanh 0xFF004B87), 2 nút cùng kích thước
  Widget _buildFooter() {
    return Row(
      children: [
        Expanded(
          child: OutlinedButton(
            onPressed: () => Navigator.pop(context),
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
            child: const Text('Hủy'),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: ElevatedButton(
            // Chưa nhập gì -> nút mờ, không bấm được
            onPressed: _hasText ? _onActivate : null,
            style: ElevatedButton.styleFrom(
              minimumSize: const Size(0, 48),
              backgroundColor: _brandColor,
              foregroundColor: Colors.white,
              disabledBackgroundColor: const Color(0x66004B87),
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
            child: const Text('Kích hoạt'),
          ),
        ),
      ],
    );
  }
}