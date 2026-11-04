import 'package:flutter/material.dart';

// Enum quản lý các loại Reaction
enum MessageReaction { heart, like, haha, wow, sad, angry }

extension MessageReactionExtension on MessageReaction {
  String get value {
    switch (this) {
      case MessageReaction.heart:
        return '❤️';
      case MessageReaction.like:
        return '👍';
      case MessageReaction.haha:
        return '😆';
      case MessageReaction.wow:
        return '😮';
      case MessageReaction.sad:
        return '😭';
      case MessageReaction.angry:
        return '😡';
    }
  }
}



class ModalIconsReaction extends StatelessWidget {
  final Function(MessageReaction) onReactionSelected;
  const ModalIconsReaction({
    super.key,
    required this.onReactionSelected,
  });

  @override
  Widget build(BuildContext context) {

    return Container(

      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        boxShadow: const [
          BoxShadow(
            color: Colors.black26,
            blurRadius: 10,
            offset: Offset(0, 4),
          )
        ],
      ),

      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: MessageReaction.values.map((reaction) {
          return GestureDetector(
            onTap: () => onReactionSelected(reaction),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 6.0),
              child: Text(
                reaction.value,
                style: const TextStyle(fontSize: 26),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }
}