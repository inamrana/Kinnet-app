<?php
/**
 * POST postId, type (like|love|haha|wow|sad|angry, or empty to remove), clientId → { post }.
 * One reaction per user per post: the primary key (post_id, user_id) makes a new pick replace the old one.
 */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
once(param('clientId'), $me, function () use ($me) {
    $postId = resolve_id(param('postId'), 'posts') ?? fail('Post not found', 404);
    $post = q('SELECT * FROM posts WHERE id = ?', [$postId])->fetch() ?: fail('Post not found', 404);
    $type = param('type');
    if ($type === '') {
        q('DELETE FROM reactions WHERE post_id = ? AND user_id = ?', [$postId, $me]);
    } else {
        if (!in_array($type, ['like', 'love', 'haha', 'wow', 'sad', 'angry'], true)) fail('Unknown reaction');
        $had = q('SELECT 1 FROM reactions WHERE post_id = ? AND user_id = ?', [$postId, $me])->fetch();
        q('INSERT INTO reactions (post_id, user_id, type) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE type = VALUES(type)', [$postId, $me, $type]);
        if (!$had) notify((int)$post['author_id'], $me, 'reaction', (string)$postId, $type);
    }
    return ['post' => post_json($post)];
});
