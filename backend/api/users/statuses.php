<?php
/** GET ids=1,2,3 → { statuses: { "1": { online, lastSeen } } }. Cheap enough to poll for green dots. */
require __DIR__ . '/../../lib.php';

auth_user_id();
$ids = array_slice(array_filter(array_map('intval', explode(',', param('ids')))), 0, 200);
$statuses = [];
if ($ids) {
    $marks = implode(',', array_fill(0, count($ids), '?'));
    foreach (q("SELECT * FROM users WHERE id IN ($marks)", $ids)->fetchAll() as $row) {
        $u = user_json($row);
        $statuses[$u['uid']] = ['online' => $u['online'], 'lastSeen' => $u['lastSeen']];
    }
}
respond(['statuses' => (object)$statuses]);
