<?php
/** POST → marks all my notifications as read.  POST id, remove=1 → deletes one. */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
if (param('remove') === '1') {
    q('DELETE FROM notifications WHERE id = ? AND user_id = ?', [(int)param('id'), $me]);
} else {
    q('UPDATE notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0', [$me]);
}
respond([]);
