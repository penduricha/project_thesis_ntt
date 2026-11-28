import 'package:flutter/material.dart';

/// A tappable conversation row with an inset bottom divider even for the final item.
class ChatListItem extends StatelessWidget {
  const ChatListItem({super.key, required this.onTap, required this.child});

  final VoidCallback onTap;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Column(
        children: [
          Container(
            constraints: const BoxConstraints(minHeight: 80),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: child,
          ),
          Divider(
            height: 1,
            thickness: 1,
            indent: 76,
            color: Theme.of(context).dividerColor,
          ),
        ],
      ),
    );
  }
}
