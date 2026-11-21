-- ============================================================================
-- Legacy Banking App - SQLite schema
-- Applied automatically on first start by DatabaseInitializer when the
-- database file has no tables. Can also be run by hand:
--     sqlite3 legacybank.db < database/sqlite-schema.sql
-- Re-running drops the application tables first (ALL DATA IS LOST).
-- ============================================================================

PRAGMA foreign_keys = OFF;
DROP TABLE IF EXISTS TRANSACTIONS;
DROP TABLE IF EXISTS ACCOUNTS;
DROP TABLE IF EXISTS CUSTOMERS;
DROP TABLE IF EXISTS EMPLOYEES;
DROP TABLE IF EXISTS REFERENCE_SEQUENCE;
PRAGMA foreign_keys = ON;

-- ---------------------------------------------------------------------------
-- EMPLOYEES : bank branch staff who can log in to the desktop application.
-- NOTE: passwords are stored in plain text on purpose (legacy demo system).
-- ---------------------------------------------------------------------------
CREATE TABLE EMPLOYEES (
    EMPLOYEE_ID    INTEGER PRIMARY KEY AUTOINCREMENT,
    USERNAME       VARCHAR(50)   NOT NULL UNIQUE,
    PASSWORD       VARCHAR(100)  NOT NULL,
    EMPLOYEE_NAME  VARCHAR(100)  NOT NULL,
    STATUS         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                   CHECK (STATUS IN ('ACTIVE', 'INACTIVE'))
);

-- ---------------------------------------------------------------------------
-- CUSTOMERS
-- ---------------------------------------------------------------------------
CREATE TABLE CUSTOMERS (
    CUSTOMER_ID    INTEGER PRIMARY KEY AUTOINCREMENT,
    CUSTOMER_NAME  VARCHAR(100)  NOT NULL,
    EMAIL          VARCHAR(100),
    PHONE          VARCHAR(30),
    ADDRESS        VARCHAR(250),
    CREATED_DATE   TEXT          NOT NULL DEFAULT (datetime('now', 'localtime'))
);

-- ---------------------------------------------------------------------------
-- ACCOUNTS
-- BALANCE is NUMERIC(15,2); the application always reads/writes it as BigDecimal.
-- ---------------------------------------------------------------------------
CREATE TABLE ACCOUNTS (
    ACCOUNT_ID      INTEGER PRIMARY KEY AUTOINCREMENT,
    ACCOUNT_NUMBER  VARCHAR(20)   NOT NULL UNIQUE,
    CUSTOMER_ID     INTEGER       NOT NULL REFERENCES CUSTOMERS (CUSTOMER_ID),
    ACCOUNT_TYPE    VARCHAR(20)   NOT NULL
                    CHECK (ACCOUNT_TYPE IN ('SAVINGS', 'CHECKING', 'FIXED_DEPOSIT')),
    BALANCE         NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (BALANCE >= 0),
    STATUS          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                    CHECK (STATUS IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CREATED_DATE    TEXT          NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE INDEX IDX_ACCOUNTS_CUSTOMER ON ACCOUNTS (CUSTOMER_ID);

-- ---------------------------------------------------------------------------
-- TRANSACTIONS
-- TRANSACTION_TYPE: DEPOSIT, WITHDRAWAL, TRANSFER_DEBIT, TRANSFER_CREDIT
-- The two legs of a transfer share the same REFERENCE_NUMBER.
-- ---------------------------------------------------------------------------
CREATE TABLE TRANSACTIONS (
    TRANSACTION_ID    INTEGER PRIMARY KEY AUTOINCREMENT,
    ACCOUNT_NUMBER    VARCHAR(20)   NOT NULL REFERENCES ACCOUNTS (ACCOUNT_NUMBER),
    TRANSACTION_TYPE  VARCHAR(20)   NOT NULL
                      CHECK (TRANSACTION_TYPE IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_DEBIT', 'TRANSFER_CREDIT')),
    AMOUNT            NUMERIC(15,2) NOT NULL CHECK (AMOUNT > 0),
    DESCRIPTION       VARCHAR(250),
    TRANSACTION_DATE  TEXT          NOT NULL DEFAULT (datetime('now', 'localtime')),
    REFERENCE_NUMBER  VARCHAR(30)   NOT NULL
);

-- Frequently searched: transaction history by account number, newest first
CREATE INDEX IDX_TXN_ACCOUNT_NUMBER ON TRANSACTIONS (ACCOUNT_NUMBER, TRANSACTION_DATE);
CREATE INDEX IDX_TXN_REFERENCE ON TRANSACTIONS (REFERENCE_NUMBER);

-- ---------------------------------------------------------------------------
-- REFERENCE_SEQUENCE : SQLite has no sequences, so each insert into this table
-- hands out the next reference number (REF_ID).
-- ---------------------------------------------------------------------------
CREATE TABLE REFERENCE_SEQUENCE (
    REF_ID INTEGER PRIMARY KEY AUTOINCREMENT
);
