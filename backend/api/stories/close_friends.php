<?php
/**
 * GET → { closeFriends: [uid] }.
 * POST friendId, on=1|0 → adds or removes one friend from my close-friends list.
 */
require __DIR__ . '/../../lib.php';

$me = auth_user_id();
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $friend = (int)param('friendId');
    if (param('on', '1') === '1') {
        if (friend_state($me, $friend) !== 'friends') fail('Only friends can be close friends');
        q('INSERT IGNORE INTO close_friends (user_id, friend_id) VALUES (?, ?)', [$me, $friend]);
    } else {
        q('DELETE FROM close_friends WHERE user_id = ? AND friend_id = ?', [$me, $friend]);
    }
}
$ids = q('SELECT friend_id FROM close_friends WHERE user_id = ?', [$me])->fetchAll(PDO::FETCH_COLUMN);
respond(['closeFriends' => array_map('strval', $ids)]);
