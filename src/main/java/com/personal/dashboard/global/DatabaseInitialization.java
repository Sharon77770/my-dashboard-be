package com.personal.dashboard.global;

import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/** Applies the idempotent initial schema before metadata services seed the local server profile. */
@Component("workspaceSchema")
public class DatabaseInitialization implements InitializingBean {
  private final DataSource dataSource;

  public DatabaseInitialization(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public void afterPropertiesSet() {
    new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);
    var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    if (jdbc.queryForList("PRAGMA table_info(devices)").stream()
        .noneMatch(column -> "network_mode".equals(column.get("name"))))
      new ResourceDatabasePopulator(new ClassPathResource("db/migrations/V3__device_network.sql"))
          .execute(dataSource);
    new ResourceDatabasePopulator(new ClassPathResource("db/migrations/V4__notes.sql"))
        .execute(dataSource);
    if (jdbc.queryForList("PRAGMA table_info(devices)").stream()
        .noneMatch(column -> "jump_device_ids".equals(column.get("name"))))
      new ResourceDatabasePopulator(
              new ClassPathResource("db/migrations/V5__device_jump_proxy.sql"))
          .execute(dataSource);
  }
}
