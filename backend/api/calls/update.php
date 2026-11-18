<?php
/**
 * POST id, action=accept|decline|end → { call }.
 * Only the person being called can accept or decline, and only while it rings. Either person can
 * end it: an answered call becomes "ended", one that was still ringing becomes "missed".
 * Ending a call that is already over changes nothing, so both phones can safely hang up.
 */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
expire_calls();
$call = my_call($me, param('id'));
$id = (int)$call['id'];
$ringing = $call['status'] === 'ringing';
$isCallee = (int)$call['callee_id'] === $me;

switch (param('action')) {
    case 'accept':
        if (!$isCallee) fail('Only the person being called can answer', 403);
        if (!$ringing) fail('This call has ended', 409);
        q("UPDATE calls SET status = 'accepted', accepted_at = UTC_TIMESTAMP() WHERE id = ?", [$id]);
        break;
    case 'decline':
        if (!$isCallee) fail('Only the person being called can decline', 403);
        if ($ringing) q("UPDATE calls SET status = 'declined', ended_at = UTC_TIMESTAMP() WHERE id = ?", [$id]);
        break;
    case 'end':
        if ($ringing) {
            q("UPDATE calls SET status = 'missed', ended_at = UTC_TIMESTAMP() WHERE id = ?", [$id]);
        } elseif ($call['status'] === 'accepted') {
            q("UPDATE calls SET status = 'ended', ended_at = UTC_TIMESTAMP() WHERE id = ?", [$id]);
        }
        break;
    default:
        fail('Unknown action');
}
respond(['call' => call_json(q('SELECT * FROM calls WHERE id = ?', [$id])->fetch())]);
