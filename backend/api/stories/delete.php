<?php
/** POST storyId, clientId → deletes one of my stories and its files. */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
once(param('clientId'), $me, function () use ($me) {
    $id = resolve_id(param('storyId'), 'stories');
    $story = $id ? q('SELECT * FROM stories WHERE id = ? AND owner_id = ?', [$id, $me])->fetch() : false;
    if ($story) {
        delete_upload($story['media_path']);
        delete_upload($story['thumb_path']);
        q('DELETE FROM stories WHERE id = ?', [$id]);
    }
    return [];
});
