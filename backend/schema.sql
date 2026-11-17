-- Kinnect MySQL schema (MariaDB / MySQL 8). Import once:
--   mysql -u root < schema.sql
-- Media files live on disk under backend/uploads/; tables store their relative paths.

CREATE DATABASE IF NOT EXISTS kinnect CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE kinnect;

-- Accounts ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS users (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    email            VARCHAR(190) NOT NULL UNIQUE,
    password_hash    VARCHAR(255) NOT NULL,
    first_name       VARCHAR(60)  NOT NULL DEFAULT '',
    last_name        VARCHAR(60)  NOT NULL DEFAULT '',
    full_name        VARCHAR(130) NOT NULL DEFAULT '',
    username         VARCHAR(30)  NULL UNIQUE,
    bio              VARCHAR(200) NOT NULL DEFAULT '',
    website          VARCHAR(200) NOT NULL DEFAULT '',
    phone            VARCHAR(30)  NOT NULL DEFAULT '',
    gender           VARCHAR(20)  NOT NULL DEFAULT '',
    birthday         VARCHAR(10)  NOT NULL DEFAULT '',
    profile_image    VARCHAR(255) NOT NULL DEFAULT '',
    cover_image      VARCHAR(255) NOT NULL DEFAULT '',
    profile_complete TINYINT(1)   NOT NULL DEFAULT 0,
    is_online        TINYINT(1)   NOT NULL DEFAULT 0,
    last_seen        DATETIME     NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_users_name (full_name)
) ENGINE=InnoDB;

