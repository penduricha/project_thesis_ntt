import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../../../core/localization/app_strings.dart';

/// Text-only composer. Sending and draft ownership remain with the screen.
class ChatComposer extends StatelessWidget {
  const ChatComposer({
    super.key,
    required this.controller,
    required this.enabled,
    required this.onSend,
    required this.maxLength,
  });

  final TextEditingController controller;
  final bool enabled;
  final VoidCallback onSend;
  final int maxLength;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: colors.surface,
        border: Border(top: BorderSide(color: Theme.of(context).dividerColor)),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(
                child: Container(
                  constraints: const BoxConstraints(minHeight: 48),
                  decoration: BoxDecoration(
                    color: colors.surfaceContainerLow,
                    border: Border.all(color: colors.outlineVariant),
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: TextField(
                    controller: controller,
                    enabled: enabled,
                    minLines: 1,
                    maxLines: 5,
                    keyboardType: TextInputType.multiline,
                    textInputAction: TextInputAction.newline,
                    textCapitalization: TextCapitalization.sentences,
                    inputFormatters: [LengthLimitingTextInputFormatter(maxLength)],
                    style: TextStyle(fontSize: 16, color: colors.onSurface),
                    decoration: InputDecoration(
                      hintText: AppStrings.of(context).text('messageHint'),
                      hintStyle: TextStyle(color: colors.onSurfaceVariant),
                      filled: false,
                      isDense: true,
                      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
                      // Override every themed border: otherwise the app theme draws
                      // a second rectangle inside the rounded composer on focus.
                      border: InputBorder.none,
                      enabledBorder: InputBorder.none,
                      focusedBorder: InputBorder.none,
                      disabledBorder: InputBorder.none,
                      errorBorder: InputBorder.none,
                      focusedErrorBorder: InputBorder.none,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              ValueListenableBuilder<TextEditingValue>(
                valueListenable: controller,
                builder: (context, value, _) {
                  final canSend = enabled && value.text.trim().isNotEmpty;
                  return SizedBox(
                    width: 48,
                    height: 48,
                    child: IconButton.filled(
                      tooltip: AppStrings.of(context).text('send'),
                      onPressed: canSend ? onSend : null,
                      style: IconButton.styleFrom(
                        backgroundColor: colors.primary,
                        foregroundColor: colors.onPrimary,
                        disabledBackgroundColor: colors.onSurface.withValues(alpha: 0.08),
                        disabledForegroundColor: colors.onSurface.withValues(alpha: 0.38),
                      ),
                      icon: const Icon(Icons.send_rounded, size: 22),
                    ),
                  );
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}
