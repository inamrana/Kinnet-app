<?php
/**
 * Shared helpers for every Kinnect endpoint: database, JSON in/out, token auth,
 * file uploads, presence and the offline-sync duplicate guard.
 */

declare(strict_types=1);

$config = require __DIR__ . '/config.php';
date_default_timezone_set('UTC');

header('Content-Type: application/json; charset=utf-8');

set_exception_handler(function (Throwable $e) {
    error_log('[kinnect] ' . $e);
    http_response_code(500);
    echo json_encode(['ok' => false, 'error' => 'Server error']);
});

function db(): PDO
{
    static $pdo = null;
    global $config;
    if ($pdo === null) {
        $pdo = new PDO(
            "mysql:host={$config['db_host']};dbname={$config['db_name']};charset=utf8mb4",
            $config['db_user'],
            $config['db_pass'],
            [
                PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                PDO::ATTR_EMULATE_PREPARES => false,
            ]
        );
        $pdo->exec("SET time_zone = '+00:00'");
    }
    return $pdo;
}

/** Runs a prepared statement and returns it. */
function q(string $sql, array $params = []): PDOStatement
{
    $stmt = db()->prepare($sql);
    $stmt->execute($params);
    return $stmt;
}

function respond(array $data, int $code = 200): never
{
    http_response_code($code);
    echo json_encode(['ok' => true] + $data, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function fail(string $message, int $code = 400): never
{
    http_response_code($code);
    echo json_encode(['ok' => false, 'error' => $message], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function require_method(string $method): void
{
    if ($_SERVER['REQUEST_METHOD'] !== $method) fail("Use $method", 405);
}

/** Request fields from a JSON body, a form post or the query string. */
function input(): array
{
    static $data = null;
    if ($data === null) {
        $data = $_GET;
        $raw = file_get_contents('php://input');
        $json = $raw ? json_decode($raw, true) : null;
        if (is_array($json)) $data = array_merge($data, $json);
        $data = array_merge($data, $_POST);
    }
    return $data;
}

function param(string $key, string $default = ''): string
{
    $value = input()[$key] ?? $default;
    return is_scalar($value) ? trim((string)$value) : $default;
}

// ---- auth ----------------------------------------------------------------------------------

function bearer_token(): string
{
    $headers = function_exists('getallheaders') ? array_change_key_case(getallheaders(), CASE_LOWER) : [];
    $header = $headers['authorization'] ?? ($_SERVER['HTTP_AUTHORIZATION'] ?? '');
    if (preg_match('/^Bearer\s+([a-f0-9]{64})$/i', $header, $m)) return strtolower($m[1]);
    return '';
}

/** The signed-in user's id, or a 401. Every authenticated request also refreshes last_seen. */
function auth_user_id(): int
{
    static $uid = null;
    if ($uid !== null) return $uid;
    $token = bearer_token();
    if ($token === '') fail('Please log in', 401);
    $row = q('SELECT user_id FROM sessions WHERE token = ? AND revoked = 0', [$token])->fetch();
    if (!$row) fail('Your session has ended. Please log in again.', 401);
    $uid = (int)$row['user_id'];
    q('UPDATE users SET last_seen = UTC_TIMESTAMP() WHERE id = ?', [$uid]);
    return $uid;
}

function new_token(int $userId): string
{
    $token = bin2hex(random_bytes(32));
    q('INSERT INTO sessions (token, user_id) VALUES (?, ?)', [$token, $userId]);
    return $token;
}

// ---- users ---------------------------------------------------------------------------------

/** Absolute URL for a stored upload path, using the host the phone used to reach us. */
function media_url(string $path): string
{
    if ($path === '') return '';
    $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    $base = rtrim(dirname($_SERVER['SCRIPT_NAME'], substr_count(script_rel(), '/') + 1), '/');
    return "$scheme://{$_SERVER['HTTP_HOST']}$base/$path";
}

/** Script path relative to the backend folder, e.g. "api/users/get.php". */
function script_rel(): string
{
    $script = str_replace('\\', '/', realpath($_SERVER['SCRIPT_FILENAME']) ?: $_SERVER['SCRIPT_FILENAME']);
    $root = str_replace('\\', '/', realpath(__DIR__));
    return ltrim(substr($script, strlen($root)), '/');
}

const ONLINE_WINDOW_SECONDS = 70;

function ms(?string $datetime): int
{
    return $datetime ? (int)(strtotime($datetime . ' UTC') * 1000) : 0;
}

/** A user row in the same shape as the app's User class. */
function user_json(array $u): array
{
    $online = (int)$u['is_online'] === 1 && $u['last_seen'] !== null
        && (time() - strtotime($u['last_seen'] . ' UTC')) < ONLINE_WINDOW_SECONDS;
    return [
        'uid' => (string)$u['id'],
        'firstName' => $u['first_name'],
        'lastName' => $u['last_name'],
        'fullName' => $u['full_name'],
        'nameLower' => mb_strtolower($u['full_name']),
        'username' => $u['username'] ?? '',
        'email' => $u['email'],
        'gender' => $u['gender'],
        'birthday' => $u['birthday'],
        'bio' => $u['bio'],
        'website' => $u['website'],
        'phone' => $u['phone'],
        'profileImage' => media_url($u['profile_image']),
        'coverImage' => media_url($u['cover_image']),
        'profileComplete' => (bool)$u['profile_complete'],
        'online' => $online,
        'lastSeen' => ms($u['last_seen']),
        'createdAt' => ms($u['created_at']),
    ];
}

function find_user(int $id): ?array
{
    $row = q('SELECT * FROM users WHERE id = ?', [$id])->fetch();
    return $row ?: null;
}

/** Where $me stands with $other: none, requested, incoming or friends. */
function friend_state(int $me, int $other): string
{
    if ($me === $other) return 'self';
    if (q('SELECT 1 FROM friendships WHERE user_id = ? AND friend_id = ?', [$me, $other])->fetch()) return 'friends';
    if (q('SELECT 1 FROM friend_requests WHERE from_id = ? AND to_id = ?', [$me, $other])->fetch()) return 'requested';
    if (q('SELECT 1 FROM friend_requests WHERE from_id = ? AND to_id = ?', [$other, $me])->fetch()) return 'incoming';
    return 'none';
}

function friend_ids(int $uid): array
{
    return array_map('intval', q('SELECT friend_id FROM friendships WHERE user_id = ?', [$uid])->fetchAll(PDO::FETCH_COLUMN));
}

// ---- uploads -------------------------------------------------------------------------------

const UPLOAD_TYPES = [
    'image/jpeg' => 'jpg', 'image/png' => 'png', 'image/webp' => 'webp', 'image/gif' => 'gif',
    'video/mp4' => 'mp4', 'video/3gpp' => '3gp', 'video/webm' => 'webm', 'video/quicktime' => 'mov',
    'application/pdf' => 'pdf', 'text/plain' => 'txt', 'application/zip' => 'zip',
    'application/msword' => 'doc',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document' => 'docx',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation' => 'pptx',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' => 'xlsx',
];

/**
 * Saves an uploaded file under uploads/$folder with a random name and returns its relative
 * path, or '' when no file was sent. The type is checked from the file's content, not its name.
 */
function save_upload(string $field, string $folder, array $allowedPrefixes = ['image/'], int $maxBytes = 25_000_000): string
{
    $file = $_FILES[$field] ?? null;
    if (!$file || ($file['error'] ?? UPLOAD_ERR_NO_FILE) === UPLOAD_ERR_NO_FILE) return '';
    if ($file['error'] !== UPLOAD_ERR_OK) fail('Upload failed (code ' . $file['error'] . ')');
    if ($file['size'] > $maxBytes) fail('That file is too large');
    $mime = (new finfo(FILEINFO_MIME_TYPE))->file($file['tmp_name']) ?: '';
    $ext = UPLOAD_TYPES[$mime] ?? null;
    $allowed = false;
    foreach ($allowedPrefixes as $prefix) if (str_starts_with($mime, $prefix)) $allowed = true;
    if ($ext === null || !$allowed) fail("That file type isn't supported ($mime)");

    $dir = __DIR__ . "/uploads/$folder";
    if (!is_dir($dir)) mkdir($dir, 0775, true);
    $name = bin2hex(random_bytes(12)) . ".$ext";
    if (!move_uploaded_file($file['tmp_name'], "$dir/$name")) fail('Could not save the file', 500);
    return "uploads/$folder/$name";
}

function delete_upload(string $path): void
{
    if ($path !== '' && str_starts_with($path, 'uploads/')) {
        $full = __DIR__ . '/' . $path;
        if (is_file($full)) unlink($full);
    }
}

// ---- offline sync --------------------------------------------------------------------------

/**
 * Runs $action once per client-generated UUID. A retried request with the same UUID gets the
 * stored response back instead of being applied again, so syncing never creates duplicates.
 */
function once(string $clientId, int $uid, callable $action): never
{
    if ($clientId !== '' && !preg_match('/^[0-9a-f-]{36}$/i', $clientId)) fail('Bad client id');
    if ($clientId !== '') {
        $done = q('SELECT response FROM processed_actions WHERE client_id = ? AND user_id = ?', [$clientId, $uid])->fetch();
        if ($done) {
            http_response_code(200);
            echo $done['response'];
            exit;
        }
    }
    $result = ['ok' => true, 'duplicate' => false] + $action();
    $json = json_encode($result, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    if ($clientId !== '') {
        $stored = json_encode(['duplicate' => true] + $result, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
        q('INSERT IGNORE INTO processed_actions (client_id, user_id, response) VALUES (?, ?, ?)', [$clientId, $uid, $stored]);
    }
    echo $json;
    exit;
}

function notify(int $userId, int $fromId, string $type, string $targetId = '', string $text = ''): void
{
    q('INSERT INTO notifications (user_id, from_id, type, target_id, text) VALUES (?, ?, ?, ?, ?)',
        [$userId, $fromId, $type, $targetId, $text]);
}
