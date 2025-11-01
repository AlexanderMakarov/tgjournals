-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    telegram_id BIGINT UNIQUE NOT NULL,
    username TEXT,
    first_name TEXT,
    last_name TEXT,
    role TEXT NOT NULL CHECK (role IN ('ADMIN', 'PLAYER', 'BANNED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    state_type TEXT CHECK (state_type IN ('QA_FLOW', 'SESSION_CREATE_PENDING', 'QUESTIONS_UPDATE')),
    state_session_id BIGINT,
    state_question_index INTEGER NOT NULL DEFAULT 0,
    state_updated_at TIMESTAMP
);

-- Sessions table
CREATE TABLE IF NOT EXISTS sessions (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP
);

-- Questions table
CREATE TABLE IF NOT EXISTS questions (
    id BIGSERIAL PRIMARY KEY,
    text TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('BEFORE', 'AFTER')),
    order_index INTEGER NOT NULL,
    session_id BIGINT NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
);

-- Journals table
CREATE TABLE IF NOT EXISTS journals (
    id BIGSERIAL PRIMARY KEY,
    answer TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
);


-- Indexes for better performance
CREATE INDEX IF NOT EXISTS idx_users_telegram_id ON users(telegram_id);
CREATE INDEX IF NOT EXISTS idx_sessions_finished_at ON sessions(finished_at);
CREATE INDEX IF NOT EXISTS idx_questions_session_type ON questions(session_id, type);
CREATE INDEX IF NOT EXISTS idx_journals_user ON journals(user_id);
CREATE INDEX IF NOT EXISTS idx_journals_session ON journals(session_id);
CREATE INDEX IF NOT EXISTS idx_journals_created_at ON journals(created_at);
