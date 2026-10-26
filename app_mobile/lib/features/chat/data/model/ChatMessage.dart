import 'ReactionUser.dart';

class ChatMessage {
  final String id;
  final String text;
  final bool isMe;
  final DateTime timestamp;
  List<ReactionUser> reactions;
  final ChatMessage? replyTo;

  ChatMessage({
    required this.id,
    required this.text,
    required this.isMe,
    required this.timestamp,
    List<ReactionUser>? reactions,
    this.replyTo,
  }) : reactions = reactions ?? [];
}