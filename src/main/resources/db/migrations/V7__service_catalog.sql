CREATE TABLE IF NOT EXISTS services (
 id TEXT PRIMARY KEY, name TEXT NOT NULL, icon TEXT NOT NULL, environment TEXT NOT NULL,
 description TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS service_resources (
 id TEXT PRIMARY KEY, service_id TEXT NOT NULL REFERENCES services(id) ON DELETE CASCADE,
 type TEXT NOT NULL, reference TEXT NOT NULL, device_id TEXT NOT NULL DEFAULT '',
 label TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL,
 UNIQUE(service_id,type,reference,device_id)
);
CREATE INDEX IF NOT EXISTS service_resources_service ON service_resources(service_id);
CREATE TABLE IF NOT EXISTS service_activity (
 id TEXT PRIMARY KEY, service_id TEXT NOT NULL REFERENCES services(id) ON DELETE CASCADE,
 source TEXT NOT NULL, type TEXT NOT NULL, occurred_at INTEGER NOT NULL,
 severity TEXT NOT NULL, title TEXT NOT NULL, metadata TEXT NOT NULL DEFAULT '{}'
);
CREATE INDEX IF NOT EXISTS service_activity_time ON service_activity(service_id,occurred_at DESC);
