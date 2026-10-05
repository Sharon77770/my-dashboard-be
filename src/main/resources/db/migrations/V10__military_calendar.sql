CREATE TABLE IF NOT EXISTS military_profile (
 id INTEGER PRIMARY KEY CHECK(id=1), nickname TEXT NOT NULL, service_type TEXT NOT NULL,
 enlistment_date TEXT NOT NULL, discharge_override TEXT,
 private_first_date TEXT, corporal_date TEXT, sergeant_date TEXT,
 leave_allowance INTEGER, calendar_enabled INTEGER NOT NULL DEFAULT 1,
 revision INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS military_events (
 id TEXT PRIMARY KEY, profile_id INTEGER NOT NULL DEFAULT 1 REFERENCES military_profile(id) ON DELETE CASCADE,
 kind TEXT NOT NULL, title TEXT NOT NULL, start_date TEXT NOT NULL, end_date TEXT NOT NULL,
 leave_days INTEGER NOT NULL DEFAULT 0, notes TEXT NOT NULL DEFAULT '', revision INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS military_events_dates ON military_events(start_date,end_date);
