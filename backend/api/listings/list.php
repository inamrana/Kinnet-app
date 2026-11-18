<?php
/** GET → { listings } — Marketplace "Today's picks", newest first (the latest 60). */
require __DIR__ . '/../../lib.php';

auth_user_id();
$rows = q('SELECT * FROM listings ORDER BY created_at DESC, id DESC LIMIT 60')->fetchAll();
respond(['listings' => array_map('listing_json', $rows)]);
