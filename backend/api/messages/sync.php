<?php
/**
 * GET with=<uid>, since=<serverTime from the last call, ms> → { messages, other, serverTime }.
 * Returns every message in the conversation created or changed (edited, deleted, hearted, read)
 * since the last sync — the Chat screen polls this so new messages appear without leaving it.
 * since=0 returns the latest 200.
 */
require __DIR__ . '/../../lib.php';

$me = auth_user_id();
$other = (int)param('with');
$since = (int)param('since', '0');
$serverTime = ms(q('SELECT UTC_TIMESTAMP(3)')->fetchColumn());

if ($since > 0) {
    $rows = q('SELECT * FROM messages
               WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?))
                 AND updated_at >= FROM_UNIXTIME(? / 1000)
               ORDER BY id', [$me, $other, $other, $me, $since])->fetchAll();
} else {
    $rows = array_reverse(q('SELECT * FROM messages
               WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)
               ORDER BY id DESC LIMIT 200', [$me, $other, $other, $me])->fetchAll());
}
$otherUser = find_user($other);
respond([
    'messages' => array_map('message_json', $rows),
    'other' => $otherUser ? user_json($otherUser) : null,
    'serverTime' => $serverTime,
]);
