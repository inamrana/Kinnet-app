<?php
/** GET → { posts } — my posts and my friends' posts, newest first (the Home feed). */
require __DIR__ . '/../../lib.php';

$me = auth_user_id();
$rows = q('SELECT p.* FROM posts p
           WHERE p.author_id = ? OR p.author_id IN (SELECT friend_id FROM friendships WHERE user_id = ?)
           ORDER BY p.created_at DESC, p.id DESC LIMIT 60', [$me, $me])->fetchAll();
respond(['posts' => array_map('post_json', $rows), 'serverTime' => now_ms()]);
