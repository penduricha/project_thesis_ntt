import 'reaction_admin.dart';

class ChatMessage {
  final String id;
  final String text;
  final bool isMe;
  final DateTime timestamp;
  List<ReactionAdmin> reactions;
  final ChatMessage? replyTo;

  ChatMessage({
    required this.id,
    required this.text,
    required this.isMe,
    required this.timestamp,
    List<ReactionAdmin>? reactions,
    this.replyTo,
  }) : reactions = reactions ?? [];
}