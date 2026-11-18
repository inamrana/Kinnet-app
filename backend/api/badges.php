<?php
/** GET → { requests, unreadNotices, unreadChats } for the red badges on the bell and Chats buttons. */
require __DIR__ . '/../lib.php';

$me = auth_user_id();
respond([
    'requests' => (int)q('SELECT COUNT(*) FROM friend_requests WHERE to_id = ?', [$me])->fetchColumn(),
    'unreadNotices' => (int)q("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0 AND type <> 'friend_request'", [$me])->fetchColumn(),
    'unreadChats' => (int)q('SELECT COUNT(DISTINCT sender_id) FROM messages WHERE receiver_id = ? AND is_read = 0 AND deleted = 0', [$me])->fetchColumn(),
]);
