-- Enable foreign keys
PRAGMA foreign_keys = ON;

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    telegram_id INTEGER UNIQUE NOT NULL,
    username TEXT,
    first_name TEXT,
    last_name TEXT,
    role TEXT NOT NULL CHECK (role IN ('COACH', 'PLAYER')),
    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)
);

-- Sessions table
CREATE TABLE IF NOT EXISTS sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    date INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
    is_active BOOLEAN NOT NULL DEFAULT 1
);

-- Questions table
CREATE TABLE IF NOT EXISTS questions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    text TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('BEFORE', 'AFTER')),
    order_index INTEGER NOT NULL,
    session_id INTEGER NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
);

-- Journals table
CREATE TABLE IF NOT EXISTS journals (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    answer TEXT NOT NULL,
    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
    user_id INTEGER NOT NULL,
    session_id INTEGER NOT NULL,
    question_id INTEGER NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
);

-- Indexes for better performance
CREATE INDEX IF NOT EXISTS idx_users_telegram_id ON users(telegram_id);
CREATE INDEX IF NOT EXISTS idx_sessions_active ON sessions(is_active);
CREATE INDEX IF NOT EXISTS idx_questions_session_type ON questions(session_id, type);
CREATE INDEX IF NOT EXISTS idx_journals_user ON journals(user_id);
CREATE INDEX IF NOT EXISTS idx_journals_session ON journals(session_id);
CREATE INDEX IF NOT EXISTS idx_journals_created_at ON journals(created_at);
