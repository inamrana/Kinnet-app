<?php
/** GET → { people: [{ user, state }] } — newest accounts that aren't friends yet (People you may know). */
require __DIR__ . '/../../lib.php';

$me = auth_user_id();
$rows = q('SELECT u.* FROM users u
           WHERE u.id <> ? AND u.profile_complete = 1
             AND u.id NOT IN (SELECT friend_id FROM friendships WHERE user_id = ?)
           ORDER BY u.created_at DESC LIMIT 8', [$me, $me])->fetchAll();
respond(['people' => array_map(fn($u) => ['user' => user_json($u), 'state' => friend_state($me, (int)$u['id'])], $rows)]);
