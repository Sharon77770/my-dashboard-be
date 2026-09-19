ALTER TABLE devices ADD COLUMN jump_device_ids TEXT NOT NULL DEFAULT '';
PRAGMA user_version=5;
