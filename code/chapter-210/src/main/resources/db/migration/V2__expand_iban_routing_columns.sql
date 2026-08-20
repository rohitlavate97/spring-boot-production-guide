-- Phase 1 (Expand): Add nullable new columns for international IBAN and SWIFT routing
ALTER TABLE merchant_payout_profiles ADD COLUMN iban VARCHAR(34);
ALTER TABLE merchant_payout_profiles ADD COLUMN swift_routing_code VARCHAR(11);
