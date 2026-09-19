CREATE TABLE IF NOT EXISTS devices (
 id TEXT PRIMARY KEY, name TEXT NOT NULL, host TEXT NOT NULL, ssh_port INTEGER NOT NULL,
 username TEXT NOT NULL, password_cipher TEXT NOT NULL, fingerprint TEXT NOT NULL,
 root_path TEXT NOT NULL, remote_protocol TEXT NOT NULL, remote_port INTEGER NOT NULL,
 remote_username TEXT NOT NULL, remote_password_cipher TEXT NOT NULL, mac TEXT NOT NULL,
 broadcast TEXT NOT NULL, pinned INTEGER NOT NULL DEFAULT 0,
 network_mode TEXT NOT NULL DEFAULT 'DIRECT' CHECK(network_mode IN ('DIRECT','TAILSCALE')),
 jump_device_ids TEXT NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS applications (
 id TEXT PRIMARY KEY, name TEXT NOT NULL, url TEXT NOT NULL, pinned INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS clips (
 id TEXT PRIMARY KEY, content TEXT NOT NULL, expires_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS clips_expiry ON clips(expires_at);
CREATE TABLE IF NOT EXISTS bookmarks (
 id TEXT PRIMARY KEY, device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
 path TEXT NOT NULL, UNIQUE(device_id, path)
);
CREATE TABLE IF NOT EXISTS activity (
 id TEXT PRIMARY KEY, kind TEXT NOT NULL, target_id TEXT NOT NULL, label TEXT NOT NULL,
 path TEXT NOT NULL, occurred_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS activity_time ON activity(occurred_at);
CREATE TABLE IF NOT EXISTS preferences (
 id INTEGER PRIMARY KEY CHECK(id=1), theme TEXT NOT NULL DEFAULT 'dark', compact INTEGER NOT NULL DEFAULT 1,
 terminal_font INTEGER NOT NULL DEFAULT 13, clip_minutes INTEGER NOT NULL DEFAULT 60
);
INSERT OR IGNORE INTO preferences(id) VALUES (1);
CREATE TABLE IF NOT EXISTS browser_preferences (
 id INTEGER PRIMARY KEY CHECK(id=1), mode TEXT NOT NULL DEFAULT 'SERVER',
 device_id TEXT NOT NULL DEFAULT '', debug_port INTEGER NOT NULL DEFAULT 9222
);
INSERT OR IGNORE INTO browser_preferences(id) VALUES (1);
CREATE TABLE IF NOT EXISTS workspace_tabs (
 id TEXT PRIMARY KEY, kind TEXT NOT NULL, target_id TEXT NOT NULL, path TEXT NOT NULL,
 title TEXT NOT NULL, pinned INTEGER NOT NULL, position INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS calendar_events (
 id TEXT PRIMARY KEY, title TEXT NOT NULL, starts_at TEXT NOT NULL, ends_at TEXT NOT NULL,
 all_day INTEGER NOT NULL, location TEXT NOT NULL, notes TEXT NOT NULL, color TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS calendar_range ON calendar_events(starts_at,ends_at);
CREATE TABLE IF NOT EXISTS timetable_terms (
 id TEXT PRIMARY KEY, name TEXT NOT NULL, starts_on TEXT NOT NULL, ends_on TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS timetable_courses (
 id TEXT PRIMARY KEY, term_id TEXT NOT NULL REFERENCES timetable_terms(id) ON DELETE CASCADE,
 title TEXT NOT NULL, professor TEXT NOT NULL, location TEXT NOT NULL, credits INTEGER NOT NULL,
 color TEXT NOT NULL, notes TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS courses_term ON timetable_courses(term_id);
CREATE TABLE IF NOT EXISTS timetable_meetings (
 course_id TEXT NOT NULL REFERENCES timetable_courses(id) ON DELETE CASCADE,
 day INTEGER NOT NULL CHECK(day BETWEEN 1 AND 7), starts_at TEXT NOT NULL, ends_at TEXT NOT NULL,
 PRIMARY KEY(course_id,day,starts_at)
);
PRAGMA user_version=5;
