<?php
/** POST email, password, firstName, lastName, gender, birthday → { token, user }. */
require __DIR__ . '/../../lib.php';
require_method('POST');

$email = strtolower(param('email'));
$password = (string)(input()['password'] ?? '');
$first = param('firstName');
$last = param('lastName');

if (!filter_var($email, FILTER_VALIDATE_EMAIL)) fail('Enter a valid email');
if (strlen($password) < 6) fail('Password must be at least 6 characters');
if ($first === '' || $last === '') fail('Enter your first and last name');
if (q('SELECT 1 FROM users WHERE email = ?', [$email])->fetch()) fail('An account with this email already exists', 409);

// The password itself is never stored, only its bcrypt hash
q('INSERT INTO users (email, password_hash, first_name, last_name, full_name, gender, birthday, is_online, last_seen)
   VALUES (?, ?, ?, ?, ?, ?, ?, 1, UTC_TIMESTAMP())',
    [$email, password_hash($password, PASSWORD_DEFAULT), $first, $last, "$first $last", param('gender'), param('birthday')]);
$uid = (int)db()->lastInsertId();

respond(['token' => new_token($uid), 'user' => user_json(find_user($uid))], 201);
