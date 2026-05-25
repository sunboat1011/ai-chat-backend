-- ==========================================
-- V2: Add default_model_id to user_settings
-- Source: 《Controller与Service实现指南》§3.1 补丁
-- ==========================================

ALTER TABLE user_settings
    ADD COLUMN default_model_id VARCHAR(64) NULL AFTER language;
