<?php
/**
 * GET → { logs } — my call history, newest first (the last 50): who, voice or video, whether I
 * made it, how it ended (ended, missed, declined) and how long it lasted.
 */
require __DIR__ . '/../../lib.php';

$me = auth_user_id();
expire_calls();
$rows = q("SELECT * FROM calls WHERE (caller_id = ? OR callee_id = ?) AND status NOT IN ('ringing', 'accepted')
           ORDER BY id DESC LIMIT 50", [$me, $me])->fetchAll();

$logs = [];
foreach ($rows as $c) {
    $other = (int)$c['caller_id'] === $me ? (int)$c['callee_id'] : (int)$c['caller_id'];
    $user = find_user($other);
    $logs[] = call_json($c) + [
        'otherUid' => (string)$other,
        'user' => $user ? user_json($user) : null,
        'outgoing' => (int)$c['caller_id'] === $me,
        'timestamp' => ms($c['created_at']),
    ];
}
respond(['logs' => $logs]);
