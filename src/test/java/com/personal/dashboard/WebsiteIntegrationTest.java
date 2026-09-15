package com.personal.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Exercises rendered forms and security filters together, plus the real SQLite driver. */
@SpringBootTest(
    properties = {
      "SESSION_COOKIE_SECURE=false",
      "workspace.root=./target/test-files",
      "workspace.key-path=./target/test-credential.key"
    })
@AutoConfigureMockMvc(
    printOnlyOnFailure = false,
    print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class WebsiteIntegrationTest {

  private static final String TEST_PASSWORD = UUID.randomUUID().toString();

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;

  @DynamicPropertySource
  static void testConfiguration(DynamicPropertyRegistry registry) {
    registry.add("DASHBOARD_AUTH_ID", () -> "test-owner");
    registry.add("DASHBOARD_AUTH_PASSWORD", () -> TEST_PASSWORD);
    registry.add("DASHBOARD_DB_PATH", () -> "./target/test-dashboard.db");
  }

  @Test
  void loginAndAssetsArePublicAndFormHasCsrf() throws Exception {
    String html =
        mvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var document = org.jsoup.Jsoup.parse(html);
    assertThat(document.selectFirst("form[action=/login] input[name=_csrf]").val()).isNotBlank();
    assertThat(document.selectFirst("input[name=password]").hasAttr("value")).isFalse();
    mvc.perform(get("/css/app.css")).andExpect(status().isOk());
    mvc.perform(get("/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void anonymousVisitorMustLogIn() throws Exception {
    mvc.perform(get("/")).andExpect(status().isFound()).andExpect(redirectedUrlPattern("**/login"));
  }

  @Test
  void wrongPasswordDoesNotAuthenticate() throws Exception {
    mvc.perform(
            post("/login").with(csrf()).param("id", "test-owner").param("password", "incorrect"))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/login?error"));
  }

  @Test
  void unknownAccountDoesNotAuthenticate() throws Exception {
    mvc.perform(
            post("/login")
                .with(csrf())
                .param("id", "another-user")
                .param("password", TEST_PASSWORD))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/login?error"));
  }

  @Test
  void loginAndLogoutRequireCsrf() throws Exception {
    mvc.perform(post("/login").param("id", "test-owner").param("password", TEST_PASSWORD))
        .andExpect(status().isForbidden());
    mvc.perform(post("/logout").with(user("test-owner").roles("OWNER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void validLoginRotatesSessionAndLogoutInvalidatesIt() throws Exception {
    MockHttpSession originalSession =
        (MockHttpSession) mvc.perform(get("/login")).andReturn().getRequest().getSession(false);
    String originalSessionId = originalSession.getId();
    MvcResult login =
        mvc.perform(
                post("/login")
                    .session(originalSession)
                    .with(csrf())
                    .param("id", "test-owner")
                    .param("password", TEST_PASSWORD))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/"))
            .andReturn();
    MockHttpSession authenticatedSession = (MockHttpSession) login.getRequest().getSession(false);
    assertThat(authenticatedSession.getId()).isNotEqualTo(originalSessionId);
    String homeHtml =
        mvc.perform(get("/").session(authenticatedSession))
            .andExpect(status().isOk())
            .andExpect(view().name("home"))
            .andExpect(model().attribute("accountId", "test-owner"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(
            org.jsoup.Jsoup.parse(homeHtml)
                .selectFirst("form[action=/logout] input[name=_csrf]")
                .val())
        .isNotBlank();
    mvc.perform(post("/logout").session(authenticatedSession).with(csrf()))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/login?logout"));
    assertThat(authenticatedSession.isInvalid()).isTrue();
    mvc.perform(get("/")).andExpect(status().isFound());
  }

  @Test
  void authenticatedUserWithoutOwnerRoleIsForbidden() throws Exception {
    mvc.perform(get("/").with(user("guest").roles("GUEST"))).andExpect(status().isForbidden());
  }

  @Test
  void sqliteConnectionUsesFileAndEnforcesForeignKeys() throws Exception {
    try (Connection connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      try (var result = statement.executeQuery("PRAGMA foreign_keys")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(1);
      }
      statement.execute("CREATE TABLE IF NOT EXISTS connection_probe (id INTEGER PRIMARY KEY)");
      statement.execute("DROP TABLE connection_probe");
    }
    assertThat(Files.exists(Path.of("target/test-dashboard.db"))).isTrue();
  }
}
