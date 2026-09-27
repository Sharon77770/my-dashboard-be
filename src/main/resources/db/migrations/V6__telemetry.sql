CREATE TABLE IF NOT EXISTS telemetry_services (
 id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL, service_type TEXT NOT NULL,
 enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0,1)), created_at INTEGER NOT NULL,
 api_key_hash TEXT NOT NULL, last_used_at INTEGER
);
CREATE TABLE IF NOT EXISTS telemetry_events (
 id TEXT PRIMARY KEY, service_id TEXT NOT NULL REFERENCES telemetry_services(id) ON DELETE CASCADE,
 type TEXT NOT NULL, occurred_at INTEGER NOT NULL, anonymous_user_id TEXT, properties TEXT NOT NULL DEFAULT '{}'
);
CREATE INDEX IF NOT EXISTS telemetry_events_service_time ON telemetry_events(service_id, occurred_at);
CREATE INDEX IF NOT EXISTS telemetry_events_users ON telemetry_events(service_id, type, occurred_at, anonymous_user_id);
CREATE TABLE IF NOT EXISTS telemetry_gauges (
 id TEXT PRIMARY KEY, service_id TEXT NOT NULL REFERENCES telemetry_services(id) ON DELETE CASCADE,
 name TEXT NOT NULL, value REAL NOT NULL, occurred_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS telemetry_gauges_service_time ON telemetry_gauges(service_id, occurred_at);
CREATE TABLE IF NOT EXISTS service_metrics_hourly (
 service_id TEXT NOT NULL REFERENCES telemetry_services(id) ON DELETE CASCADE, hour_start INTEGER NOT NULL,
 request_count INTEGER NOT NULL DEFAULT 0, error_count INTEGER NOT NULL DEFAULT 0,
 latency_sum REAL NOT NULL DEFAULT 0, latency_count INTEGER NOT NULL DEFAULT 0,
 PRIMARY KEY(service_id, hour_start)
);
CREATE INDEX IF NOT EXISTS telemetry_hourly_time ON service_metrics_hourly(service_id, hour_start);
CREATE TABLE IF NOT EXISTS service_metrics_daily (
 service_id TEXT NOT NULL REFERENCES telemetry_services(id) ON DELETE CASCADE, day_start INTEGER NOT NULL,
 request_count INTEGER NOT NULL DEFAULT 0, error_count INTEGER NOT NULL DEFAULT 0,
 latency_sum REAL NOT NULL DEFAULT 0, latency_count INTEGER NOT NULL DEFAULT 0,
 PRIMARY KEY(service_id, day_start)
);
CREATE INDEX IF NOT EXISTS telemetry_daily_time ON service_metrics_daily(service_id, day_start);
PRAGMA user_version=6;
