CREATE TABLE merchant_payout_profiles (
    id VARCHAR(36) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    payout_currency VARCHAR(3) NOT NULL,
    legacy_bank_account VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_merchant_payout UNIQUE (merchant_id)
);
