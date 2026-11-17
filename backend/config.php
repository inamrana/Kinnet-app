<?php
/**
 * Database settings. Real values go in config.local.php (git-ignored); these defaults match a
 * fresh XAMPP install (user root, no password).
 */
$defaults = [
    'db_host' => '127.0.0.1',
    'db_name' => 'kinnect',
    'db_user' => 'root',
    'db_pass' => '',
    // Firebase service-account JSON used to send FCM pushes (git-ignored).
    'fcm_service_account' => __DIR__ . '/fcm-service-account.json',
];
$local = is_file(__DIR__ . '/config.local.php') ? require __DIR__ . '/config.local.php' : [];
return array_merge($defaults, $local);
