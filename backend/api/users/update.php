<?php
/**
 * POST (multipart) any of: name, username, website, bio, avatar (file), cover (file), setup=1.
 * Only the fields sent are changed. Usernames are unique; photos are saved to uploads/.
 */
require __DIR__ . '/../../lib.php';
require_method('POST');

$uid = auth_user_id();
$user = find_user($uid);
$in = input();
$set = [];

if (array_key_exists('name', $in)) {
    $name = param('name');
    if ($name === '') fail('Enter your name');
    $parts = preg_split('/\s+/', $name, 2);
    $set += ['full_name' => $name, 'first_name' => $parts[0], 'last_name' => $parts[1] ?? ''];
}
if (array_key_exists('username', $in)) {
    $username = strtolower(param('username'));
    if (!preg_match('/^[a-z0-9._]{3,30}$/', $username)) fail('3–30 letters, numbers, . or _');
    $taken = q('SELECT 1 FROM users WHERE username = ? AND id <> ?', [$username, $uid])->fetch();
    if ($taken) fail('That username is taken', 409);
    $set['username'] = $username;
}
foreach (['website' => 200, 'bio' => 200] as $field => $max) {
    if (array_key_exists($field, $in)) $set[$field] = mb_substr(param($field), 0, $max);
}
if (param('setup') === '1' && trim($set['bio'] ?? $user['bio']) === '') fail('Add a short bio');

foreach (['avatar' => ['profile_image', 'avatars'], 'cover' => ['cover_image', 'covers']] as $field => [$column, $folder]) {
    $path = save_upload($field, $folder, ['image/'], 10_000_000);
    if ($path !== '') {
        delete_upload($user[$column]);
        $set[$column] = $path;
    }
}

$username = $set['username'] ?? $user['username'];
$bio = $set['bio'] ?? $user['bio'];
if ($username && trim($bio) !== '') $set['profile_complete'] = 1;

if ($set) {
    $columns = implode(', ', array_map(fn($c) => "$c = ?", array_keys($set)));
    q("UPDATE users SET $columns WHERE id = ?", [...array_values($set), $uid]);
}
respond(['user' => user_json(find_user($uid))]);
