<?php
/**
 * POST (multipart) media (photo or video file), thumb (small JPEG, for videos), label, labelStyle,
 * labelX, labelY, audience=friends|close, clientId → { story }.
 * Metadata goes to MySQL, the files to uploads/stories.
 */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
once(param('clientId'), $me, function () use ($me) {
    $media = save_upload('media', 'stories', ['image/', 'video/'], 40_000_000);
    if ($media === '') fail('Add a photo or video');
    $isVideo = (new finfo(FILEINFO_MIME_TYPE))->file(__DIR__ . "/../../$media");
    $isVideo = str_starts_with((string)$isVideo, 'video/') ? 1 : 0;
    $thumb = save_upload('thumb', 'stories', ['image/'], 5_000_000);
    $audience = param('audience') === 'close' ? 'close' : 'friends';
    $style = param('labelStyle') === 'white' ? 'white' : 'red';

    q('INSERT INTO stories (client_id, owner_id, media_path, thumb_path, is_video, label, label_style, label_x, label_y, audience)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
        [param('clientId') ?: null, $me, $media, $thumb, $isVideo, mb_substr(param('label'), 0, 120), $style,
         (float)param('labelX', '0.1'), (float)param('labelY', '0.55'), $audience]);
    $id = (int)db()->lastInsertId();
    return ['story' => story_json(q('SELECT * FROM stories WHERE id = ?', [$id])->fetch(), $me)];
});
