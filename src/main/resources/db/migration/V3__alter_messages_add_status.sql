-- ==========================================
-- V3: Add status column to messages
-- For SSE streaming state machine (Stage 6)
-- ==========================================

ALTER TABLE messages
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'done' AFTER parent_id;

ALTER TABLE messages
    ADD CONSTRAINT chk_messages_status CHECK (status IN ('streaming', 'done', 'interrupted', 'error'));
