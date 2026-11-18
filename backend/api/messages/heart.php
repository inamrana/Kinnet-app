<?php
/** POST id, on=1|0, clientId → { message }. The heart reaction on a message (screen 21). */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
once(param('clientId'), $me, function () use ($me) {
    $id = resolve_id(param('id'), 'messages') ?? fail('Message not found', 404);
    $message = q('SELECT * FROM messages WHERE id = ?', [$id])->fetch() ?: fail('Message not found', 404);
    if (!in_array($me, [(int)$message['sender_id'], (int)$message['receiver_id']], true)) fail('Not your conversation', 403);
    if (param('on', '1') === '1') {
        q('INSERT IGNORE INTO message_hearts (message_id, user_id) VALUES (?, ?)', [$id, $me]);
    } else {
        q('DELETE FROM message_hearts WHERE message_id = ? AND user_id = ?', [$id, $me]);
    }
    // Hearts live in their own table, so bump the message so the other phone's sync picks it up
    q('UPDATE messages SET updated_at = UTC_TIMESTAMP(3) WHERE id = ?', [$id]);
    return ['message' => message_json(q('SELECT * FROM messages WHERE id = ?', [$id])->fetch())];
});
