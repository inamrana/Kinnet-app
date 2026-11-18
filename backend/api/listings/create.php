<?php
/**
 * POST (multipart) title, price, location, category, image (file), clientId → { listing }.
 * The photo is saved to uploads/listings and served by URL.
 */
require __DIR__ . '/../../lib.php';
require_method('POST');

$me = auth_user_id();
once(param('clientId'), $me, function () use ($me) {
    $title = mb_substr(param('title'), 0, 60);
    $price = param('price');
    $category = param('category', 'Other');
    if ($title === '') fail('Enter a title');
    if (!ctype_digit($price) || (int)$price <= 0 || strlen($price) > 12) fail('Enter a price');
    if (!in_array($category, LISTING_CATEGORIES, true)) $category = 'Other';
    $image = save_upload('image', 'listings', ['image/'], 15_000_000);
    if ($image === '') fail('Add a photo of the item');

    q('INSERT INTO listings (client_id, seller_id, title, price, location, category, image) VALUES (?, ?, ?, ?, ?, ?, ?)',
        [param('clientId') ?: null, $me, $title, (int)$price, mb_substr(param('location'), 0, 40), $category, $image]);
    return ['listing' => listing_json(q('SELECT * FROM listings WHERE id = ?', [(int)db()->lastInsertId()])->fetch())];
});
