<?php
/** POST token → stores this device's FCM token so the server can push to it. */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
$token = param('token');
if ($token === '') fail('Missing token');
q('INSERT INTO fcm_tokens (user_id, token) VALUES (?, ?) ON DUPLICATE KEY UPDATE token = VALUES(token)', [$me, $token]);
respond([]);
