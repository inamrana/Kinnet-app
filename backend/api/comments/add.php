<?php
/**
 * POST (multipart) postId, text, image (file), parentId, replyToName, clientId → { comment }.
 * A reply to a reply is attached to the top-level comment, so threads stay one level deep.
 */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
once(param('clientId'), $me, function () use ($me) {
    $postId = resolve_id(param('postId'), 'posts') ?? fail('Post not found', 404);
    $post = q('SELECT * FROM posts WHERE id = ?', [$postId])->fetch() ?: fail('Post not found', 404);
    $parentId = resolve_id(param('parentId'), 'comments');
    $parent = $parentId ? q('SELECT * FROM comments WHERE id = ?', [$parentId])->fetch() : null;
    if ($parent && $parent['parent_id'] !== null) $parentId = (int)$parent['parent_id'];

    $image = save_upload('image', 'comments', ['image/'], 15_000_000);
    $text = param('text');
    if ($text === '' && $image === '') fail('Write a comment or add a photo');

    q('INSERT INTO comments (client_id, post_id, author_id, parent_id, reply_to_name, text, image) VALUES (?, ?, ?, ?, ?, ?, ?)',
        [param('clientId') ?: null, $postId, $me, $parentId, param('replyToName'), $text, $image]);
    $id = (int)db()->lastInsertId();

    $preview = $text !== '' ? mb_substr($text, 0, 120) : 'a photo';
    if ($parent) notify((int)$parent['author_id'], $me, 'reply', (string)$postId, $preview);
    if (!$parent || (int)$parent['author_id'] !== (int)$post['author_id']) notify((int)$post['author_id'], $me, 'comment', (string)$postId, $preview);

    return ['comment' => comment_json(q('SELECT * FROM comments WHERE id = ?', [$id])->fetch())];
});
