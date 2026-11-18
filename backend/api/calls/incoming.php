<?php
/** GET → { call } — the call ringing for me right now, or call: null. Polled while the app is open. */
require __DIR__ . '/../../lib.php';

$me = auth_user_id();
expire_calls();
$call = q("SELECT * FROM calls WHERE callee_id = ? AND status = 'ringing' ORDER BY id DESC LIMIT 1", [$me])->fetch();
respond(['call' => $call ? call_json($call) : null]);
