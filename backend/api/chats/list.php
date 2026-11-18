<?php
/**
 * GET → { chats: [{ otherUid, user, lastMessage, lastTime, unread }] } — one row per conversation,
 * newest first, with the last message ("You: …" when I sent it) and my unread count.
 */
require __DIR__ . '/../../lib.php';

$me = auth_user_id();
$rows = q('SELECT m.* FROM messages m
           JOIN (SELECT LEAST(sender_id, receiver_id) AS a, GREATEST(sender_id, receiver_id) AS b, MAX(id) AS last_id
                 FROM messages WHERE sender_id = ? OR receiver_id = ? GROUP BY a, b) t ON t.last_id = m.id
           ORDER BY m.id DESC', [$me, $me])->fetchAll();

$chats = [];
foreach ($rows as $m) {
    $other = (int)$m['sender_id'] === $me ? (int)$m['receiver_id'] : (int)$m['sender_id'];
    $user = find_user($other);
    if (!$user) continue;
    $unread = (int)q('SELECT COUNT(*) FROM messages WHERE sender_id = ? AND receiver_id = ? AND is_read = 0 AND deleted = 0', [$other, $me])->fetchColumn();
    $preview = message_preview($m);
    $chats[] = [
        'otherUid' => (string)$other,
        'user' => user_json($user),
        'lastMessage' => (int)$m['sender_id'] === $me && $m['type'] !== 'system' ? "You: $preview" : $preview,
        'lastTime' => ms($m['created_at']),
        'unread' => $unread,
    ];
}
respond(['chats' => $chats]);
