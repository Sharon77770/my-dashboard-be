-- Existing dashboard data is preserved. Provider IDs are scoped to account and conversation.
CREATE TABLE IF NOT EXISTS communication_accounts (
 id TEXT PRIMARY KEY, provider TEXT NOT NULL, external_id TEXT NOT NULL, label TEXT NOT NULL,
 credential_cipher TEXT NOT NULL, capabilities TEXT NOT NULL, created_at INTEGER NOT NULL,
 UNIQUE(provider, external_id)
);
CREATE TABLE IF NOT EXISTS communication_messages (
 account_id TEXT NOT NULL REFERENCES communication_accounts(id) ON DELETE CASCADE,
 conversation_id TEXT NOT NULL, message_id TEXT NOT NULL, sent_at INTEGER, payload TEXT NOT NULL,
 PRIMARY KEY(account_id, conversation_id, message_id)
);
CREATE INDEX IF NOT EXISTS communication_message_time ON communication_messages(account_id, sent_at, message_id);
CREATE TABLE IF NOT EXISTS communication_cursors (
 account_id TEXT NOT NULL REFERENCES communication_accounts(id) ON DELETE CASCADE,
 conversation_id TEXT NOT NULL, cursor TEXT NOT NULL, synchronized_at INTEGER NOT NULL,
 PRIMARY KEY(account_id, conversation_id)
);
CREATE TABLE IF NOT EXISTS communication_actions (
 id TEXT PRIMARY KEY, account_id TEXT NOT NULL REFERENCES communication_accounts(id) ON DELETE CASCADE,
 payload_cipher TEXT NOT NULL, state TEXT NOT NULL CHECK(state IN ('PENDING','SENDING','SENT','UNKNOWN','CANCELLED')),
 expires_at INTEGER NOT NULL, result_id TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS communication_profiles (id TEXT PRIMARY KEY, provider TEXT NOT NULL, label TEXT NOT NULL);

CREATE TABLE IF NOT EXISTS communication_event_receipts (event_id TEXT PRIMARY KEY, received_at INTEGER NOT NULL);
