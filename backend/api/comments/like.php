<?php
/** POST commentId, like=1|0, clientId → { comment }. Explicit on/off so a retried request can't flip it back. */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
once(param('clientId'), $me, function () use ($me) {
    $id = resolve_id(param('commentId'), 'comments') ?? fail('Comment not found', 404);
    if (param('like', '1') === '1') {
        q('INSERT IGNORE INTO comment_likes (comment_id, user_id) VALUES (?, ?)', [$id, $me]);
    } else {
        q('DELETE FROM comment_likes WHERE comment_id = ? AND user_id = ?', [$id, $me]);
    }
    return ['comment' => comment_json(q('SELECT * FROM comments WHERE id = ?', [$id])->fetch())];
});
