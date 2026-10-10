package com.personal.dashboard.communication.repository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persistent profile identifiers; browser state lives in its existing volume. */
@Repository
public class BridgeRepository {
  public record ProfileRecord(String id, String provider, String label) {}

  private final JdbcTemplate jdbc;

  public BridgeRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<ProfileRecord> profiles() {
    return jdbc.query(
        "SELECT id,provider,label FROM communication_profiles ORDER BY label",
        (r, i) -> new ProfileRecord(r.getString(1), r.getString(2), r.getString(3)));
  }

  public void save(ProfileRecord profile) {
    jdbc.update(
        "INSERT INTO communication_profiles(id,provider,label) VALUES(?,?,?)",
        profile.id(),
        profile.provider(),
        profile.label());
  }

  public void delete(String id) {
    jdbc.update("DELETE FROM communication_profiles WHERE id=?", id);
  }
}
