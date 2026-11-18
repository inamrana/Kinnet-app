<?php
/**
 * POST id, text, clientId → { message }. Only the sender, and only within 5 minutes of sending —
 * the server checks the time itself, so an app with a wrong clock (or a late offline sync) can't get round it.
 */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
once(param('clientId'), $me, function () use ($me) {
    $id = resolve_id(param('id'), 'messages') ?? fail('Message not found', 404);
    $message = q('SELECT *, TIMESTAMPDIFF(SECOND, created_at, UTC_TIMESTAMP()) AS age FROM messages WHERE id = ?', [$id])->fetch()
        ?: fail('Message not found', 404);
    if ((int)$message['sender_id'] !== $me) fail('You can only edit your own messages', 403);
    if ($message['deleted']) fail('This message was deleted', 409);
    if ((int)$message['age'] >= EDIT_WINDOW_SECONDS) fail('Messages can only be edited within 5 minutes', 403);
    $text = param('text');
    if ($text === '') fail('Empty message');
    q('UPDATE messages SET text = ?, edited = 1 WHERE id = ?', [$text, $id]);
    return ['message' => message_json(q('SELECT * FROM messages WHERE id = ?', [$id])->fetch())];
});
