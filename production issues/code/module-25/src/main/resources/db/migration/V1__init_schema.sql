-- V1: Initial Accounts Schema
CREATE TABLE accounts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_number VARCHAR(32) NOT NULL UNIQUE,
    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO accounts (account_number, balance) VALUES ('ACC-1001', 50000.00);
INSERT INTO accounts (account_number, balance) VALUES ('ACC-1002', 125000.00);
