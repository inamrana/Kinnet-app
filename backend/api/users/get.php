<?php
/** GET id → { user, state, friendCount, mutualCount, mutual: [up to 3 users] }. */
require __DIR__ . '/../../lib.php';

$me = auth_user_id();
$id = (int)param('id', (string)$me);
$user = find_user($id) ?? fail('User not found', 404);

$theirFriends = friend_ids($id);
$mutualIds = $id === $me ? [] : array_values(array_intersect($theirFriends, friend_ids($me)));
$mutual = array_map(fn($fid) => user_json(find_user($fid)), array_slice($mutualIds, 0, 3));

respond([
    'user' => user_json($user),
    'state' => friend_state($me, $id),
    'friendCount' => count($theirFriends),
    'mutualCount' => count($mutualIds),
    'mutual' => $mutual,
]);
