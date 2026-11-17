<?php
/**
 * GET q, filter=everyone|friends|requests → { results: [{ user, state }] }.
 * Matches the start of the username, the full name or any word of the name.
 */
require __DIR__ . '/../../lib.php';

$me = auth_user_id();
$text = mb_strtolower(param('q'));
$filter = param('filter', 'everyone');
$like = addcslashes($text, '%_\\') . '%';

$where = ['u.profile_complete = 1', 'u.id <> ?'];
$params = [$me];
if ($text !== '') {
    $where[] = '(u.username LIKE ? OR LOWER(u.full_name) LIKE ? OR LOWER(u.full_name) LIKE ?)';
    array_push($params, $like, $like, "% $like");
} elseif ($filter === 'everyone') {
    respond(['results' => []]);
}
if ($filter === 'friends') {
    $where[] = 'u.id IN (SELECT friend_id FROM friendships WHERE user_id = ?)';
    $params[] = $me;
} elseif ($filter === 'requests') {
    $where[] = '(u.id IN (SELECT from_id FROM friend_requests WHERE to_id = ?) OR u.id IN (SELECT to_id FROM friend_requests WHERE from_id = ?))';
    array_push($params, $me, $me);
}

$rows = q('SELECT u.* FROM users u WHERE ' . implode(' AND ', $where) . ' ORDER BY u.full_name LIMIT 30', $params)->fetchAll();
respond(['results' => array_map(fn($u) => ['user' => user_json($u), 'state' => friend_state($me, (int)$u['id'])], $rows)]);
