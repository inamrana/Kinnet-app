<?php
/**
 * POST to=<uid>, video=1|0 → { call }. Starts ringing the other person: their app finds the call
 * by polling calls/incoming.php while it is open, and gets an FCM push when it is closed.
 * The Agora channel name is the call id; the audio and video themselves go through Agora.
 */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
$to = (int)param('to');
if ($to === $me || !find_user($to)) fail('User not found', 404);
$video = in_array(param('video'), ['1', 'true'], true) ? 1 : 0;

expire_calls();
// Anything I left ringing (an app that crashed mid-call) is over now
q("UPDATE calls SET status = 'missed', ended_at = UTC_TIMESTAMP() WHERE caller_id = ? AND status = 'ringing'", [$me]);
q('INSERT INTO calls (caller_id, callee_id, video) VALUES (?, ?, ?)', [$me, $to, $video]);
$call = q('SELECT * FROM calls WHERE id = ?', [(int)db()->lastInsertId()])->fetch();

push_to($to, [
    'type' => 'call',
    'callId' => (string)$call['id'],
    'fromUid' => (string)$me,
    'title' => find_user($me)['full_name'],
    'video' => $video ? 'true' : 'false',
]);
respond(['call' => call_json($call)], 201);
