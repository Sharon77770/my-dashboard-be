CREATE TABLE IF NOT EXISTS database_connections (
 id TEXT PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, host TEXT NOT NULL DEFAULT '',
 port INTEGER NOT NULL DEFAULT 0, database_name TEXT NOT NULL, username TEXT NOT NULL DEFAULT '',
 credential_cipher TEXT NOT NULL DEFAULT '', ssl_mode TEXT NOT NULL, access_mode TEXT NOT NULL,
 metadata TEXT NOT NULL DEFAULT '{}',
 created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS database_query_history (
 id TEXT PRIMARY KEY, connection_id TEXT REFERENCES database_connections(id) ON DELETE SET NULL,
 connection_name TEXT NOT NULL, sql_text TEXT NOT NULL, executed_at INTEGER NOT NULL,
 duration_ms INTEGER NOT NULL, result_type TEXT NOT NULL, success INTEGER NOT NULL,
 error_type TEXT NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS database_history_time ON database_query_history(executed_at DESC);
CREATE TABLE IF NOT EXISTS database_query_favorites (
 id TEXT PRIMARY KEY, connection_id TEXT REFERENCES database_connections(id) ON DELETE CASCADE,
 name TEXT NOT NULL, sql_text TEXT NOT NULL, created_at INTEGER NOT NULL
);
