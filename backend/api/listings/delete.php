<?php
/** POST id → removes one of my listings and its photo. */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
$listing = q('SELECT * FROM listings WHERE id = ? AND seller_id = ?', [(int)param('id'), $me])->fetch();
if (!$listing) fail('Listing not found', 404);
delete_upload($listing['image']);
q('DELETE FROM listings WHERE id = ?', [(int)$listing['id']]);
respond([]);
