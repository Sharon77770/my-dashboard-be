package com.personal.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.assistant.service.AssistantMcpService;
import com.personal.dashboard.services.dto.ServiceDto;
import com.personal.dashboard.services.dto.ServiceOnboardingDto;
import com.personal.dashboard.services.service.ServiceCatalogService;
import com.personal.dashboard.services.service.ServiceOnboardingService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises catalog persistence, resource references, health and MCP with the real SQLite schema.
 */
@SpringBootTest(
    properties = {
      "DASHBOARD_AUTH_ID=services-test", "DASHBOARD_AUTH_PASSWORD=services-test-only",
      "workspace.root=./target/services-files", "workspace.key-path=./target/services-key",
      "DASHBOARD_DB_PATH=./target/services-test.db"
    })
@AutoConfigureMockMvc
class ServiceCatalogIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired AssistantMcpService mcp;
  @Autowired ServiceOnboardingService onboarding;
  @Autowired ServiceCatalogService catalogServices;

  @Test
  @WithMockUser(username = "owner", roles = "OWNER")
  void runtimeRoutesRequireBoundContainerAndCsrf() throws Exception {
    String id =
        catalogServices
            .save(null, new ServiceDto.Request("Runtime fixture", "server", "Development", ""))
            .id();
    String resourceId =
        catalogServices.bind(id, new ServiceDto.ResourceRequest("DEVICE", "local", "", "")).id();
    String path = "/api/v1/services/" + id;
    String resourcePath = path + "/resources/" + resourceId;

    mvc.perform(get(path + "/runtime")).andExpect(status().isOk());
    mvc.perform(get(resourcePath + "/logs")).andExpect(status().isBadRequest());
    mvc.perform(get(resourcePath + "/log-history")).andExpect(status().isBadRequest());
    mvc.perform(
            get(resourcePath + "/log-history")
                .param("since", "2026-09-28T00:00:00Z")
                .param("until", "2026-10-05T00:00:00Z"))
        .andExpect(status().isBadRequest());
    assertThat(mcp.tools().stream().map(tool -> tool.get("name")))
        .contains("get_service_logs", "get_service_runtime");
    mvc.perform(
            post(resourcePath + "/actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"restart\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(resourcePath + "/actions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"restart\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(username = "owner", roles = "OWNER")
  void failedAssistantBindingRollsBackServiceAndEarlierBinding() {
    long before = jdbc.queryForObject("SELECT COUNT(*) FROM services", Long.class);
    assertThat(
            org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () ->
                    catalogServices.applyAssistantDraft(
                        null,
                        0,
                        java.util.Set.of(),
                        new ServiceDto.Request("Rollback Fixture", "layers", "Development", ""),
                        List.of(
                            new ServiceDto.ResourceRequest("DEVICE", "local", "", ""),
                            new ServiceDto.ResourceRequest(
                                "TELEMETRY", "missing-service", "", "")))))
        .isNotNull();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM services", Long.class)).isEqualTo(before);
  }

  @Test
  @WithMockUser(username = "owner", roles = "OWNER")
  void assistantDraftRemovesOnlyReviewedExistingBinding() throws Exception {
    String id =
        catalogServices
            .save(null, new ServiceDto.Request("Fixture API", "server", "Development", "test"))
            .id();
    catalogServices.bind(id, new ServiceDto.ResourceRequest("DEVICE", "local", "", ""));
    catalogServices.bind(
        id, new ServiceDto.ResourceRequest("ENDPOINT", "https://example.com/api", "", ""));
    onboarding.discover("existing-service-thread", "Fixture API");
    var draft =
        onboarding.create(
            new ServiceOnboardingDto.DraftRequest(
                "existing-service-thread", id, "Fixture API", "test", "Development", null));
    var changed =
        onboarding.update(
            draft.id(),
            new ServiceOnboardingDto.DraftUpdate(
                draft.revision(),
                null,
                null,
                null,
                List.of(new ServiceDto.ResourceRequest("DEVICE", "local", "", ""))));
    onboarding.approve(changed.id(), changed.revision());
    onboarding.commit(changed.id(), changed.revision());
    assertThat(catalogServices.resources(id))
        .extracting(ServiceDto.Resource::type)
        .containsExactly("DEVICE");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_activity WHERE service_id=? AND source='ASSISTANT' AND type='RESOURCE_REMOVED'",
                Integer.class,
                id))
        .isEqualTo(1);
  }

  @Test
  @WithMockUser(username = "owner", roles = "OWNER")
  void assistantDraftRequiresOwnerCsrfAndExplicitBrowserApproval() throws Exception {
    String thread = "onboarding-integration";
    onboarding.discover(thread, "Dashboard Server");
    var draft =
        onboarding.create(
            new ServiceOnboardingDto.DraftRequest(
                thread,
                null,
                "Onboarded Fixture",
                "",
                "Development",
                List.of(
                    new ServiceDto.ResourceRequest("DEVICE", "local", "", "Dashboard Server"))));
    String path = "/api/v1/assistant/service-drafts/" + draft.id();
    String body = json.writeValueAsString(Map.of("revision", draft.revision()));
    mvc.perform(get(path)).andExpect(status().isOk());
    mvc.perform(
            post(path + "/commit")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(path + "/approve")
                .with(user("owner").roles("OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(path + "/approve")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
    String created =
        mvc.perform(
                post(path + "/commit")
                    .with(user("owner").roles("OWNER"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(created).path("id").asText();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_resources WHERE service_id=?", Integer.class, id))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_activity WHERE service_id=? AND source='ASSISTANT'",
                Integer.class,
                id))
        .isEqualTo(2);
  }

  private JsonNode create() throws Exception {
    String result =
        mvc.perform(
                post("/api/v1/services")
                    .with(user("owner").roles("OWNER"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "name",
                                "Fixture API",
                                "icon",
                                "server",
                                "environment",
                                "Development",
                                "description",
                                "test"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(result);
  }

  @Test
  void crudBindingHealthContextActivityAndAuthorization() throws Exception {
    mvc.perform(get("/api/v1/services")).andExpect(status().isUnauthorized());
    JsonNode service = create();
    String id = service.path("id").asText();
    mvc.perform(get("/api/v1/services/" + id).with(user("owner").roles("OWNER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Fixture API"));
    mvc.perform(
            put("/api/v1/services/" + id)
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Renamed API\",\"icon\":\"server\",\"environment\":\"Production\"}"))
        .andExpect(status().isOk());
    String device = "{\"type\":\"DEVICE\",\"reference\":\"local\"}";
    mvc.perform(
            post("/api/v1/services/" + id + "/resources")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(device))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/services/" + id + "/resources")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(device))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/v1/services/" + id + "/health").with(user("owner").roles("OWNER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("HEALTHY"));
    mvc.perform(get("/api/v1/services/" + id + "/context").with(user("owner").roles("OWNER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.service.name").value("Renamed API"));
    mvc.perform(get("/api/v1/services/" + id + "/activity").with(user("owner").roles("OWNER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].type").value("RESOURCE_BOUND"));
    JsonNode resources =
        json.readTree(
            mvc.perform(
                    get("/api/v1/services/" + id + "/resources").with(user("owner").roles("OWNER")))
                .andReturn()
                .getResponse()
                .getContentAsString());
    mvc.perform(
            delete("/api/v1/services/" + id + "/resources/" + resources.get(0).path("id").asText())
                .with(user("owner").roles("OWNER"))
                .with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(delete("/api/v1/services/" + id).with(user("owner").roles("OWNER")).with(csrf()))
        .andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_resources WHERE service_id=?", Integer.class, id))
        .isZero();
  }

  @Test
  @WithMockUser(username = "owner", roles = "OWNER")
  void mcpToolsShareServiceContext() throws Exception {
    JsonNode service = create();
    String id = service.path("id").asText();
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "owner",
                "",
                java.util.List.of(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_OWNER"))));
    assertThat(mcp.tools().stream().map(tool -> tool.get("name")))
        .contains("list_services", "get_service", "get_service_context", "get_service_health");
    assertThat(mcp.call("get_service_context", json.readTree("{\"id\":\"" + id + "\"}")))
        .containsKey("context");
  }

  @Test
  void deletedTelemetryIsReportedAsOrphanWithoutDeletingService() throws Exception {
    String id = create().path("id").asText();
    String telemetryJson =
        mvc.perform(
                post("/api/v1/telemetry/services")
                    .with(user("owner").roles("OWNER"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Fixture telemetry\",\"serviceType\":\"Backend API\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String telemetryId = json.readTree(telemetryJson).path("serviceId").asText();
    mvc.perform(
            post("/api/v1/services/" + id + "/resources")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(Map.of("type", "TELEMETRY", "reference", telemetryId))))
        .andExpect(status().isCreated());
    jdbc.update("DELETE FROM telemetry_services WHERE id=?", telemetryId);
    mvc.perform(get("/api/v1/services/" + id + "/resources").with(user("owner").roles("OWNER")))
        .andExpect(jsonPath("$[0].orphaned").value(true));
    mvc.perform(get("/api/v1/services/" + id + "/health").with(user("owner").roles("OWNER")))
        .andExpect(jsonPath("$.state").value("UNKNOWN"));
  }
}
