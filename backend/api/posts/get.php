<?php
/** GET id → { post }.  GET author=<uid> → { posts } (that person's posts, newest first). */
require __DIR__ . '/../../lib.php';

auth_user_id();
if (param('author') !== '') {
    $rows = q('SELECT * FROM posts WHERE author_id = ? ORDER BY created_at DESC LIMIT 60', [(int)param('author')])->fetchAll();
    respond(['posts' => array_map('post_json', $rows)]);
}
$id = resolve_id(param('id'), 'posts');
$post = $id ? q('SELECT * FROM posts WHERE id = ?', [$id])->fetch() : false;
if (!$post) fail('This post is no longer available', 404);
respond(['post' => post_json($post)]);
