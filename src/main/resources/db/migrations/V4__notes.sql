CREATE TABLE IF NOT EXISTS note_entries (
 id TEXT PRIMARY KEY, parent_id TEXT REFERENCES note_entries(id) ON DELETE RESTRICT,
 kind TEXT NOT NULL CHECK(kind IN ('FOLDER','DOCUMENT')), title TEXT NOT NULL, icon TEXT NOT NULL,
 content TEXT NOT NULL DEFAULT '[]', revision INTEGER NOT NULL DEFAULT 0,
 created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS note_entries_parent ON note_entries(parent_id);
CREATE TABLE IF NOT EXISTS note_images (
 id TEXT PRIMARY KEY, document_id TEXT NOT NULL REFERENCES note_entries(id) ON DELETE CASCADE,
 media_type TEXT NOT NULL, data BLOB NOT NULL, created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS note_images_document ON note_images(document_id);
PRAGMA user_version=4;
