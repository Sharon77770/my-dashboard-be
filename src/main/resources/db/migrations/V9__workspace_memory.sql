CREATE TABLE IF NOT EXISTS workspace_memories (
 id TEXT PRIMARY KEY, content TEXT NOT NULL, type TEXT NOT NULL, confidence TEXT NOT NULL,
 scope TEXT NOT NULL, status TEXT NOT NULL, importance TEXT NOT NULL, tags TEXT NOT NULL DEFAULT '',
 time_hint TEXT NOT NULL DEFAULT '', related_service_id TEXT, related_project TEXT NOT NULL DEFAULT '',
 source_type TEXT NOT NULL, source_thread_id TEXT NOT NULL DEFAULT '', source_description TEXT NOT NULL DEFAULT '',
 pinned INTEGER NOT NULL DEFAULT 0, manually_created INTEGER NOT NULL DEFAULT 0,
 created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, last_accessed_at INTEGER NOT NULL DEFAULT 0,
 access_count INTEGER NOT NULL DEFAULT 0, expires_at INTEGER, superseded_by TEXT,
 promoted_target_type TEXT, promoted_target_id TEXT,
 FOREIGN KEY(related_service_id) REFERENCES services(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS workspace_memories_status_scope ON workspace_memories(status,scope,updated_at DESC);
CREATE INDEX IF NOT EXISTS workspace_memories_service ON workspace_memories(related_service_id,status);
CREATE INDEX IF NOT EXISTS workspace_memories_expiry ON workspace_memories(status,expires_at);
CREATE INDEX IF NOT EXISTS workspace_memories_archived ON workspace_memories(status,updated_at);
CREATE TABLE IF NOT EXISTS workspace_memory_preferences (
 id INTEGER PRIMARY KEY CHECK(id=1), auto_archive INTEGER NOT NULL DEFAULT 1,
 auto_delete INTEGER NOT NULL DEFAULT 1, protect_manual INTEGER NOT NULL DEFAULT 1,
 tentative_days INTEGER NOT NULL DEFAULT 30, possibility_days INTEGER NOT NULL DEFAULT 60,
 follow_up_days INTEGER NOT NULL DEFAULT 14, archived_days INTEGER NOT NULL DEFAULT 90,
 last_cleanup_at INTEGER NOT NULL DEFAULT 0
);
INSERT OR IGNORE INTO workspace_memory_preferences(id) VALUES (1);
