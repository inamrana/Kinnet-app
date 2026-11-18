<?php
/**
 * GET → { stories } — my stories and my friends' stories from the last 24 hours, oldest first.
 * "Close friends" stories are only returned to people on the owner's close-friends list.
 * Expired stories are deleted (with their files) here, so they can never be returned again.
 */
require __DIR__ . '/../../lib.php';

$me = auth_user_id();

$expired = q('SELECT id, media_path, thumb_path FROM stories WHERE created_at < UTC_TIMESTAMP() - INTERVAL ' . STORY_LIFETIME_HOURS . ' HOUR')->fetchAll();
foreach ($expired as $s) {
    delete_upload($s['media_path']);
    delete_upload($s['thumb_path']);
    q('DELETE FROM stories WHERE id = ?', [$s['id']]);
}

$rows = q("SELECT s.* FROM stories s
           WHERE s.created_at >= UTC_TIMESTAMP() - INTERVAL " . STORY_LIFETIME_HOURS . " HOUR
             AND (s.owner_id = ?
                  OR (s.owner_id IN (SELECT friend_id FROM friendships WHERE user_id = ?)
                      AND (s.audience = 'friends'
                           OR EXISTS (SELECT 1 FROM close_friends c WHERE c.user_id = s.owner_id AND c.friend_id = ?))))
           ORDER BY s.created_at, s.id", [$me, $me, $me])->fetchAll();
respond(['stories' => array_map(fn($s) => story_json($s, $me), $rows), 'serverTime' => now_ms()]);
