ALTER TABLE devices ADD COLUMN network_mode TEXT NOT NULL DEFAULT 'DIRECT'
 CHECK(network_mode IN ('DIRECT','TAILSCALE'));
PRAGMA user_version=3;
