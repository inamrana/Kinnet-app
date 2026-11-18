<?php
/**
 * POST id, clientId → { message }. Same 5-minute rule as editing. The row is kept (marked deleted,
 * text and file removed) so the other phone learns about the deletion on its next sync.
 */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
once(param('clientId'), $me, function () use ($me) {
    $id = resolve_id(param('id'), 'messages') ?? fail('Message not found', 404);
    $message = q('SELECT *, TIMESTAMPDIFF(SECOND, created_at, UTC_TIMESTAMP()) AS age FROM messages WHERE id = ?', [$id])->fetch()
        ?: fail('Message not found', 404);
    if ((int)$message['sender_id'] !== $me) fail('You can only delete your own messages', 403);
    if ((int)$message['age'] >= EDIT_WINDOW_SECONDS) fail('Messages can only be deleted within 5 minutes', 403);
    delete_upload($message['media_path']);
    q("UPDATE messages SET deleted = 1, text = '', media_path = '' WHERE id = ?", [$id]);
    return ['message' => message_json(q('SELECT * FROM messages WHERE id = ?', [$id])->fetch())];
});
