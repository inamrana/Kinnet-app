<?php
/** POST postId, clientId → counts one share (the post itself is sent with messages/send.php type=post). */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
once(param('clientId'), $me, function () {
    $postId = resolve_id(param('postId'), 'posts') ?? fail('Post not found', 404);
    q('UPDATE posts SET share_count = share_count + 1 WHERE id = ?', [$postId]);
    return ['post' => post_json(q('SELECT * FROM posts WHERE id = ?', [$postId])->fetch())];
});
