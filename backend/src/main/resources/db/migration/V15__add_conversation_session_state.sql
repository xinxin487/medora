-- Phase 9.3: 为 conversation 表添加 session_state JSONB 字段
-- 用于存储 ADK Session 的 state Map(userAccountId / userMessage / traceId / pendingBooking 等)
-- 后向兼容:允许 NULL,旧 conversation 记录不受影响

ALTER TABLE conversation
    ADD COLUMN session_state JSONB;
