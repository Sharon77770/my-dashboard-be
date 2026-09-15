package com.personal.dashboard.catalog.repository;

import com.personal.dashboard.catalog.dto.*;
import com.personal.dashboard.catalog.entity.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Owns SQLite metadata operations; connection secrets remain encrypted at this boundary. */
@Repository
public class CatalogRepository {
  private final JdbcTemplate jdbc;
  private final RowMapper<DeviceRecord> devices =
      (row, index) ->
          new DeviceRecord(
              row.getString("id"),
              row.getString("name"),
              row.getString("host"),
              row.getInt("ssh_port"),
              row.getString("username"),
              row.getString("password_cipher"),
              row.getString("fingerprint"),
              row.getString("root_path"),
              row.getString("remote_protocol"),
              row.getInt("remote_port"),
              row.getString("remote_username"),
              row.getString("remote_password_cipher"),
              row.getString("mac"),
              row.getString("broadcast"),
              row.getBoolean("pinned"),
              NetworkMode.valueOf(row.getString("network_mode")));

  public CatalogRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<DeviceRecord> devices() {
    return jdbc.query("SELECT * FROM devices ORDER BY pinned DESC, name", devices);
  }

  public Optional<DeviceRecord> device(String id) {
    return jdbc.query("SELECT * FROM devices WHERE id=?", devices, id).stream().findFirst();
  }

  public void save(DeviceRecord device) {
    jdbc.update(
        "INSERT INTO devices (id,name,host,ssh_port,username,password_cipher,fingerprint,root_path,remote_protocol,remote_port,remote_username,remote_password_cipher,mac,broadcast,pinned,network_mode) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,host=excluded.host,ssh_port=excluded.ssh_port,username=excluded.username,password_cipher=excluded.password_cipher,fingerprint=excluded.fingerprint,root_path=excluded.root_path,remote_protocol=excluded.remote_protocol,remote_port=excluded.remote_port,remote_username=excluded.remote_username,remote_password_cipher=excluded.remote_password_cipher,mac=excluded.mac,broadcast=excluded.broadcast,pinned=excluded.pinned,network_mode=excluded.network_mode",
        device.id(),
        device.name(),
        device.host(),
        device.sshPort(),
        device.username(),
        device.passwordCipher(),
        device.fingerprint(),
        device.rootPath(),
        device.remoteProtocol(),
        device.remotePort(),
        device.remoteUsername(),
        device.remotePasswordCipher(),
        device.mac(),
        device.broadcast(),
        device.pinned(),
        device.networkMode().name());
  }

  public void deleteDevice(String id) {
    jdbc.update("DELETE FROM devices WHERE id=?", id);
    jdbc.update("DELETE FROM activity WHERE target_id=?", id);
  }

  public List<ApplicationRecord> applications() {
    return jdbc.query(
        "SELECT * FROM applications ORDER BY pinned DESC,name",
        (row, index) ->
            new ApplicationRecord(
                row.getString("id"),
                row.getString("name"),
                row.getString("url"),
                row.getBoolean("pinned")));
  }

  public void save(ApplicationRecord app) {
    jdbc.update(
        "INSERT INTO applications VALUES (?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,url=excluded.url,pinned=excluded.pinned",
        app.id(),
        app.name(),
        app.url(),
        app.pinned());
  }

  public void deleteApplication(String id) {
    jdbc.update("DELETE FROM applications WHERE id=?", id);
    jdbc.update("DELETE FROM activity WHERE target_id=?", id);
  }

  public List<ClipView> clips() {
    jdbc.update("DELETE FROM clips WHERE expires_at<=?", System.currentTimeMillis());
    return jdbc.query(
        "SELECT * FROM clips ORDER BY expires_at DESC",
        (row, index) ->
            new ClipView(row.getString("id"), row.getString("content"), row.getLong("expires_at")));
  }

  public void saveClip(ClipView clip) {
    jdbc.update("INSERT INTO clips VALUES (?,?,?)", clip.id(), clip.content(), clip.expiresAt());
  }

  public void deleteClip(String id) {
    jdbc.update("DELETE FROM clips WHERE id=?", id);
  }

  public List<BookmarkView> bookmarks() {
    return jdbc.query(
        "SELECT * FROM bookmarks ORDER BY path",
        (row, index) ->
            new BookmarkView(
                row.getString("id"), row.getString("device_id"), row.getString("path")));
  }

  public void saveBookmark(BookmarkView bookmark) {
    jdbc.update(
        "INSERT OR IGNORE INTO bookmarks VALUES (?,?,?)",
        bookmark.id(),
        bookmark.deviceId(),
        bookmark.path());
  }

  public void deleteBookmark(String id) {
    jdbc.update("DELETE FROM bookmarks WHERE id=?", id);
  }

  public List<ActivityView> activity() {
    return jdbc.query(
        "SELECT * FROM activity ORDER BY occurred_at DESC LIMIT 100",
        (row, index) ->
            new ActivityView(
                row.getString("id"),
                row.getString("kind"),
                row.getString("target_id"),
                row.getString("label"),
                row.getString("path"),
                row.getLong("occurred_at")));
  }

  public void activity(String kind, String target, String label, String path) {
    jdbc.update("DELETE FROM activity WHERE kind=? AND target_id=? AND path=?", kind, target, path);
    jdbc.update(
        "INSERT INTO activity VALUES (?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        kind,
        target,
        label,
        path,
        System.currentTimeMillis());
    jdbc.update(
        "DELETE FROM activity WHERE id NOT IN (SELECT id FROM activity ORDER BY occurred_at DESC LIMIT 100)");
  }

  public Preferences preferences() {
    return jdbc.queryForObject(
        "SELECT * FROM preferences WHERE id=1",
        (row, index) ->
            new Preferences(
                row.getString("theme"),
                row.getBoolean("compact"),
                row.getInt("terminal_font"),
                row.getInt("clip_minutes")));
  }

  public void preferences(Preferences preferences) {
    jdbc.update(
        "UPDATE preferences SET theme=?,compact=?,terminal_font=?,clip_minutes=? WHERE id=1",
        preferences.theme(),
        preferences.compact(),
        preferences.terminalFont(),
        preferences.clipMinutes());
  }

  public BrowserSettings browserSettings() {
    return jdbc.queryForObject(
        "SELECT * FROM browser_preferences WHERE id=1",
        (row, index) ->
            new BrowserSettings(
                row.getString("mode"), row.getString("device_id"), row.getInt("debug_port")));
  }

  public void browserSettings(BrowserSettings settings) {
    jdbc.update(
        "UPDATE browser_preferences SET mode=?,device_id=?,debug_port=? WHERE id=1",
        settings.mode(),
        settings.deviceId(),
        settings.debugPort());
  }

  public List<TabView> tabs() {
    return jdbc.query(
        "SELECT * FROM workspace_tabs ORDER BY position",
        (row, index) ->
            new TabView(
                row.getString("id"),
                row.getString("kind"),
                row.getString("target_id"),
                row.getString("path"),
                row.getString("title"),
                row.getBoolean("pinned")));
  }

  public void tabs(List<TabRequest> tabs) {
    jdbc.update("DELETE FROM workspace_tabs");
    int position = 0;
    for (TabRequest tab : tabs)
      jdbc.update(
          "INSERT INTO workspace_tabs VALUES (?,?,?,?,?,?,?)",
          tab.id(),
          tab.kind(),
          tab.targetId(),
          tab.path(),
          tab.title(),
          tab.pinned(),
          position++);
  }
}
