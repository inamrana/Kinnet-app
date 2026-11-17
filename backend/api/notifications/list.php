<?php
/**
 * GET → { notices: [{ id, type, fromUid, postId, text, read, timestamp }] } (newest first, last 60).
 * Friend requests are listed separately by friends/list.php, so they are left out here.
 */
require __DIR__ . '/../../lib.php';

$me = auth_user_id();
$rows = q("SELECT * FROM notifications WHERE user_id = ? AND type <> 'friend_request' ORDER BY created_at DESC LIMIT 60", [$me])->fetchAll();
$types = ['friend_accept' => 'friend_accepted'];
respond(['notices' => array_map(fn($n) => [
    'id' => (string)$n['id'],
    'type' => $types[$n['type']] ?? $n['type'],
    'fromUid' => (string)$n['from_id'],
    'postId' => $n['target_id'],
    'text' => $n['text'],
    'read' => (bool)$n['is_read'],
    'timestamp' => ms($n['created_at']),
], $rows)]);
