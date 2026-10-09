package com.personal.dashboard.database;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.database.entity.DatabaseConnection;
import com.personal.dashboard.database.repository.DatabaseRepository;
import com.personal.dashboard.global.DatabaseInitialization;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.sqlite.SQLiteDataSource;

class DatabaseTargetMigrationTest {
  @TempDir Path directory;

  @Test
  void upgradesExistingConnectionsWithoutChangingSecretsAndPersistsTargets() {
    var source = new SQLiteDataSource();
    source.setUrl("jdbc:sqlite:" + directory.resolve("legacy.db"));
    new ResourceDatabasePopulator(new ClassPathResource("db/migrations/V8__database_studio.sql"))
        .execute(source);
    var jdbc = new JdbcTemplate(source);
    jdbc.update(
        "INSERT INTO database_connections(id,name,type,database_name,username,credential_cipher,ssl_mode,access_mode,created_at,updated_at) VALUES('legacy','Legacy','POSTGRESQL','studio','studio','encrypted','REQUIRE','READ_ONLY',1,1)");
    new DatabaseInitialization(source).afterPropertiesSet();
    new DatabaseInitialization(source).afterPropertiesSet();
    var repository = new DatabaseRepository(jdbc, new ObjectMapper());
    var legacy = repository.connection("legacy").orElseThrow();
    assertEquals("DIRECT", legacy.targetMode());
    assertEquals("", legacy.deviceId());
    assertEquals("encrypted", legacy.credentialCipher());
    repository.save(
        new DatabaseConnection(
            "remote",
            "Remote",
            "POSTGRESQL",
            "127.0.0.1",
            5432,
            "studio",
            "studio",
            "encrypted",
            "DISABLE",
            "READ_ONLY",
            Map.of(),
            1,
            2,
            "DOCKER",
            "device",
            "container"));
    var saved = repository.connection("remote").orElseThrow();
    assertEquals("DOCKER", saved.targetMode());
    assertEquals("device", saved.deviceId());
    assertEquals("container", saved.containerId());
  }
}
