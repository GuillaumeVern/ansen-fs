CREATE TABLE IF NOT EXISTS roles
(
    role_id   INTEGER PRIMARY KEY,
    role_name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS permissions
(
    permission_id   INTEGER PRIMARY KEY,
    permission_name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS users
(
    user_id  INTEGER PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS user_roles
(
    user_id INTEGER,
    role_id INTEGER,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles (role_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS role_permissions
(
    role_id       INTEGER,
    permission_id INTEGER,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles (role_id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions (permission_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS files
(
    file_id     INTEGER PRIMARY KEY,
    external_id TEXT UNIQUE NOT NULL,
    parent_id   INTEGER,
    name        TEXT        NOT NULL,
    type        TEXT        NOT NULL,
    file_hash   TEXT,
    size_bytes  INTEGER DEFAULT 0,
    deleted_at  DATETIME DEFAULT NULL,
    FOREIGN KEY (parent_id) REFERENCES files (file_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_parent_name ON files (parent_id, name);

CREATE TABLE IF NOT EXISTS file_roles
(
    file_id          INTEGER,
    role_id          INTEGER,
    permission_level TEXT DEFAULT 'READ',
    PRIMARY KEY (file_id, role_id),
    FOREIGN KEY (file_id) REFERENCES files (file_id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles (role_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS jobs
(
    job_id      TEXT PRIMARY KEY,
    parent_uuid TEXT    NOT NULL,
    total_files INTEGER NOT NULL,
    job_type    TEXT     DEFAULT 'UPLOAD',
    status      TEXT     DEFAULT 'PENDING',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);