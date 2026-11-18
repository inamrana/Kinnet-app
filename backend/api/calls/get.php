<?php
/** GET id → { call }. The call screens poll this to follow ringing → accepted → ended. */
require __DIR__ . '/../../lib.php';

$me = auth_user_id();
expire_calls();
respond(['call' => call_json(my_call($me, param('id')))]);
