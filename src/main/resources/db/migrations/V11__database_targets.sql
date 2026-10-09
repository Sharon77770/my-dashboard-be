ALTER TABLE database_connections ADD COLUMN target_mode TEXT NOT NULL DEFAULT 'DIRECT';
ALTER TABLE database_connections ADD COLUMN device_id TEXT NOT NULL DEFAULT '';
ALTER TABLE database_connections ADD COLUMN container_id TEXT NOT NULL DEFAULT '';
