<?php
/**
 * POST (multipart) to, type=text|image|video|file|post|system, text, file, postId, replyTo, clientId → { message }.
 * Photos, videos and files are saved to uploads/chat and served by URL.
 */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
once(param('clientId'), $me, function () use ($me) {
    $to = (int)param('to');
    if ($to === $me || !find_user($to)) fail('User not found', 404);
    $type = param('type', 'text');
    $text = param('text');
    $media = '';
    $fileName = '';
    $postId = null;

    switch ($type) {
        case 'text':
        case 'system':
            if ($text === '') fail('Empty message');
            break;
        case 'image':
            $media = save_upload('file', 'chat', ['image/'], 15_000_000);
            break;
        case 'video':
            $media = save_upload('file', 'chat', ['video/'], 40_000_000);
            break;
        case 'file':
            $media = save_upload('file', 'chat', ['application/', 'text/', 'image/', 'video/'], 40_000_000);
            $fileName = mb_substr(basename(param('fileName', $_FILES['file']['name'] ?? 'file')), 0, 200);
            break;
        case 'post':
            $postId = resolve_id(param('postId'), 'posts') ?? fail('Post not found', 404);
            q('UPDATE posts SET share_count = share_count + 1 WHERE id = ?', [$postId]);
            break;
        default:
            fail('Unknown message type');
    }
    if (in_array($type, ['image', 'video', 'file'], true) && $media === '') fail('Attach a file');

    $replyId = resolve_id(param('replyTo'), 'messages');
    q('INSERT INTO messages (client_id, sender_id, receiver_id, type, text, media_path, file_name, post_id, reply_to_id, reply_to_text)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
        [param('clientId') ?: null, $me, $to, $type, $text, $media, $fileName, $postId, $replyId, mb_substr(param('replyToText'), 0, 255)]);
    $message = q('SELECT * FROM messages WHERE id = ?', [(int)db()->lastInsertId()])->fetch();

    // Push to the other phone; a screenshot system line is sent as an alert
    $sender = find_user($me);
    $isScreenshot = $type === 'system' && str_contains($text, 'took a screenshot');
    push_to($to, [
        'type' => 'message',
        'kind' => $isScreenshot ? 'screenshot' : 'message',
        'fromUid' => (string)$me,
        'title' => $isScreenshot ? 'Screenshot alert' : $sender['full_name'],
        'body' => $isScreenshot ? "{$sender['full_name']} took a screenshot of your chat" : message_preview($message),
    ]);
    return ['message' => message_json($message)];
});
