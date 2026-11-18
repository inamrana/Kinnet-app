<?php
/** GET postId → { comments } oldest first; replies carry the id of their top-level comment in parentId. */
require __DIR__ . '/../../lib.php';

auth_user_id();
$postId = resolve_id(param('postId'), 'posts') ?? fail('Post not found', 404);
$rows = q('SELECT * FROM comments WHERE post_id = ? ORDER BY created_at, id', [$postId])->fetchAll();
respond(['comments' => array_map('comment_json', $rows)]);
