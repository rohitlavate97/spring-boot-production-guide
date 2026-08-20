-- Performance Index for status and creation lookup
CREATE INDEX idx_payout_status_created ON merchant_payout_profiles (status, created_at);
