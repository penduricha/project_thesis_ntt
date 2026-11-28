import 'package:flutter/material.dart';

/// Web-inspired QBank chrome shared by the Authenticator and chat tabs.
class QBankSectionHeader extends StatelessWidget {
  const QBankSectionHeader({super.key, required this.title, required this.onMenu,
    required this.actionIcon, required this.actionLabel, required this.onAction});

  final String title;
  final VoidCallback onMenu;
  final IconData actionIcon;
  final String actionLabel;
  final VoidCallback onAction;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final fill = dark ? const Color(0xff175f9f) : const Color(0xff004b87);
    final onFill = dark ? const Color(0xfff2f7fc) : Colors.white;
    return Material(
      color: fill,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        child: Row(children: [
          IconButton(onPressed: onMenu, tooltip: 'Menu', icon: Icon(Icons.menu_rounded, color: onFill)),
          Container(
            width: 34, height: 34,
            decoration: BoxDecoration(color: onFill, borderRadius: BorderRadius.circular(8)),
            alignment: Alignment.center,
            child: Text('QB', style: TextStyle(color: fill, fontWeight: FontWeight.w800, fontSize: 13)),
          ),
          const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('QBank', style: TextStyle(color: onFill.withValues(alpha: 0.78), fontSize: 11, fontWeight: FontWeight.w600)),
            Text(title, maxLines: 1, overflow: TextOverflow.ellipsis,
              style: TextStyle(color: onFill, fontSize: 18, fontWeight: FontWeight.w700)),
          ])),
          IconButton(onPressed: onAction, tooltip: actionLabel, icon: Icon(actionIcon, color: onFill)),
        ]),
      ),
    );
  }
}
