import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/localization/app_strings.dart';
import '../../../../core/localization/mobile_server_messages.dart';
import '../../../settings/presentation/settings_screen.dart';
import '../../data/model/login_result.dart';
import '../controller/login_controller.dart';
import '../widgets/error_dialog.dart';
import 'create_account_screen.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final LoginController _controller = LoginController();

  final _formKey = GlobalKey<FormState>();
  final _idController = TextEditingController();
  final _passwordController = TextEditingController();
  final _idFocus = FocusNode();
  final _passwordFocus = FocusNode();

  bool _obscurePassword = true;

  // Chỉ bắt đầu "soi lỗi" sau lần bấm Login đầu tiên.
  // Trước đó người dùng đang gõ dở thì không báo đỏ.
  bool _submitted = false;

  Locale? _lastLocale;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final current = Localizations.maybeLocaleOf(context);
    if (_submitted && _lastLocale != current) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _formKey.currentState?.validate();
      });
    }
    _lastLocale = current;
  }

  @override
  void dispose() {
    _controller.dispose();
    _idController.dispose();
    _passwordController.dispose();
    _idFocus.dispose();
    _passwordFocus.dispose();
    super.dispose();
  }

  // ---------------------------------------------------------------------------
  // Validators (lỗi ngay dưới ô nhập): chỉ kiểm tra "đã nhập chưa".
  // Sai tài khoản / mật khẩu thì báo bằng popup, không báo ở đây.
  // ---------------------------------------------------------------------------
  String? _validateId(String? value) {
    if (value == null || value.trim().isEmpty) {
      return AppStrings.of(context).text('enterId');
    }
    return null;
  }

  String? _validatePassword(String? value) {
    if (value == null || value.isEmpty) {
      return AppStrings.of(context).text('enterPassword');
    }
    return null;
  }

  // ---------------------------------------------------------------------------
  // Xử lý bấm Login
  // ---------------------------------------------------------------------------
  Future<void> _onSubmit() async {
    // Đang gọi API thì bỏ qua (ví dụ bấm phím Done trên bàn phím)
    if (_controller.isLoading) return;

    setState(() => _submitted = true); // từ giờ ô nào sửa là lỗi tự cập nhật

    // 1) Còn ô trống -> báo lỗi ngay dưới ô
    if (!_formKey.currentState!.validate()) {
      HapticFeedback.lightImpact();
      if (_validateId(_idController.text) != null) {
        _idFocus.requestFocus();
      } else {
        _passwordFocus.requestFocus();
      }
      return;
    }

    FocusScope.of(context).unfocus(); // hạ bàn phím trước khi gọi API / hiện popup

    final adminId = int.tryParse(_idController.text.trim());
    if (adminId == null) return; // không xảy ra: ô nhập chỉ cho phép chữ số

    // 2) Đủ dữ liệu -> gọi API (spinner + khóa form do controller điều khiển)
    final result = await _controller.login(
      adminId: adminId,
      password: _passwordController.text,
    );
    if (!mounted) return;

    await _handleLoginResult(result, adminId);
  }

  Future<void> _handleLoginResult(LoginResult result, int adminId) async {
    switch (result) {
    // Trường hợp 1: thành công -> vào khung Tab (Authenticator | Chat),
    // xóa LoginScreen khỏi lịch sử. Token đã được controller lưu.
      case LoginSuccess():
        Navigator.pushNamedAndRemoveUntil(context, '/home', (route) => false);

    // Trường hợp 2: sai Admin Id hoặc mật khẩu.
      case LoginRejected(:final message):
        HapticFeedback.mediumImpact();
        await showErrorDialog(
          context,
          icon: Icons.lock_outline_rounded,
          title: AppStrings.of(context).text('loginFailed'),
          message: '${_loginRejectedMessage(message)}\n${AppStrings.of(context).text('retry')}',
          buttonText: 'OK',
        );
        _focusPasswordAndSelectAll();

    // Trường hợp 2a: tài khoản bật 2FA -> yêu cầu nhập mã 6 số hoặc đang bị khóa tạm.
      case LoginTotpRequired(:final reason, :final retryAfterSeconds):
        if (reason == LoginTotpFailureReason.locked) {
          HapticFeedback.heavyImpact();
          await showErrorDialog(
            context,
            icon: Icons.timer_off_outlined,
            title: AppStrings.of(context).text('tooManyAttempts'),
            message: retryAfterSeconds > 0
                ? MobileServerMessages.lookup('loginLockedSeconds', params: {'seconds': retryAfterSeconds})!
                : MobileServerMessages.lookup('loginLocked')!,
            buttonText: 'OK',
          );
          return;
        }
        HapticFeedback.mediumImpact();
        final code = await _promptTotpCode(MobileServerMessages.lookup(
          reason == LoginTotpFailureReason.required ? 'totpRequired' : 'totpInvalid',
        )!);
        if (!mounted || code == null) return;
        final retryResult = await _controller.login(
          adminId: adminId,
          password: _passwordController.text,
          totpCode: code,
        );
        if (!mounted) return;
        await _handleLoginResult(retryResult, adminId);

    // Trường hợp 3: tài khoản đã đăng nhập ở thiết bị khác.
      case LoginBlockedByOtherDevice blocked:
        HapticFeedback.heavyImpact();
        await showErrorDialog(
          context,
          icon: Icons.devices_other_rounded,
          title: AppStrings.of(context).text('accountInUse'),
          message: _buildBlockedMessage(blocked),
          buttonText: 'OK',
        );

    // Không gọi được server (mất mạng, quá thời gian chờ, lỗi 5xx).
      case LoginFailure(:final message, :final isNetworkFailure):
        HapticFeedback.mediumImpact();
        await showErrorDialog(
          context,
          icon: Icons.wifi_off_rounded,
          title: AppStrings.of(context).text('connectionError'),
          message: isNetworkFailure ? AppStrings.of(context).text('network') : message,
          buttonText: 'OK',
        );
    }
  }

  Future<String?> _promptTotpCode(String message) async {
    final codeController = TextEditingController();
    String? inlineError;
    try {
      return await showDialog<String>(
        context: context,
        barrierDismissible: false,
        builder: (dialogContext) {
          return StatefulBuilder(
            builder: (context, setDialogState) {
              return AlertDialog(
                title: Text(AppStrings.of(context).text('twoFactor')),
                content: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      message,
                      style: TextStyle(fontSize: 14, color: Theme.of(context).colorScheme.onSurface),
                    ),
                    const SizedBox(height: 14),
                    TextField(
                      controller: codeController,
                      autofocus: true,
                      keyboardType: TextInputType.number,
                      textAlign: TextAlign.center,
                      maxLength: 6,
                      inputFormatters: [
                        FilteringTextInputFormatter.digitsOnly,
                        LengthLimitingTextInputFormatter(6),
                      ],
                      decoration: InputDecoration(
                        hintText: '000000',
                        counterText: '',
                        errorText: inlineError,
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                    ),
                  ],
                ),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.of(dialogContext).pop(),
                    child: Text(AppStrings.of(context).text('cancel')),
                  ),
                  ElevatedButton(
                    onPressed: () {
                      final trimmed = codeController.text.trim();
                      if (!RegExp(r'^\d{6}$').hasMatch(trimmed)) {
                        setDialogState(() {
                          inlineError = AppStrings.of(context).text('enterSix');
                        });
                        return;
                      }
                      Navigator.of(dialogContext).pop(trimmed);
                    },
                    child: Text(AppStrings.of(context).text('verify')),
                  ),
                ],
              );
            },
          );
        },
      );
    } finally {
      codeController.dispose();
    }
  }

  // Legacy login failures do not carry a machine-readable code. Only override
  // exact documented messages and retain any unrecognised server text.
  String _loginRejectedMessage(String original) {
    if (original == 'Admin Id or password is wrong.' ||
        original == 'Admin Id or password is incorrect.') {
      return MobileServerMessages.lookup('credentials') ?? original;
    }
    return original;
  }

  /// Nội dung popup trường hợp 3: câu cảnh báo của server + thiết bị đang giữ phiên.
  String _buildBlockedMessage(LoginBlockedByOtherDevice blocked) {
    final deviceName = blocked.deviceName;
    final platform = blocked.platform;
    final loggedInAt = blocked.loggedInAt;

    return [
      MobileServerMessages.lookup('alreadyLoggedIn') ?? blocked.message,
      if (deviceName != null)
        '\n${AppStrings.of(context).text('device')}: $deviceName${platform != null ? ' ($platform)' : ''}',
      if (loggedInAt != null) '${AppStrings.of(context).text('since')}: ${_formatLocalTime(loggedInAt)}',
    ].join('\n');
  }

  // Đóng popup xong: đưa con trỏ về ô mật khẩu, bôi đen sẵn để gõ lại luôn
  void _focusPasswordAndSelectAll() {
    if (!mounted) return;
    _passwordController.selection = TextSelection(
      baseOffset: 0,
      extentOffset: _passwordController.text.length,
    );
    _passwordFocus.requestFocus();
  }

  // ---------------------------------------------------------------------------
  // UI  (Scaffold này KHÔNG có bottomNavigationBar -> không có menu Tab)
  // ---------------------------------------------------------------------------
  @override
  Widget build(BuildContext context) {
    final autoMode = _submitted
        ? AutovalidateMode.onUserInteraction
        : AutovalidateMode.disabled;

    return Scaffold(
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        actions: [IconButton(
          tooltip: AppStrings.of(context).text('settings'),
          onPressed: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const SettingsScreen())),
          icon: const Icon(Icons.settings_outlined),
        )],
      ),
      body: SafeArea(
        // Chạm ra ngoài để đóng bàn phím
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: () => FocusScope.of(context).unfocus(),
          child: Center(
            child: SingleChildScrollView(
              keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
              padding: const EdgeInsets.symmetric(horizontal: 24.0),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 460),
                child: Card(
                  elevation: 0,
                  shape: RoundedRectangleBorder(side: BorderSide(color: Theme.of(context).dividerColor), borderRadius: BorderRadius.circular(12)),
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Form(
                key: _formKey,
                // Chỉ phần phụ thuộc trạng thái loading mới được dựng lại
                child: ListenableBuilder(
                  listenable: _controller,
                  builder: (context, _) {
                    final isLoading = _controller.isLoading;

                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SizedBox(
                          width: double.infinity,
                          child: Text(
                            'Authenticator',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                              fontSize: 24,
                              color: Theme.of(context).colorScheme.primary,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),

                        const SizedBox(height: 40),

                        // ---------- Admin Id ----------
                        _AuthTextField(
                          label: AppStrings.of(context).text('adminId'),
                          hint: AppStrings.of(context).text('enterId'),
                          controller: _idController,
                          focusNode: _idFocus,
                          enabled: !isLoading,
                          autoValidateMode: autoMode,
                          validator: _validateId,
                          keyboardType: TextInputType.number,
                          textInputAction: TextInputAction.next,
                          inputFormatters: [
                            FilteringTextInputFormatter.digitsOnly, // chỉ cho nhập 0-9
                            LengthLimitingTextInputFormatter(10), // tối đa 10 ký tự
                          ],
                          onSubmitted: (_) => _passwordFocus.requestFocus(),
                        ),

                        const SizedBox(height: 4),

                        // ---------- Password ----------
                        _AuthTextField(
                          label: AppStrings.of(context).text('password'),
                          hint: AppStrings.of(context).text('enterPassword'),
                          controller: _passwordController,
                          focusNode: _passwordFocus,
                          enabled: !isLoading,
                          autoValidateMode: autoMode,
                          validator: _validatePassword,
                          obscureText: _obscurePassword,
                          textInputAction: TextInputAction.done,
                          onSubmitted: (_) => _onSubmit(), // phím Done = bấm Login
                          suffixIcon: IconButton(
                            tooltip:
                            AppStrings.of(context).text(_obscurePassword ? 'showPassword' : 'hidePassword'),
                            icon: Icon(
                              _obscurePassword
                                  ? Icons.visibility_off_outlined
                                  : Icons.visibility_outlined,
                              color: Colors.grey,
                              size: 20,
                            ),
                            onPressed: isLoading
                                ? null
                                : () {
                              setState(
                                    () => _obscurePassword = !_obscurePassword,
                              );
                            },
                          ),
                        ),

                        const SizedBox(height: 12),

                        // ---------- Nút Login ----------
                        // Bình thường luôn cho bấm (để nhận phản hồi lỗi).
                        // Đang gọi API: chữ "Login" -> spinner, nút bị khóa.
                        SizedBox(
                          width: double.infinity,
                          height: 50,
                          child: ElevatedButton(
                            onPressed: isLoading ? null : _onSubmit,
                            style: ElevatedButton.styleFrom(
                              backgroundColor: AppColors.primaryBlue,
                              // Giữ nguyên màu xanh khi bị khóa (mặc định sẽ xám)
                              disabledBackgroundColor: AppColors.primaryBlue,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(8),
                              ),
                              elevation: 0,
                            ),
                            child: AnimatedSwitcher(
                              duration: const Duration(milliseconds: 200),
                              child: isLoading
                                  ? const _ButtonSpinner(key: ValueKey('spinner'))
                                  : Text(
                                AppStrings.of(context).text('login'),
                                key: ValueKey('label'),
                                style: TextStyle(
                                  fontSize: 16,
                                  color: Colors.white,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            ),
                          ),
                        ),

                        const SizedBox(height: 20),

                        Center(
                          child: TextButton(
                            onPressed: isLoading
                                ? null
                                : () {
                              Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (context) =>
                                  const CreateAccountScreen(),
                                ),
                              );
                            },
                            child: Text(
                              AppStrings.of(context).text('createAccount'),
                              style: TextStyle(
                                color: Theme.of(context).colorScheme.primary,
                                fontSize: 15,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ),
                        ),
                      ],
                    );
                  },
                ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Định dạng giờ địa phương: dd/MM/yyyy HH:mm
String _formatLocalTime(DateTime time) {
  final local = time.toLocal();
  String twoDigits(int value) => value.toString().padLeft(2, '0');
  return '${twoDigits(local.day)}/${twoDigits(local.month)}/${local.year} '
      '${twoDigits(local.hour)}:${twoDigits(local.minute)}';
}

// Vòng xoay trong nút Login khi đang xử lý API (kích thước cố định để nút không nhảy).
class _ButtonSpinner extends StatelessWidget {
  const _ButtonSpinner({super.key});

  @override
  Widget build(BuildContext context) {
    return const SizedBox(
      width: 22,
      height: 22,
      child: CircularProgressIndicator(
        strokeWidth: 2.5,
        color: Colors.white,
        semanticsLabel: 'Logging in',
      ),
    );
  }
}

// =============================================================================
// Ô nhập có nhãn + dòng lỗi TỰ VẼ.
//
// Vì sao không dùng errorText/helperText của InputDecoration?
// Flutter tự thụt lề dòng lỗi theo contentPadding (~16dp) và ta không chỉnh
// được -> lỗi không thẳng hàng với nhãn/ô nhập. Tự vẽ dòng lỗi thì canh sát
// lề trái bằng đúng với nhãn và mép ô nhập, đồng thời chỉnh cỡ chữ thoải mái.
// =============================================================================
class _AuthTextField extends StatelessWidget {
  const _AuthTextField({
    required this.label,
    required this.hint,
    required this.controller,
    required this.focusNode,
    required this.validator,
    required this.autoValidateMode,
    this.enabled = true,
    this.onChanged,
    this.onSubmitted,
    this.keyboardType,
    this.textInputAction,
    this.inputFormatters,
    this.obscureText = false,
    this.suffixIcon,
  });

  final String label;
  final String hint;
  final TextEditingController controller;
  final FocusNode focusNode;
  final String? Function(String value) validator;
  final AutovalidateMode autoValidateMode;
  final bool enabled; // false: khóa ô nhập khi đang gọi API
  final ValueChanged<String>? onChanged;
  final ValueChanged<String>? onSubmitted;
  final TextInputType? keyboardType;
  final TextInputAction? textInputAction;
  final List<TextInputFormatter>? inputFormatters;
  final bool obscureText;
  final Widget? suffixIcon;

  @override
  Widget build(BuildContext context) {
    return FormField<String>(
      autovalidateMode: autoValidateMode,
      // Đọc thẳng từ controller để giá trị không bao giờ bị lệch
      validator: (_) => validator(controller.text),
      builder: (state) {
        final errorText = state.errorText;
        final hasError = errorText != null;

        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              label,
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w500,
                color: Theme.of(context).colorScheme.onSurface,
              ),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: controller,
              focusNode: focusNode,
              enabled: enabled,
              obscureText: obscureText,
              keyboardType: keyboardType,
              textInputAction: textInputAction,
              inputFormatters: inputFormatters,
              cursorColor: AppColors.primaryBlue,
              onChanged: (value) {
                state.didChange(value); // báo cho FormField: người dùng đã gõ
                onChanged?.call(value);
              },
              onSubmitted: onSubmitted,
              decoration: InputDecoration(
                hintText: hint,
                hintStyle: const TextStyle(color: Colors.grey, fontSize: 14),
                suffixIcon: suffixIcon,
                filled: true,
                fillColor: enabled ? Theme.of(context).colorScheme.surface : Theme.of(context).colorScheme.surfaceContainerHighest,
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 16,
                ),
                border: _border(hasError ? Theme.of(context).colorScheme.error : Theme.of(context).dividerColor),
                enabledBorder: _border(hasError ? Theme.of(context).colorScheme.error : Theme.of(context).dividerColor),
                disabledBorder: _border(Theme.of(context).dividerColor),
                focusedBorder: hasError
                    ? _border(Theme.of(context).colorScheme.error, 1.5)
                    : _border(Theme.of(context).colorScheme.primary, 1.5),
              ),
            ),
            _FieldError(errorText),
          ],
        );
      },
    );
  }

  static OutlineInputBorder _border(Color color, [double width = 1]) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(8),
      borderSide: BorderSide(color: color, width: width),
    );
  }
}

// Dòng lỗi dưới ô nhập: sát lề trái, chữ 14, giữ sẵn chỗ để layout không nhảy.
class _FieldError extends StatelessWidget {
  const _FieldError(this.message);
  final String? message;

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
              Padding(
                padding: const EdgeInsets.only(top: 1),
                child: Icon(
                  Icons.error_outline,
                  color: Theme.of(context).colorScheme.error,
                  size: 16,
                ),
              ),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  message!,
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.error,
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