-- One row per login. Logging out revokes the token.
CREATE TABLE IF NOT EXISTS sessions (
    token      CHAR(64)  PRIMARY KEY,
    user_id    INT       NOT NULL,
    created_at DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked    TINYINT(1) NOT NULL DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS fcm_tokens (
    user_id    INT          PRIMARY KEY,
    token      VARCHAR(255) NOT NULL,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Offline sync: every queued action carries a client UUID; a repeated UUID is answered
-- from here instead of being applied twice (no duplicates).
CREATE TABLE IF NOT EXISTS processed_actions (
    client_id  CHAR(36)  PRIMARY KEY,
    user_id    INT       NOT NULL,
    response   MEDIUMTEXT NOT NULL,
    created_at DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Friends ---------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS friend_requests (
    from_id    INT      NOT NULL,
    to_id      INT      NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (from_id, to_id),
    FOREIGN KEY (from_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (to_id)   REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Stored in both directions so "my friends" is a single indexed lookup.
CREATE TABLE IF NOT EXISTS friendships (
    user_id    INT      NOT NULL,
    friend_id  INT      NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, friend_id),
    FOREIGN KEY (user_id)   REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (friend_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS close_friends (
    user_id   INT NOT NULL,
    friend_id INT NOT NULL,
    PRIMARY KEY (user_id, friend_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS recent_searches (
    user_id    INT          NOT NULL,
    text       VARCHAR(100) NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, text)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS notifications (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT          NOT NULL,
    from_id    INT          NOT NULL,
    type       VARCHAR(30)  NOT NULL,
    target_id  VARCHAR(40)  NOT NULL DEFAULT '',
    text       VARCHAR(255) NOT NULL DEFAULT '',
    is_read    TINYINT(1)   NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notif_user (user_id, created_at)
) ENGINE=InnoDB;

-- Posts, reactions, comments --------------------------------------------------------------

CREATE TABLE IF NOT EXISTS posts (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    client_id   CHAR(36)     NULL UNIQUE,
    author_id   INT          NOT NULL,
    text        TEXT         NOT NULL,
    bg_color    VARCHAR(20)  NOT NULL DEFAULT '',
    share_count INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_posts_author (author_id, created_at),
    FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS post_images (
    post_id INT          NOT NULL,
    idx     INT          NOT NULL,
    path    VARCHAR(255) NOT NULL,
    PRIMARY KEY (post_id, idx),
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- One reaction per user per post (primary key), so a new pick replaces the old one.
CREATE TABLE IF NOT EXISTS reactions (
    post_id    INT         NOT NULL,
    user_id    INT         NOT NULL,
    type       VARCHAR(10) NOT NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, user_id),
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS comments (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    client_id     CHAR(36)     NULL UNIQUE,
    post_id       INT          NOT NULL,
    author_id     INT          NOT NULL,
    parent_id     INT          NULL,
    reply_to_name VARCHAR(130) NOT NULL DEFAULT '',
    text          TEXT         NOT NULL,
    image         VARCHAR(255) NOT NULL DEFAULT '',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_comments_post (post_id, created_at),
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS comment_likes (
    comment_id INT NOT NULL,
    user_id    INT NOT NULL,
    PRIMARY KEY (comment_id, user_id),
    FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Stories (the server only returns stories younger than 24 hours) -------------------------

CREATE TABLE IF NOT EXISTS stories (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    client_id   CHAR(36)     NULL UNIQUE,
    owner_id    INT          NOT NULL,
    media_path  VARCHAR(255) NOT NULL,
    thumb_path  VARCHAR(255) NOT NULL DEFAULT '',
    is_video    TINYINT(1)   NOT NULL DEFAULT 0,
    label       VARCHAR(120) NOT NULL DEFAULT '',
    label_style VARCHAR(10)  NOT NULL DEFAULT 'red',
    label_x     DOUBLE       NOT NULL DEFAULT 0.1,
    label_y     DOUBLE       NOT NULL DEFAULT 0.5,
    audience    VARCHAR(10)  NOT NULL DEFAULT 'friends',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_stories_owner (owner_id, created_at),
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS story_views (
    story_id  INT         NOT NULL,
    user_id   INT         NOT NULL,
    reaction  VARCHAR(10) NOT NULL DEFAULT '',
    viewed_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (story_id, user_id),
    FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Messaging -------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS messages (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    client_id     CHAR(36)     NULL UNIQUE,
    sender_id     INT          NOT NULL,
    receiver_id   INT          NOT NULL,
    type          VARCHAR(10)  NOT NULL DEFAULT 'text',
    text          TEXT         NOT NULL,
    media_path    VARCHAR(255) NOT NULL DEFAULT '',
    file_name     VARCHAR(200) NOT NULL DEFAULT '',
    post_id       INT          NULL,
    reply_to_id   INT          NULL,
    reply_to_text VARCHAR(255) NOT NULL DEFAULT '',
    edited        TINYINT(1)   NOT NULL DEFAULT 0,
    deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    is_read       TINYINT(1)   NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_messages_pair (sender_id, receiver_id, id),
    INDEX idx_messages_updated (updated_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS message_hearts (
    message_id INT NOT NULL,
    user_id    INT NOT NULL,
    PRIMARY KEY (message_id, user_id),
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Calls (Agora carries the media; these rows are the signalling and the call log) ---------

CREATE TABLE IF NOT EXISTS calls (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    caller_id   INT         NOT NULL,
    callee_id   INT         NOT NULL,
    video       TINYINT(1)  NOT NULL DEFAULT 0,
    status      VARCHAR(10) NOT NULL DEFAULT 'ringing',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at DATETIME    NULL,
    ended_at    DATETIME    NULL,
    INDEX idx_calls_callee (callee_id, status)
) ENGINE=InnoDB;

-- Marketplace -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS listings (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    client_id  CHAR(36)     NULL UNIQUE,
    seller_id  INT          NOT NULL,
    title      VARCHAR(60)  NOT NULL,
    price      BIGINT       NOT NULL,
    location   VARCHAR(40)  NOT NULL DEFAULT '',
    category   VARCHAR(30)  NOT NULL DEFAULT 'Other',
    image      VARCHAR(255) NOT NULL DEFAULT '',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
