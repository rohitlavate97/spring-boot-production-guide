-- V2: Phase 1 (Expand) - Safe Backward-Compatible Non-Blocking Schema Evolution
-- New columns MUST be nullable or have a static constant DEFAULT without rewrites
ALTER TABLE accounts ADD COLUMN account_uuid VARCHAR(64);
ALTER TABLE accounts ADD COLUMN risk_tier VARCHAR(32) DEFAULT 'STANDARD';
