import 'package:flutter/material.dart';

import '../../../../core/constants/app_colors.dart';

class CreateAccountScreen extends StatefulWidget {
  const CreateAccountScreen({super.key});

  @override
  State<CreateAccountScreen> createState() => _CreateAccountScreenState();
}

class _CreateAccountScreenState extends State<CreateAccountScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _emailController = TextEditingController();
  final _dateController = TextEditingController();
  final _passwordController = TextEditingController();

  String? _gender = 'Male';
  DateTime? _selectedDate;
  bool _obscurePassword = true;

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _dateController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  // Hàm hiển thị bảng chọn lịch Flutter
  Future<void> _selectDate(BuildContext context) async {
    final DateTime? picked = await showDatePicker(
      context: context,
      initialDate: DateTime(2003, 1, 1),
      firstDate: DateTime(1900),
      lastDate: DateTime.now(),
      builder: (context, child) {
        return Theme(
          data: Theme.of(context).copyWith(
            colorScheme: const ColorScheme.light(
              primary: AppColors.primaryBlue,
              onPrimary: Colors.white,
              onSurface: Colors.black,
            ),
          ),
          child: child!,
        );
      },
    );
    if (picked != null && picked != _selectedDate) {
      setState(() {
        _selectedDate = picked;
        _dateController.text = "${picked.day}/${picked.month}/${picked.year}";
      });
    }
  }

  void _onCreateAccountPressed() {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Administrator Registration'),
        content: const Text(
          'Admin accounts are provisioned by the system administrator. '
          'Please contact your faculty or IT administrator to activate your Admin ID.',
        ),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.of(dialogContext).pop();
              Navigator.of(context).pop();
            },
            child: const Text('Back to Login'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 24.0),
            child: Form(
              key: _formKey,
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const SizedBox(height: 20),

                  const SizedBox(
                    width: double.infinity,
                    child: Text(
                      'Create account',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 24,
                        color: AppColors.primaryBlue,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),

                  const SizedBox(height: 30),

                  // 1. Trường Họ và tên
                  const Text(
                    'Full name',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w500,
                      color: Colors.black87,
                    ),
                  ),
                  const SizedBox(height: 8),
                  TextFormField(
                    controller: _nameController,
                    validator: (v) =>
                        (v == null || v.trim().isEmpty) ? 'Enter your full name' : null,
                    decoration: InputDecoration(
                      hintText: 'Enter your full name',
                      hintStyle: const TextStyle(
                        color: Colors.grey,
                        fontSize: 14,
                      ),
                      prefixIcon: const Icon(
                        Icons.person_outline,
                        color: Colors.grey,
                        size: 20,
                      ),
                      filled: true,
                      fillColor: AppColors.fieldFill,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: BorderSide.none,
                      ),
                    ),
                  ),

                  const SizedBox(height: 16),

                  // Trường Email Address
                  const Text(
                    'Email address',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w500,
                      color: Colors.black87,
                    ),
                  ),
                  const SizedBox(height: 8),
                  TextFormField(
                    controller: _emailController,
                    keyboardType: TextInputType.emailAddress,
                    validator: (v) {
                      final trimmed = v?.trim() ?? '';
                      if (trimmed.isEmpty || !trimmed.contains('@')) {
                        return 'Enter a valid email address';
                      }
                      return null;
                    },
                    decoration: InputDecoration(
                      hintText: 'Enter email',
                      hintStyle: const TextStyle(
                        color: Colors.grey,
                        fontSize: 14,
                      ),
                      prefixIcon: const Icon(
                        Icons.email_outlined,
                        color: Colors.grey,
                        size: 20,
                      ),
                      filled: true,
                      fillColor: AppColors.fieldFill,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: BorderSide.none,
                      ),
                    ),
                  ),

                  const SizedBox(height: 16),

                  // 2. Trường Giới tính (Radio Male / Female)
                  const Text(
                    'Gender',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w500,
                      color: Colors.black87,
                    ),
                  ),

                  const SizedBox(height: 4),

                  RadioGroup<String>(
                    groupValue: _gender,
                    onChanged: (String? value) {
                      setState(() {
                        _gender = value;
                      });
                    },
                    child: const Row(
                      children: [
                        Expanded(
                          child: RadioListTile<String>(
                            title: Text(
                              'Male',
                              style: TextStyle(fontSize: 14),
                            ),
                            value: 'Male',
                            activeColor: AppColors.accentBlue,
                            contentPadding: EdgeInsets.zero,
                          ),
                        ),
                        Expanded(
                          child: RadioListTile<String>(
                            title: Text(
                              'Female',
                              style: TextStyle(fontSize: 14),
                            ),
                            value: 'Female',
                            activeColor: AppColors.primaryBlue,
                            contentPadding: EdgeInsets.zero,
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 8),

                  // 3. Trường Ngày sinh (Lịch Flutter)
                  const Text(
                    'Date of birth',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w500,
                      color: Colors.black87,
                    ),
                  ),
                  const SizedBox(height: 8),
                  TextFormField(
                    controller: _dateController,
                    readOnly: true,
                    onTap: () => _selectDate(context),
                    validator: (v) =>
                        (v == null || v.isEmpty) ? 'Select date of birth' : null,
                    decoration: InputDecoration(
                      hintText: 'Select date of birth',
                      hintStyle: const TextStyle(
                        color: Colors.grey,
                        fontSize: 14,
                      ),
                      prefixIcon: const Icon(
                        Icons.calendar_today_outlined,
                        color: Colors.grey,
                        size: 20,
                      ),
                      filled: true,
                      fillColor: AppColors.fieldFill,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: BorderSide.none,
                      ),
                    ),
                  ),

                  const SizedBox(height: 16),

                  // Trường Password
                  const Text(
                    'Password',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w500,
                      color: Colors.black87,
                    ),
                  ),
                  const SizedBox(height: 8),
                  TextFormField(
                    controller: _passwordController,
                    obscureText: _obscurePassword,
                    validator: (v) =>
                        (v == null || v.length < 6) ? 'Password must be at least 6 characters' : null,
                    decoration: InputDecoration(
                      hintText: 'Enter password',
                      hintStyle: const TextStyle(
                        color: Colors.grey,
                        fontSize: 14,
                      ),
                      prefixIcon: const Icon(
                        Icons.lock_outline,
                        color: Colors.grey,
                        size: 20,
                      ),
                      suffixIcon: IconButton(
                        icon: Icon(
                          _obscurePassword
                              ? Icons.visibility_off_outlined
                              : Icons.visibility_outlined,
                          color: Colors.grey,
                          size: 20,
                        ),
                        onPressed: () {
                          setState(() {
                            _obscurePassword = !_obscurePassword;
                          });
                        },
                      ),
                      filled: true,
                      fillColor: AppColors.fieldFill,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: BorderSide.none,
                      ),
                    ),
                  ),

                  const SizedBox(height: 30),

                  // Nút Sign Up / Create Account chính
                  SizedBox(
                    width: double.infinity,
                    height: 50,
                    child: ElevatedButton(
                      onPressed: _onCreateAccountPressed,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.primaryBlue,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                        elevation: 0,
                      ),
                      child: const Text(
                        'Create account',
                        style: TextStyle(
                          fontSize: 16,
                          color: Colors.white,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ),

                  const SizedBox(height: 15),

                  // Nút quay lại trang Login
                  Center(
                    child: TextButton(
                      onPressed: () => Navigator.pop(context),
                      child: const Text(
                        'Already have an account? Login',
                        style: TextStyle(
                          color: AppColors.primaryBlue,
                          fontSize: 14,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 20),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
