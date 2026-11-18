<?php
/** POST storyId, reaction (optional: like|love|haha) → records that I viewed (and maybe reacted to) a story. */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
$id = resolve_id(param('storyId'), 'stories') ?? fail('Story not found', 404);
$story = q('SELECT * FROM stories WHERE id = ?', [$id])->fetch() ?: fail('Story not found', 404);
if ((int)$story['owner_id'] !== $me) {
    $reaction = param('reaction');
    if (!in_array($reaction, ['', 'like', 'love', 'haha'], true)) fail('Unknown reaction');
    q('INSERT INTO story_views (story_id, user_id, reaction) VALUES (?, ?, ?)
       ON DUPLICATE KEY UPDATE reaction = IF(VALUES(reaction) = \'\', reaction, VALUES(reaction))', [$id, $me, $reaction]);
}
respond([]);
