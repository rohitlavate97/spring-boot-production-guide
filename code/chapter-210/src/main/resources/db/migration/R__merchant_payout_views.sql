-- Repeatable Migration: Re-executes whenever checksum changes
CREATE OR REPLACE VIEW v_active_merchant_payouts AS
SELECT 
    id,
    merchant_id,
    payout_currency,
    status,
    COALESCE(iban, legacy_bank_account) AS effective_account_number,
    swift_routing_code,
    created_at
FROM merchant_payout_profiles
WHERE status = 'ACTIVE';
