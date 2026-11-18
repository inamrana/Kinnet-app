<?php
/**
 * POST (multipart) text, bgColor, image0 … image9, clientId → { post }.
 * Photos are saved to uploads/posts; the post and its photo rows are written in one transaction.
 */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
once(param('clientId'), $me, function () use ($me) {
    $paths = [];
    for ($i = 0; $i < 10; $i++) {
        $path = save_upload("image$i", 'posts', ['image/'], 15_000_000);
        if ($path !== '') $paths[] = $path;
    }
    $text = param('text');
    if ($text === '' && !$paths) fail('Write something or add a photo');

    db()->beginTransaction();
    q('INSERT INTO posts (client_id, author_id, text, bg_color) VALUES (?, ?, ?, ?)',
        [param('clientId') ?: null, $me, $text, $paths ? '' : param('bgColor')]);
    $id = (int)db()->lastInsertId();
    foreach ($paths as $i => $path) q('INSERT INTO post_images (post_id, idx, path) VALUES (?, ?, ?)', [$id, $i, $path]);
    db()->commit();

    return ['post' => post_json(q('SELECT * FROM posts WHERE id = ?', [$id])->fetch())];
});
