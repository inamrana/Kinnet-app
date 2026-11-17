<?php
/** GET → { user } for the token's owner. Used to check a stored session is still valid. */
require __DIR__ . '/../../lib.php';

$uid = auth_user_id();
respond(['user' => user_json(find_user($uid))]);
