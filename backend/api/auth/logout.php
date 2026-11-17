<?php
/** POST (token in header) → invalidates the token, marks the user offline, forgets the push token. */
require __DIR__ . '/../../lib.php';
require_method('POST');

$uid = auth_user_id();
q('UPDATE sessions SET revoked = 1 WHERE token = ?', [bearer_token()]);
q('UPDATE users SET is_online = 0, last_seen = UTC_TIMESTAMP() WHERE id = ?', [$uid]);
q('DELETE FROM fcm_tokens WHERE user_id = ?', [$uid]);
respond([]);
