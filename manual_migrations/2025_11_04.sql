-- Manual migration: add state_payload column and remove check constraint for state_type

-- Add state_payload column if not exists
ALTER TABLE users ADD COLUMN IF NOT EXISTS state_payload TEXT;

-- Drop the "users_state_type_check" constraint on state_type (if it exists)
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_state_type_check;

-- Add unique constraint on journals (user_id, session_id, question_id) for upsert support
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'journals_user_session_question_unique'
    ) THEN
        ALTER TABLE journals ADD CONSTRAINT journals_user_session_question_unique 
            UNIQUE (user_id, session_id, question_id);
    END IF;
END $$;


