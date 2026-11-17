<?php
/** GET → { friends: [user], incoming: [{ user, time, mutual }], sent: [{ user, time }] }. */
require __DIR__ . '/../../lib.php';

$me = auth_user_id();
$myFriends = friend_ids($me);

$friends = q('SELECT u.* FROM friendships f JOIN users u ON u.id = f.friend_id WHERE f.user_id = ? ORDER BY u.full_name', [$me])->fetchAll();
$incoming = q('SELECT u.*, r.created_at AS req_at FROM friend_requests r JOIN users u ON u.id = r.from_id WHERE r.to_id = ? ORDER BY r.created_at DESC', [$me])->fetchAll();
$sent = q('SELECT u.*, r.created_at AS req_at FROM friend_requests r JOIN users u ON u.id = r.to_id WHERE r.from_id = ? ORDER BY r.created_at DESC', [$me])->fetchAll();

respond([
    'friends' => array_map('user_json', $friends),
    'incoming' => array_map(fn($u) => [
        'user' => user_json($u),
        'time' => ms($u['req_at']),
        'mutual' => count(array_intersect(friend_ids((int)$u['id']), $myFriends)),
    ], $incoming),
    'sent' => array_map(fn($u) => ['user' => user_json($u), 'time' => ms($u['req_at'])], $sent),
]);
