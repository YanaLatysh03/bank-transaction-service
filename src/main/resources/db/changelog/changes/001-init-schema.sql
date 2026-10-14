--liquibase formatted sql

--changeset latysh:001-expense-limit
CREATE TABLE expense_limit (
                               id                  BIGINT GENERATED ALWAYS AS IDENTITY,
                               account_from        VARCHAR(64)    NOT NULL,
                               expense_category    VARCHAR(16)    NOT NULL,
                               limit_sum           NUMERIC(19, 2) NOT NULL,
                               limit_datetime      TIMESTAMPTZ    NOT NULL,
                               currency_shortname  VARCHAR(3)     NOT NULL DEFAULT 'USD',
                               CONSTRAINT pk_expense_limit PRIMARY KEY (id),
                               CONSTRAINT ck_expense_limit_category CHECK (expense_category IN ('product', 'service')),
                               CONSTRAINT ck_expense_limit_sum CHECK (limit_sum > 0),
                               CONSTRAINT ck_expense_limit_currency CHECK (currency_shortname = 'USD')
);

CREATE INDEX idx_expense_limit_lookup
    ON expense_limit (account_from, expense_category, limit_datetime DESC);

--changeset latysh:002-account-category-lock
CREATE TABLE account_category_lock (
                                       account_from     VARCHAR(64) NOT NULL,
                                       expense_category VARCHAR(16) NOT NULL,
                                       CONSTRAINT pk_account_category_lock PRIMARY KEY (account_from, expense_category),
                                       CONSTRAINT ck_account_category_lock_category CHECK (expense_category IN ('product', 'service'))
);

--changeset latysh:003-bank-transaction
CREATE TABLE bank_transaction (
                                  id                  BIGINT GENERATED ALWAYS AS IDENTITY,
                                  account_from        VARCHAR(64)    NOT NULL,
                                  account_to          VARCHAR(64)    NOT NULL,
                                  currency_shortname  VARCHAR(3)     NOT NULL,
                                  sum                 NUMERIC(19, 2) NOT NULL,
                                  expense_category    VARCHAR(16)    NOT NULL,
                                  datetime            TIMESTAMPTZ    NOT NULL,
                                  sum_usd             NUMERIC(19, 2) NOT NULL,
                                  limit_exceeded      BOOLEAN        NOT NULL,
                                  expense_limit_id    BIGINT,
                                  CONSTRAINT pk_bank_transaction PRIMARY KEY (id),
                                  CONSTRAINT fk_bank_transaction_expense_limit
                                      FOREIGN KEY (expense_limit_id) REFERENCES expense_limit (id),
                                  CONSTRAINT ck_bank_transaction_category CHECK (expense_category IN ('product', 'service')),
                                  CONSTRAINT ck_bank_transaction_sum CHECK (sum >= 0),
                                  CONSTRAINT ck_bank_transaction_sum_usd CHECK (sum_usd >= 0)
);

CREATE INDEX idx_bank_transaction_month
    ON bank_transaction (account_from, expense_category, datetime);

CREATE INDEX idx_bank_transaction_exceeded
    ON bank_transaction (datetime)
    WHERE limit_exceeded;

--changeset latysh:004-exchange-rate
CREATE TABLE exchange_rate (
                               id         BIGINT GENERATED ALWAYS AS IDENTITY,
                               base       VARCHAR(3)     NOT NULL,
                               quote      VARCHAR(3)     NOT NULL,
                               rate_date  DATE           NOT NULL,
                               close      NUMERIC(19, 8) NOT NULL,
                               CONSTRAINT pk_exchange_rate PRIMARY KEY (id),
                               CONSTRAINT uq_exchange_rate_pair_date UNIQUE (base, quote, rate_date),
                               CONSTRAINT ck_exchange_rate_close CHECK (close > 0)
    );
