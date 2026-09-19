package com.personal.dashboard;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies authenticated persistence and real file operations, including adversarial boundary
 * inputs.
 */
@SpringBootTest(
    properties = {
      "workspace.root=./target/workspace-files",
      "workspace.key-path=./target/workspace-credential.key",
      "DASHBOARD_DB_PATH=./target/workspace-test.db"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class WorkspaceIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;

  @Test
  @org.springframework.transaction.annotation.Transactional
  void removedDesktopCannotReappearFromSavedStateOrStartThroughApi() throws Exception {
    jdbc.update(
        "INSERT INTO activity VALUES (?,?,?,?,?,?)",
        "removed-app",
        "DESKTOP",
        "kakaotalk",
        "old app",
        "",
        1L);
    jdbc.update(
        "INSERT INTO workspace_tabs VALUES (?,?,?,?,?,?,?)",
        "removed-tab",
        "DESKTOP",
        "kakaotalk",
        "",
        "old app",
        false,
        99);
    String state =
        mvc.perform(get("/api/v1/workspace").with(user("owner").roles("OWNER")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(state).doesNotContain("kakaotalk", "DESKTOP");
    mvc.perform(
            post("/api/v1/sessions")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"kind\":\"DESKTOP\",\"targetId\":\"kakaotalk\",\"width\":1280,\"height\":800}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void desktopSetupRequiresOwnerAndCsrfAndRejectsLocalInstallation() throws Exception {
    mvc.perform(get("/api/v1/devices/local/remote-setup")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/devices/local/remote-setup").with(user("owner").roles("OWNER")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/devices/local/remote-setup")
                .with(user("owner").roles("OWNER"))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/devices/local/remote-setup").with(user("owner").roles("OWNER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("IDLE"));
  }

  @Test
  void deviceNetworkOptionPersistsAndOmittedUpdatesPreserveIt() throws Exception {
    var body = new LinkedHashMap<String, Object>();
    body.put("name", "Tailnet fixture");
    body.put("host", "100.64.1.2");
    body.put("sshPort", 22);
    body.put("username", "tester");
    body.put("fingerprint", "");
    body.put("rootPath", "/home/tester");
    body.put("remoteProtocol", "NONE");
    body.put("remotePort", 3389);
    body.put("networkMode", "TAILSCALE");
    body.put("jumpDeviceIds", List.of());
    String response =
        mvc.perform(
                post("/api/v1/devices")
                    .with(user("owner").roles("OWNER"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.networkMode").value("TAILSCALE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = mapper.readTree(response).get("id").asText();
    body.remove("networkMode");
    mvc.perform(
            put("/api/v1/devices/" + id)
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.networkMode").value("TAILSCALE"));
    assertThat(jdbc.queryForObject("SELECT network_mode FROM devices WHERE id=?", String.class, id))
        .isEqualTo("TAILSCALE");
    assertThat(
            jdbc.queryForObject("SELECT jump_device_ids FROM devices WHERE id=?", String.class, id))
        .isEqualTo("");
    body.put("networkMode", "PROXY_COMMAND");
    mvc.perform(
            put("/api/v1/devices/" + id)
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
    mvc.perform(delete("/api/v1/devices/" + id).with(user("owner").roles("OWNER")).with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  void studioRequiresLoginOwnerCsrfAndKnownRemoteActions() throws Exception {
    mvc.perform(get("/api/v1/studio/jobs/missing")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/studio/jobs")
                .with(user("owner").roles("OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/studio/jobs")
                .with(user("guest").roles("GUEST"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/studio/jobs")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceId\":\"local\",\"root\":\"/\",\"action\":\"list\",\"args\":{}}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.id").isNotEmpty());
    mvc.perform(
            post("/api/v1/studio/jobs")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceId\":\"local\",\"root\":\"/\",\"action\":\"sh\",\"args\":{}}"))
        .andExpect(status().isBadRequest());
  }

  @DynamicPropertySource
  static void credentials(DynamicPropertyRegistry registry) {
    registry.add("DASHBOARD_AUTH_ID", () -> "workspace-owner");
    registry.add("DASHBOARD_AUTH_PASSWORD", () -> UUID.randomUUID().toString());
  }

  private String json(Object value) throws Exception {
    return mapper.writeValueAsString(value);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
    return user("workspace-owner").roles("OWNER");
  }

  @Test
  void apiRejectsAnonymousWrongRoleAndMissingCsrf() throws Exception {
    mvc.perform(get("/api/v1/workspace")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/workspace").with(user("guest").roles("GUEST")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/clips")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"note\",\"minutes\":1}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void sshEnrollmentRequiresOwnerCsrfAndValidCommand() throws Exception {
    String body = json(Map.of("command", "ssh user@host;id", "password", "test-only"));
    mvc.perform(
            post("/api/v1/devices/ssh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/devices/ssh")
                .with(user("guest").roles("GUEST"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/devices/ssh")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/devices/ssh")
                .with(owner())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void initialWorkspaceAndThymeleafRenderRealData() throws Exception {
    mvc.perform(get("/api/v1/workspace").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.devices[?(@.id=='local')]").isNotEmpty());
    var result = mvc.perform(get("/").with(owner())).andExpect(status().isOk()).andReturn();
    var document = org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
    assertThat(document.select("[data-view]")).isNotEmpty();
    assertThat(document.select("meta[name=csrf-token]").attr("content")).isNotEmpty();
    assertThat(document.select("[data-action=browser-settings]")).hasSize(2);
  }

  @Test
  void deviceCredentialsAreEncryptedAndNotReturned() throws Exception {
    String password = UUID.randomUUID().toString();
    Map<String, Object> input = new HashMap<>();
    input.put("name", "Test device");
    input.put("host", "127.0.0.1");
    input.put("sshPort", 22);
    input.put("username", "tester");
    input.put("fingerprint", "SHA256:" + "A".repeat(43));
    input.put("password", password);
    input.put("rootPath", "/tmp");
    input.put("remoteProtocol", "NONE");
    input.put("remotePort", 3389);
    input.put("pinned", false);
    String response =
        mvc.perform(
                post("/api/v1/devices")
                    .with(owner())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(input)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.hasPassword").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(response).doesNotContain(password).doesNotContain("passwordCipher");
    String id = mapper.readTree(response).get("id").asText();
    String cipher =
        jdbc.queryForObject("SELECT password_cipher FROM devices WHERE id=?", String.class, id);
    assertThat(cipher).isNotEqualTo(password).isNotEmpty();
    input.put("password", "");
    input.remove("fingerprint");
    input.put("name", "Updated device");
    mvc.perform(
            put("/api/v1/devices/" + id)
                .with(owner())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(input)))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject("SELECT password_cipher FROM devices WHERE id=?", String.class, id))
        .isEqualTo(cipher);
    mvc.perform(delete("/api/v1/devices/" + id).with(owner()).with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  void clipsExpireAndInvalidExpiryIsRejected() throws Exception {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO clips VALUES (?,?,?)", id, "expired", System.currentTimeMillis() - 1000);
    mvc.perform(get("/api/v1/workspace").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clips[?(@.id=='" + id + "')]").isEmpty());
    mvc.perform(
            post("/api/v1/clips")
                .with(owner())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hello\",\"minutes\":0}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/clips")
                .with(owner())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hello\",\"minutes\":5}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.content").value("hello"));
  }

  @Test
  void localFilesUploadDownloadRenameAndDelete() throws Exception {
    String name = "test-" + UUID.randomUUID() + ".txt";
    var file =
        new MockMultipartFile("file", name, "text/plain", "actual server content".getBytes());
    mvc.perform(
            multipart("/api/v1/devices/local/files")
                .file(file)
                .param("path", "/")
                .with(owner())
                .with(csrf()))
        .andExpect(status().isCreated());
    assertThat(Files.readString(Path.of("target/workspace-files", name)))
        .isEqualTo("actual server content");
    mvc.perform(get("/api/v1/devices/local/files/content").param("path", "/" + name).with(owner()))
        .andExpect(status().isOk())
        .andExpect(content().string("actual server content"));
    mvc.perform(
            multipart("/api/v1/devices/local/files")
                .file(file)
                .param("path", "/")
                .with(owner())
                .with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(
            patch("/api/v1/devices/local/files")
                .with(owner())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("path", "/" + name, "name", name + ".renamed"))))
        .andExpect(status().isNoContent());
    mvc.perform(
            delete("/api/v1/devices/local/files")
                .param("path", "/" + name + ".renamed")
                .with(owner())
                .with(csrf()))
        .andExpect(status().isNoContent());
    assertThat(Files.exists(Path.of("target/workspace-files", name + ".renamed"))).isFalse();
  }

  @Test
  void pathsCannotEscapeRootAndRootCannotBeDeleted() throws Exception {
    mvc.perform(get("/api/v1/devices/local/files").param("path", "/../").with(owner()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/devices/local/files/folders")
                .with(owner())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("path", "/", "name", "../outside"))))
        .andExpect(status().isBadRequest());
    mvc.perform(delete("/api/v1/devices/local/files").param("path", "/").with(owner()).with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(delete("/api/v1/devices/local").with(owner()).with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void browserModeIsStoredAndRemoteRequiresVncDevice() throws Exception {
    mvc.perform(
            put("/api/v1/browser-settings")
                .with(owner())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"CLIENT\",\"deviceId\":\"\",\"debugPort\":9222}"))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/workspace").with(owner()))
        .andExpect(jsonPath("$.browserSettings.mode").value("CLIENT"));
    mvc.perform(
            put("/api/v1/browser-settings")
                .with(owner())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"REMOTE\",\"deviceId\":\"local\",\"debugPort\":9222}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void invalidApplicationUrlsCannotBecomeExecutableLinks() throws Exception {
    mvc.perform(
            post("/api/v1/applications")
                .with(owner())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(Map.of("name", "Invalid", "url", "javascript:alert(1)", "pinned", false))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/applications")
                .with(owner())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "name",
                            "Invalid",
                            "url",
                            "https://user:password@example.com",
                            "pinned",
                            false))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void preferencesAndTabsPersistAndSearchFindsRegisteredResources() throws Exception {
    mvc.perform(
            put("/api/v1/preferences")
                .with(owner())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"theme\":\"light\",\"compact\":false,\"terminalFont\":16,\"clipMinutes\":15}"))
        .andExpect(status().isNoContent());
    mvc.perform(
            put("/api/v1/tabs")
                .with(owner())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tabs\":[{\"id\":\"saved\",\"kind\":\"FILES\",\"targetId\":\"local\",\"path\":\"/\",\"title\":\"Files\",\"pinned\":true}]}"))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/workspace").with(owner()))
        .andExpect(jsonPath("$.preferences.theme").value("light"))
        .andExpect(jsonPath("$.tabs[0].id").value("saved"));
    mvc.perform(get("/api/v1/search").param("query", "Dashboard").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].targetId").value("local"));
  }
}
