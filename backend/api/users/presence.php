<?php
/** POST state=online|away. The app sends "online" every 30 s while open and "away" when it goes to the background. */
require __DIR__ . '/../../lib.php';
require_method('POST');

$uid = auth_user_id();
$online = param('state', 'online') === 'online' ? 1 : 0;
q('UPDATE users SET is_online = ?, last_seen = UTC_TIMESTAMP() WHERE id = ?', [$online, $uid]);
respond(['online' => (bool)$online]);
