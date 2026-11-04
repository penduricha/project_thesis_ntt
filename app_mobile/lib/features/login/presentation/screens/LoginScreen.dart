import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:my_flutter_app/features/authenticator/presentation/screens/AuthenticatorScreen.dart';

import '../../../../core/constants/AppColors.dart';
import '../../../chat/presentation/screens/ChatListScreen.dart';
import '../../data/model/AdminLogin.dart';
import 'CreateAccountScreen.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  // ---- Màu: giữ nguyên theo Figma, chỉ thêm màu lỗi + viền nhạt ----
  static const Color _fieldFill = Color(0xFFF9F9F9); // nền ô nhập (cũ)
  static const Color _fieldBorder = Color(0xFFEDEDED); // viền nhạt khi bình thường
  static const Color _errorColor = Color(0xFFD93025); // TODO: thay bằng màu lỗi của Figma nếu có
  static const Color _errorBg = Color(0x14D93025); // _errorColor ~8% opacity
  static const Color _errorBgBorder = Color(0x33D93025); // _errorColor ~20% opacity

  final AdminLogin _admin = AdminLogin();

  final _formKey = GlobalKey<FormState>();
  final _idController = TextEditingController();
  final _passwordController = TextEditingController();
  final _idFocus = FocusNode();
  final _passwordFocus = FocusNode();

  bool _obscurePassword = true;

  // Chỉ bắt đầu "soi lỗi" sau lần bấm Login đầu tiên.
  // Trước đó người dùng đang gõ dở thì không báo đỏ.
  bool _submitted = false;

  // Lỗi cấp form (vd: sai tài khoản / mật khẩu) hiển thị ngay trên nút Login.
  String? _formError;

  @override
  void dispose() {
    _idController.dispose();
    _passwordController.dispose();
    _idFocus.dispose();
    _passwordFocus.dispose();
    super.dispose();
  }

  // ---------------------------------------------------------------------------
  // Validators: nói rõ người dùng cần làm gì, không đổ lỗi.
  // ---------------------------------------------------------------------------
  String? _validateId(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Enter your admin Id';
    }
    return null;
  }

  String? _validatePassword(String? value) {
    if (value == null || value.isEmpty) {
      return 'Enter your password';
    }
    return null;
  }

  // ---------------------------------------------------------------------------
  // Xử lý bấm Login
  // ---------------------------------------------------------------------------
  void _onSubmit() {
    setState(() {
      _submitted = true; // từ giờ ô nào sửa là lỗi tự cập nhật theo thời gian thực
      _formError = null;
    });

    final isValid = _formKey.currentState!.validate();

    if (!isValid) {
      HapticFeedback.lightImpact(); // rung nhẹ báo có lỗi
      // Đưa con trỏ về ô lỗi đầu tiên để người dùng sửa luôn
      if (_validateId(_idController.text) != null) {
        _idFocus.requestFocus();
      } else {
        _passwordFocus.requestFocus();
      }
      return;
    }

    // Form đã đủ dữ liệu -> kiểm tra tiếp bằng Model
    if (!_admin.validate()) {
      HapticFeedback.lightImpact();
      setState(() {
        _formError = 'Admin Id or password is incorrect. Check and try again.';
      });
      return;
    }

    FocusScope.of(context).unfocus();
    // Đăng nhập thành công -> vào khung Tab (Authenticator | Chat), xóa LoginScreen khỏi lịch sử
    Navigator.pushNamedAndRemoveUntil(context, '/home', (route) => false);
  }

  // ---------------------------------------------------------------------------
  // UI
  // ---------------------------------------------------------------------------
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        // Chạm ra ngoài để đóng bàn phím
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: () => FocusScope.of(context).unfocus(),
          child: Center(
            child: SingleChildScrollView(
              keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
              padding: const EdgeInsets.symmetric(horizontal: 24.0),
              child: Form(
                key: _formKey,
                autovalidateMode: _submitted
                    ? AutovalidateMode.onUserInteraction
                    : AutovalidateMode.disabled,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const SizedBox(
                      width: double.infinity,
                      child: Text(
                        'Login',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontSize: 24,
                          color: AppColors.primaryBlue,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),

                    const SizedBox(height: 40),

                    // ---------- Admin Id ----------
                    const _FieldLabel('Admin Id'),
                    const SizedBox(height: 8),
                    TextFormField(
                      controller: _idController,
                      focusNode: _idFocus,
                      keyboardType: TextInputType.number,
                      textInputAction: TextInputAction.next,
                      cursorColor: AppColors.primaryBlue,
                      inputFormatters: [
                        FilteringTextInputFormatter.digitsOnly, // chỉ cho nhập 0-9
                        LengthLimitingTextInputFormatter(10), // tối đa 10 ký tự
                      ],
                      validator: _validateId,
                      onChanged: (value) {
                        _admin.adminId = int.tryParse(value);
                        if (_formError != null) setState(() => _formError = null);
                      },
                      onFieldSubmitted: (_) => _passwordFocus.requestFocus(),
                      decoration: _inputDecoration(hint: 'Enter admin Id'),
                    ),

                    const SizedBox(height: 4),

                    // ---------- Password ----------
                    const _FieldLabel('Password'),
                    const SizedBox(height: 8),
                    TextFormField(
                      controller: _passwordController,
                      focusNode: _passwordFocus,
                      obscureText: _obscurePassword,
                      textInputAction: TextInputAction.done,
                      cursorColor: AppColors.primaryBlue,
                      validator: _validatePassword,
                      onChanged: (value) {
                        _admin.password = value;
                        if (_formError != null) setState(() => _formError = null);
                      },
                      onFieldSubmitted: (_) => _onSubmit(), // phím Done = bấm Login
                      decoration: _inputDecoration(
                        hint: 'Enter password',
                        suffixIcon: IconButton(
                          tooltip: _obscurePassword ? 'Show password' : 'Hide password',
                          icon: Icon(
                            _obscurePassword
                                ? Icons.visibility_off_outlined
                                : Icons.visibility_outlined,
                            color: Colors.grey,
                            size: 20,
                          ),
                          onPressed: () {
                            setState(() => _obscurePassword = !_obscurePassword);
                          },
                        ),
                      ),
                    ),

                    const SizedBox(height: 16),

                    // ---------- Lỗi cấp form (sai tài khoản/mật khẩu) ----------
                    _buildFormError(),

                    // ---------- Nút Login ----------
                    // Luôn cho bấm (không disable) để người dùng nhận được phản hồi lỗi
                    SizedBox(
                      width: double.infinity,
                      height: 50,
                      child: ElevatedButton(
                        onPressed: _onSubmit,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.primaryBlue,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(8),
                          ),
                          elevation: 0,
                        ),
                        child: const Text(
                          'Login',
                          style: TextStyle(
                            fontSize: 16,
                            color: Colors.white,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),

                    const SizedBox(height: 20),

                    Center(
                      child: TextButton(
                        onPressed: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (context) => const CreateAccountScreen(),
                            ),
                          );
                        },
                        child: const Text(
                          'Create account',
                          style: TextStyle(
                            color: AppColors.primaryBlue,
                            fontSize: 15,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  // Banner lỗi cấp form: tự co giãn mượt khi xuất hiện/biến mất
  Widget _buildFormError() {
    return AnimatedSize(
      duration: const Duration(milliseconds: 200),
      curve: Curves.easeOut,
      alignment: Alignment.topCenter,
      child: _formError == null
          ? const SizedBox(width: double.infinity, height: 0)
          : Padding(
        padding: const EdgeInsets.only(bottom: 16),
        child: Semantics(
          liveRegion: true, // screen reader tự đọc khi lỗi xuất hiện
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            decoration: BoxDecoration(
              color: _errorBg,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: _errorBgBorder),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.error_outline, color: _errorColor, size: 18),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    _formError!,
                    style: const TextStyle(
                      color: _errorColor,
                      fontSize: 13,
                      height: 1.3,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  // Style dùng chung cho cả 2 ô nhập
  InputDecoration _inputDecoration({required String hint, Widget? suffixIcon}) {
    OutlineInputBorder border(Color color, [double width = 1]) {
      return OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: BorderSide(color: color, width: width),
      );
    }

    return InputDecoration(
      hintText: hint,
      hintStyle: const TextStyle(color: Colors.grey, fontSize: 14),
      suffixIcon: suffixIcon,
      filled: true,
      fillColor: _fieldFill,

      // Giữ sẵn 1 dòng trống dưới mỗi ô -> khi lỗi hiện ra, layout không bị giật/nhảy
      helperText: ' ',
      errorMaxLines: 2,
      errorStyle: const TextStyle(color: _errorColor, fontSize: 12, height: 1.2),

      border: border(_fieldBorder),
      enabledBorder: border(_fieldBorder), // đặt Colors.transparent nếu muốn bỏ viền như cũ
      focusedBorder: border(AppColors.primaryBlue, 1.5),
      errorBorder: border(_errorColor),
      focusedErrorBorder: border(_errorColor, 1.5),
    );
  }
}

class _FieldLabel extends StatelessWidget {
  const _FieldLabel(this.text);
  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: const TextStyle(
        fontSize: 14,
        fontWeight: FontWeight.w500,
        color: Colors.black87,
      ),
    );
  }
}