<?php
/** GET → { recent: [text] }.  POST text → saves it.  POST text, remove=1 → removes it. */
require __DIR__ . '/../../lib.php';

$me = auth_user_id();
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $text = mb_substr(param('text'), 0, 100);
    if ($text === '') fail('Nothing to save');
    if (param('remove') === '1') {
        q('DELETE FROM recent_searches WHERE user_id = ? AND text = ?', [$me, $text]);
    } else {
        q('INSERT INTO recent_searches (user_id, text) VALUES (?, ?) ON DUPLICATE KEY UPDATE created_at = UTC_TIMESTAMP()', [$me, $text]);
    }
}
$recent = q('SELECT text FROM recent_searches WHERE user_id = ? ORDER BY created_at DESC LIMIT 8', [$me])->fetchAll(PDO::FETCH_COLUMN);
respond(['recent' => $recent]);
