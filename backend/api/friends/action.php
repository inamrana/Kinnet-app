<?php
/**
 * POST action=request|cancel|confirm|delete|unfriend, userId, clientId (UUID) → { state }.
 * One endpoint for the whole Add friend → Requested → Friends flow. The clientId makes a
 * retried offline action safe: the second copy gets the first answer and changes nothing.
 */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
$other = (int)param('userId');
$action = param('action');
if ($other === $me || !find_user($other)) fail('User not found', 404);

once(param('clientId'), $me, function () use ($me, $other, $action) {
    db()->beginTransaction();
    switch ($action) {
        case 'request':
            $state = friend_state($me, $other);
            if ($state === 'incoming') {
                confirm($me, $other); // they already asked me: accepting is the natural outcome
            } elseif ($state === 'none') {
                q('INSERT IGNORE INTO friend_requests (from_id, to_id) VALUES (?, ?)', [$me, $other]);
                notify($other, $me, 'friend_request');
                $name = find_user($me)['full_name'];
                push_to($other, ['type' => 'friend', 'kind' => 'friend_request', 'fromUid' => (string)$me,
                                 'title' => 'Friend request', 'body' => "$name sent you a friend request"]);
            }
            break;
        case 'cancel':
            q('DELETE FROM friend_requests WHERE from_id = ? AND to_id = ?', [$me, $other]);
            break;
        case 'confirm':
            if (friend_state($me, $other) === 'incoming') confirm($me, $other);
            break;
        case 'delete':
            q('DELETE FROM friend_requests WHERE from_id = ? AND to_id = ?', [$other, $me]);
            q("DELETE FROM notifications WHERE user_id = ? AND from_id = ? AND type = 'friend_request'", [$me, $other]);
            break;
        case 'unfriend':
            q('DELETE FROM friendships WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)', [$me, $other, $other, $me]);
            break;
        default:
            db()->rollBack();
            fail('Unknown action');
    }
    db()->commit();
    return ['state' => friend_state($me, $other)];
});

function confirm(int $me, int $other): void
{
    q('DELETE FROM friend_requests WHERE (from_id = ? AND to_id = ?) OR (from_id = ? AND to_id = ?)', [$other, $me, $me, $other]);
    q('INSERT IGNORE INTO friendships (user_id, friend_id) VALUES (?, ?), (?, ?)', [$me, $other, $other, $me]);
    q("DELETE FROM notifications WHERE user_id = ? AND from_id = ? AND type = 'friend_request'", [$me, $other]);
    notify($other, $me, 'friend_accept');
}
