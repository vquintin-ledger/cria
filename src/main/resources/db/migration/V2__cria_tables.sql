CREATE TYPE operation_type as ENUM(
    'send',
    'receive'
);

CREATE TYPE change_type as ENUM(
    'internal',
    'external'
);

CREATE TABLE transaction (
    account_uid VARCHAR NOT NULL,
    id VARCHAR NOT NULL,
    hash VARCHAR NOT NULL,
    block_hash VARCHAR,
    block_height BIGINT,
    block_time timestamp with time zone,
    received_at timestamp with time zone,
    lock_time BIGINT,
    fees NUMERIC(30, 0),
    confirmations INTEGER,

    PRIMARY KEY (account_uid, hash)
);

CREATE TABLE input (
    account_uid VARCHAR NOT NULL,
    hash VARCHAR NOT NULL,
    output_hash VARCHAR NOT NULL,
    output_index INTEGER NOT NULL,
    input_index INTEGER NOT NULL,
    value NUMERIC(30, 0) NOT NULL,
    address VARCHAR NOT NULL,
    script_signature VARCHAR,
    txinwitness VARCHAR[],
    sequence BIGINT NOT NULL,
    derivation INTEGER[],

    PRIMARY KEY (account_uid, hash, output_hash, output_index),
    FOREIGN KEY (account_uid, hash) REFERENCES transaction (account_uid, hash) ON DELETE CASCADE
);

CREATE INDEX on input(address);

CREATE TABLE output (
    account_uid VARCHAR NOT NULL,
    hash VARCHAR NOT NULL,
    output_index INTEGER NOT NULL,
    value NUMERIC(30, 0) NOT NULL,
    address VARCHAR NOT NULL,
    script_hex VARCHAR,
    derivation INTEGER[],
    change_type CHANGE_TYPE,

    PRIMARY KEY (account_uid, hash, output_index),
    FOREIGN KEY (account_uid, hash) REFERENCES transaction (account_uid, hash) ON DELETE CASCADE
);

CREATE INDEX on output(address);


CREATE VIEW transaction_amount AS
WITH inputs AS (
    SELECT account_uid, hash, SUM(value) AS input_amount
    FROM input
    WHERE derivation IS NOT NULL
    GROUP BY account_uid, hash
),

outputs AS (
    SELECT account_uid,
        hash,
        SUM(CASE WHEN change_type = 'external' THEN value ELSE 0 END) AS output_amount,
        SUM(CASE WHEN change_type = 'internal' THEN value ELSE 0 END) AS change_amount
    FROM output
    WHERE derivation IS NOT NULL
    GROUP BY account_uid, hash
)

SELECT t.account_uid,
       t.hash,
       t.block_hash,
       t.block_height,
       t.block_time,
       t.fees,
       i.input_amount,
       o.output_amount,
       o.change_amount
FROM transaction as t
         LEFT JOIN inputs  AS i ON t.account_uid = i.account_uid AND t.hash = i.hash
         LEFT JOIN outputs AS o ON t.account_uid = o.account_uid AND t.hash = o.hash;
