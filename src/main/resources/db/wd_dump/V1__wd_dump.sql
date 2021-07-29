


--
-- Name: wallets; Type: TABLE; Schema: public; Owner: ledger
--
CREATE TABLE public.wallets (
 uid VARCHAR(255) NOT NULL,
 name VARCHAR(255),
 currency_name VARCHAR(255) NOT NULL,
 pool_name VARCHAR(255) NOT NULL,
 configuration TEXT NOT NULL,
 created_at TEXT NOT NULL
);

ALTER TABLE public.wallets OWNER TO ledger;

ALTER TABLE ONLY public.wallets
    ADD CONSTRAINT wallets_pkey PRIMARY KEY (uid);

--ALTER TABLE ONLY public.wallets
--    ADD CONSTRAINT wallets_currency_name_fkey FOREIGN KEY (currency_name) REFERENCES public.currencies(name) ON UPDATE CASCADE ON DELETE CASCADE;
--    ADD CONSTRAINT wallets_pool_name_fkey FOREIGN KEY (pool_name) REFERENCES public.pools(name) ON UPDATE CASCADE ON DELETE CASCADE;

--
-- Name: accounts; Type: TABLE; Schema: public; Owner: ledger
--
CREATE TABLE public.accounts (
    uid VARCHAR(255) NOT NULL,
    idx integer NOT NULL,
    wallet_uid VARCHAR(255) NOT NULL,
    created_at TEXT NOT NULL
 );

ALTER TABLE public.accounts OWNER TO ledger;

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT accounts_pkey PRIMARY KEY (uid);

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT accounts_wallet_uid_fkey FOREIGN KEY (wallet_uid) REFERENCES public.wallets(uid) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: bitcoin_inputs; Type: TABLE; Schema: public; Owner: ledger
--

CREATE TABLE public.bitcoin_inputs (
    uid character varying(255) NOT NULL,
    previous_output_idx integer,
    previous_tx_hash character varying(255),
    previous_tx_uid character varying(255),
    amount bigint,
    address character varying(255),
    coinbase character varying(255),
    sequence bigint NOT NULL
);


ALTER TABLE public.bitcoin_inputs OWNER TO ledger;

--
-- Name: bitcoin_operations; Type: TABLE; Schema: public; Owner: ledger
--

CREATE TABLE public.bitcoin_operations (
    uid character varying(255) NOT NULL,
    transaction_uid character varying(255) NOT NULL,
    transaction_hash character varying(255) NOT NULL
);


ALTER TABLE public.bitcoin_operations OWNER TO ledger;

--
-- Name: bitcoin_outputs; Type: TABLE; Schema: public; Owner: ledger
--

CREATE TABLE public.bitcoin_outputs (
    idx integer NOT NULL,
    transaction_uid character varying(255) NOT NULL,
    transaction_hash character varying(255) NOT NULL,
    amount bigint NOT NULL,
    script text NOT NULL,
    address character varying(255),
    account_uid character varying(255),
    block_height bigint,
    replaceable integer DEFAULT 0
);


ALTER TABLE public.bitcoin_outputs OWNER TO ledger;

--
-- Name: bitcoin_transaction_inputs; Type: TABLE; Schema: public; Owner: ledger
--

CREATE TABLE public.bitcoin_transaction_inputs (
    transaction_uid character varying(255) NOT NULL,
    transaction_hash character varying(255) NOT NULL,
    input_uid character varying(255) NOT NULL,
    input_idx integer NOT NULL
);


ALTER TABLE public.bitcoin_transaction_inputs OWNER TO ledger;

--
-- Name: bitcoin_transactions; Type: TABLE; Schema: public; Owner: ledger
--

CREATE TABLE public.bitcoin_transactions (
    transaction_uid character varying(255) NOT NULL,
    hash character varying(255) NOT NULL,
    version integer,
    block_uid character varying(255),
    "time" character varying(255),
    locktime integer
);


ALTER TABLE public.bitcoin_transactions OWNER TO ledger;

--
-- Name: blocks; Type: TABLE; Schema: public; Owner: ledger
--

CREATE TABLE public.blocks (
    uid character varying(255) NOT NULL,
    hash character varying(255) NOT NULL,
    height bigint NOT NULL,
    "time" character varying(255) NOT NULL,
    currency_name character varying(255)
);


ALTER TABLE public.blocks OWNER TO ledger;

--
-- Name: operations; Type: TABLE; Schema: public; Owner: ledger
--

CREATE TABLE public.operations (
    uid character varying(255) NOT NULL,
    account_uid character varying(255) NOT NULL,
    wallet_uid character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    date character varying(255) NOT NULL,
    senders text NOT NULL,
    recipients text NOT NULL,
    amount character varying(255) NOT NULL,
    fees character varying(255),
    block_uid character varying(255),
    currency_name character varying(255) NOT NULL,
    trust text
);


