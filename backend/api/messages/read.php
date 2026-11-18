<?php
/** POST with=<uid> → marks that person's messages to me as read (clears the unread dot in Chats). */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
q('UPDATE messages SET is_read = 1 WHERE sender_id = ? AND receiver_id = ? AND is_read = 0', [(int)param('with'), $me]);
respond([]);
