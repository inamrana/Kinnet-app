<?php
/** POST email, password → { token, user }. */
require __DIR__ . '/../../lib.php';
require_method('POST');

$email = strtolower(param('email'));
$password = (string)(input()['password'] ?? '');
$user = q('SELECT * FROM users WHERE email = ?', [$email])->fetch();

// Same message for a wrong email or password, so accounts can't be guessed
if (!$user || !password_verify($password, $user['password_hash'])) fail('Incorrect email or password', 401);

q('UPDATE users SET is_online = 1, last_seen = UTC_TIMESTAMP() WHERE id = ?', [$user['id']]);
respond(['token' => new_token((int)$user['id']), 'user' => user_json(find_user((int)$user['id']))]);