ALTER TABLE public.operations OWNER TO ledger;


--
-- Name: bitcoin_inputs bitcoin_inputs_pkey; Type: CONSTRAINT; Schema: public; Owner: ledger
--

ALTER TABLE ONLY public.bitcoin_inputs
    ADD CONSTRAINT bitcoin_inputs_pkey PRIMARY KEY (uid);


--
-- Name: bitcoin_operations bitcoin_operations_pkey; Type: CONSTRAINT; Schema: public; Owner: ledger
--

ALTER TABLE ONLY public.bitcoin_operations
    ADD CONSTRAINT bitcoin_operations_pkey PRIMARY KEY (uid);


--
-- Name: bitcoin_outputs bitcoin_outputs_pkey; Type: CONSTRAINT; Schema: public; Owner: ledger
--

ALTER TABLE ONLY public.bitcoin_outputs
    ADD CONSTRAINT bitcoin_outputs_pkey PRIMARY KEY (idx, transaction_uid);


--
-- Name: bitcoin_transaction_inputs bitcoin_transaction_inputs_pkey; Type: CONSTRAINT; Schema: public; Owner: ledger
--

ALTER TABLE ONLY public.bitcoin_transaction_inputs
    ADD CONSTRAINT bitcoin_transaction_inputs_pkey PRIMARY KEY (transaction_uid, input_uid);


--
-- Name: bitcoin_transactions bitcoin_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: ledger
--

ALTER TABLE ONLY public.bitcoin_transactions
    ADD CONSTRAINT bitcoin_transactions_pkey PRIMARY KEY (transaction_uid);


--
-- Name: blocks blocks_pkey; Type: CONSTRAINT; Schema: public; Owner: ledger
--

ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT blocks_pkey PRIMARY KEY (uid);


--
-- Name: operations operations_pkey; Type: CONSTRAINT; Schema: public; Owner: ledger
--


ALTER TABLE ONLY public.operations
    ADD CONSTRAINT operations_pkey PRIMARY KEY (uid);

--
-- Name: bitcoin_operations bitcoin_operations_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ledger
--

ALTER TABLE ONLY public.bitcoin_operations
    ADD CONSTRAINT bitcoin_operations_uid_fkey FOREIGN KEY (uid) REFERENCES public.operations(uid) ON DELETE CASCADE;


--
-- Name: bitcoin_outputs bitcoin_outputs_transaction_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ledger
--

ALTER TABLE ONLY public.bitcoin_outputs
    ADD CONSTRAINT bitcoin_outputs_transaction_uid_fkey FOREIGN KEY (transaction_uid) REFERENCES public.bitcoin_transactions(transaction_uid) ON DELETE CASCADE;


--
-- Name: bitcoin_transaction_inputs bitcoin_transaction_inputs_input_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ledger
--

ALTER TABLE ONLY public.bitcoin_transaction_inputs
    ADD CONSTRAINT bitcoin_transaction_inputs_input_uid_fkey FOREIGN KEY (input_uid) REFERENCES public.bitcoin_inputs(uid) ON DELETE CASCADE;


--
-- Name: bitcoin_transaction_inputs bitcoin_transaction_inputs_transaction_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ledger
--

ALTER TABLE ONLY public.bitcoin_transaction_inputs
    ADD CONSTRAINT bitcoin_transaction_inputs_transaction_uid_fkey FOREIGN KEY (transaction_uid) REFERENCES public.bitcoin_transactions(transaction_uid) ON DELETE CASCADE;


--
-- Name: bitcoin_transactions bitcoin_transactions_block_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ledger
--

ALTER TABLE ONLY public.bitcoin_transactions
    ADD CONSTRAINT bitcoin_transactions_block_uid_fkey FOREIGN KEY (block_uid) REFERENCES public.blocks(uid) ON DELETE CASCADE;


--
-- Name: operations operations_account_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ledger
--

ALTER TABLE ONLY public.operations
    ADD CONSTRAINT operations_account_uid_fkey FOREIGN KEY (account_uid) REFERENCES public.accounts(uid) ON DELETE CASCADE;


--
-- Name: operations operations_block_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ledger
--

ALTER TABLE ONLY public.operations
    ADD CONSTRAINT operations_block_uid_fkey FOREIGN KEY (block_uid) REFERENCES public.blocks(uid) ON DELETE CASCADE;


--
-- Name: operations operations_wallet_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ledger
--

ALTER TABLE ONLY public.operations
    ADD CONSTRAINT operations_wallet_uid_fkey FOREIGN KEY (wallet_uid) REFERENCES public.wallets(uid) ON DELETE CASCADE;

