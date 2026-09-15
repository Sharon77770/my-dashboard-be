package com.personal.dashboard.global;

import static org.assertj.core.api.Assertions.*;

import com.personal.dashboard.catalog.entity.NetworkMode;
import com.personal.dashboard.catalog.repository.CatalogRepository;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

class DeviceNetworkMigrationTest {
  @TempDir Path directory;

  @Test
  void upgradesExistingDevicesWithoutLosingProfilesAndCanRunTwice() {
    var source = new SQLiteDataSource();
    source.setUrl("jdbc:sqlite:" + directory.resolve("legacy.db"));
    var jdbc = new JdbcTemplate(source);
    jdbc.execute(
        "CREATE TABLE devices(id TEXT PRIMARY KEY,name TEXT,host TEXT,ssh_port INTEGER,username TEXT,password_cipher TEXT,fingerprint TEXT,root_path TEXT,remote_protocol TEXT,remote_port INTEGER,remote_username TEXT,remote_password_cipher TEXT,mac TEXT,broadcast TEXT,pinned INTEGER)");
    jdbc.update(
        "INSERT INTO devices VALUES ('existing','My server','100.64.1.2',22,'tester','encrypted-fixture','pin','/home/tester','NONE',3389,'','','','',1)");
    var migration = new DatabaseInitialization(source);
    migration.afterPropertiesSet();
    migration.afterPropertiesSet();
    var device = new CatalogRepository(jdbc).device("existing").orElseThrow();
    assertThat(device.networkMode()).isEqualTo(NetworkMode.DIRECT);
    assertThat(device.passwordCipher()).isEqualTo("encrypted-fixture");
    assertThat(device.rootPath()).isEqualTo("/home/tester");
    assertThat(device.pinned()).isTrue();
  }
}
