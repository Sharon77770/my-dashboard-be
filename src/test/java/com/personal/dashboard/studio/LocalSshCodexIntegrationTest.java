package com.personal.dashboard.studio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.assistant.service.AssistantMcpService;
import com.personal.dashboard.assistant.service.McpAccess;
import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.catalog.repository.CatalogRepository;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.integration.CommandAdapter;
import com.personal.dashboard.global.integration.DeviceNetworkAdapter;
import com.personal.dashboard.global.integration.SshAdapter;
import com.personal.dashboard.global.security.CredentialVault;
import com.personal.dashboard.services.adapter.ServiceLogAdapter;
import com.personal.dashboard.services.dto.ServiceDto;
import com.personal.dashboard.services.service.ServiceCatalogService;
import com.personal.dashboard.services.service.ServiceLogService;
import com.personal.dashboard.studio.adapter.StudioAdapter;
import com.personal.dashboard.studio.dto.AssistantDto;
import com.personal.dashboard.studio.dto.StudioDto;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

/** Opt-in real loopback SSH transport, pinned host key, remote bridge and Docker daemon reads. */
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_SSH_TEST", matches = "true")
class LocalSshCodexIntegrationTest {
  @TempDir Path temporary;
  private final ObjectMapper json = new ObjectMapper();
  private SshAdapter ssh;
  private DeviceRecord device;

  @BeforeEach
  void connectToLocalSsh() throws Exception {
    var vault = new CredentialVault(temporary.resolve("key").toString());
    ssh = new SshAdapter(vault, new DeviceNetworkAdapter(), mock(CatalogRepository.class));
    device =
        new DeviceRecord(
            "ssh-fixture",
            "SSH fixture",
            "127.0.0.1",
            22,
            "codexcheck",
            vault.encrypt(System.getenv("LOCAL_SSH_PASSWORD")),
            System.getenv("LOCAL_SSH_FINGERPRINT"),
            "/home/codexcheck",
            "NONE",
            0,
            "",
            "",
            "",
            "",
            false);
  }

  @Test
  void remoteCodexNullableCommandEventContinuesThroughApprovalAndHistory() throws Exception {
    var adapter = new StudioAdapter(ssh, json, new McpAccess(new MockEnvironment()));
    var execution = new StudioAdapter.Execution();
    var messages = new ArrayList<StudioAdapter.Message>();
    var args =
        json.readValue(
            "{\"prompt\":\"Inspect service logs\",\"mode\":\"read-only\"}", StudioDto.Args.class);
    try {
      adapter.execute(
          device,
          new StudioDto.Request(device.id(), device.rootPath(), "codex-run", args),
          execution,
          message -> {
            messages.add(message);
            if (message.assistant() != null && message.assistant().interaction() != null)
              adapter.control(
                  execution,
                  new AssistantDto.Control(
                      "approval", message.assistant().interaction().id(), "decline", null, null));
          });
    } finally {
      execution.cancel();
    }
    assertTrue(messages.stream().noneMatch(message -> message.error() != null));
    assertTrue(
        messages.stream()
            .anyMatch(
                message ->
                    message.assistant() != null
                        && message.assistant().item() != null
                        && "streamed output".equals(message.assistant().item().output())));
    assertEquals("completed", messages.getLast().result().assistant().status());
  }

  @Test
  void permissionsAndUsageRoundTripOverLocalSshForEditorAndDevice() throws Exception {
    var adapter = new StudioAdapter(ssh, json, new McpAccess(new MockEnvironment()));
    for (boolean deviceCodex : List.of(false, true)) {
      for (String mode : List.of("read-only", "workspace-write", "danger-full-access")) {
        var args =
            json.readValue(
                json.writeValueAsString(
                    Map.of(
                        "prompt",
                        "Inspect without command approval",
                        "threadId",
                        "thread-1",
                        "mode",
                        mode,
                        "approval",
                        "never")),
                StudioDto.Args.class);
        var messages = new ArrayList<StudioAdapter.Message>();
        var execution = new StudioAdapter.Execution();
        var request = new StudioDto.Request(device.id(), device.rootPath(), "codex-run", args);
        try {
          if (deviceCodex) adapter.executeDeviceCodex(device, request, execution, messages::add);
          else adapter.execute(device, request, execution, messages::add);
        } finally {
          execution.cancel();
        }
        assertTrue(messages.stream().noneMatch(m -> m.error() != null), messages.toString());
        assertTrue(
            messages.stream()
                .noneMatch(m -> m.assistant() != null && m.assistant().interaction() != null));
        assertTrue(
            messages.stream()
                .anyMatch(
                    m ->
                        m.assistant() != null
                            && m.assistant().usage() != null
                            && m.assistant().usage().totalTokens() == 100));
        assertEquals("completed", messages.getLast().result().assistant().status());
      }
      var messages = new ArrayList<StudioAdapter.Message>();
      var execution = new StudioAdapter.Execution();
      var request =
          new StudioDto.Request(device.id(), device.rootPath(), "codex-rate-limits", null);
      try {
        if (deviceCodex) adapter.executeDeviceCodex(device, request, execution, messages::add);
        else adapter.execute(device, request, execution, messages::add);
      } finally {
        execution.cancel();
      }
      var limits = messages.getLast().result().assistant().rateLimits();
      assertEquals(2, limits.size());
      assertEquals(25.0, limits.getFirst().usedPercent());
      assertEquals(300L, limits.getFirst().windowDurationMins());
    }
  }

  @Test
  void remoteAccountShowsSshAccountEmail() throws Exception {
    var adapter = new StudioAdapter(ssh, json, new McpAccess(new MockEnvironment()));
    var execution = new StudioAdapter.Execution();
    var messages = new ArrayList<StudioAdapter.Message>();
    try {
      adapter.execute(
          device,
          new StudioDto.Request(device.id(), device.rootPath(), "codex-account", null),
          execution,
          messages::add);
    } finally {
      execution.cancel();
    }
    assertEquals("ssh-fixture@example.com", messages.getLast().result().assistant().email());
    assertFalse(json.writeValueAsString(messages).contains("never-project"));
  }

  @Test
  void deviceCodexUsesIsolatedHomeAndApprovalOverRealSsh() throws Exception {
    var adapter = new StudioAdapter(ssh, json, new McpAccess(new MockEnvironment()));
    var execution = new StudioAdapter.Execution();
    var messages = new ArrayList<StudioAdapter.Message>();
    var args =
        json.readValue(
            "{\"prompt\":\"Inspect this device\",\"mode\":\"read-only\"}", StudioDto.Args.class);
    try {
      adapter.executeDeviceCodex(
          device,
          new StudioDto.Request(device.id(), device.rootPath(), "codex-run", args),
          execution,
          message -> {
            messages.add(message);
            if (message.assistant() != null && message.assistant().interaction() != null)
              adapter.control(
                  execution,
                  new AssistantDto.Control(
                      "approval", message.assistant().interaction().id(), "decline", null, null));
          });
    } finally {
      execution.cancel();
    }
    assertTrue(messages.stream().noneMatch(message -> message.error() != null));
    assertEquals("completed", messages.getLast().result().assistant().status());
    var proof = new CommandAdapter(ssh).execute(device, "cat /home/codexcheck/device-codex-proof");
    assertTrue(proof.contains("/device-codex/"));
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "RUN_LOCAL_SSH_INSTALL", matches = "true")
  void installsActualCliAndReadsIsolatedAccountOverLocalSsh() throws Exception {
    var installDevice =
        new DeviceRecord(
            "install-fixture",
            "Install fixture",
            "127.0.0.1",
            22,
            "codexinstall",
            device.passwordCipher(),
            device.fingerprint(),
            "/home/codexinstall",
            "NONE",
            0,
            "",
            "",
            "",
            "",
            false);
    var adapter = new StudioAdapter(ssh, json, new McpAccess(new MockEnvironment()));
    var execution = new StudioAdapter.Execution();
    var messages = new ArrayList<StudioAdapter.Message>();
    try {
      adapter.executeDeviceCodex(
          installDevice,
          new StudioDto.Request(installDevice.id(), installDevice.rootPath(), "setup", null),
          execution,
          messages::add);
    } finally {
      execution.cancel();
    }
    assertTrue(
        messages.stream().noneMatch(message -> message.error() != null), () -> messages.toString());
    assertNotNull(messages.getLast().result());
    assertTrue(messages.getLast().result().codex().startsWith("codex-cli "));
    System.out.println("Local SSH installed: " + messages.getLast().result().codex());
    messages.clear();
    var accountExecution = new StudioAdapter.Execution();
    try {
      adapter.executeDeviceCodex(
          installDevice,
          new StudioDto.Request(
              installDevice.id(), installDevice.rootPath(), "codex-account", null),
          accountExecution,
          messages::add);
    } finally {
      accountExecution.cancel();
    }
    assertTrue(
        messages.stream().noneMatch(message -> message.error() != null), () -> messages.toString());
    assertEquals(false, messages.getLast().result().assistant().authenticated());
  }

  @Test
  void realDockerLogsOverSshFindEarly500BeyondRecent200Lines() throws Exception {
    var adapter = new ServiceLogAdapter(new CommandAdapter(ssh), json);
    var since = Instant.now().minus(8, ChronoUnit.DAYS);
    var until = Instant.now().plusSeconds(1);
    var catalog = mock(CatalogService.class);
    when(catalog.requireDevice(device.id())).thenReturn(device);
    var services = mock(ServiceCatalogService.class);
    when(services.resources("service"))
        .thenReturn(
            List.of(
                new ServiceDto.Resource(
                    "resource",
                    "service",
                    "DOCKER_CONTAINER",
                    "dashboard-codex-log-fixture",
                    device.id(),
                    "API",
                    1,
                    false)));
    var logs = new ServiceLogService(services, catalog, adapter);
    var mcp =
        new AssistantMcpService(
            mock(com.personal.dashboard.planner.service.PlannerService.class),
            catalog,
            mock(com.personal.dashboard.notes.service.NoteService.class),
            mock(com.personal.dashboard.notes.service.NoteMarkdownConverter.class),
            mock(com.personal.dashboard.assistant.service.AssistantEvents.class),
            mock(jakarta.validation.Validator.class),
            json,
            mock(com.personal.dashboard.github.service.GithubService.class),
            services,
            mock(com.personal.dashboard.database.service.DatabaseStudioService.class),
            mock(com.personal.dashboard.services.service.ServiceOnboardingService.class),
            mock(com.personal.dashboard.assistant.service.WorkspaceMemoryService.class),
            logs,
            mock(com.personal.dashboard.services.service.ServiceRuntimeService.class),
            mock(com.personal.dashboard.communication.service.CommunicationMcpTools.class));
    var registered =
        mcp.tools().stream()
            .filter(tool -> tool.get("name").equals("get_service_logs"))
            .findFirst()
            .orElseThrow();
    assertEquals(true, ((Map<?, ?>) registered.get("annotations")).get("readOnlyHint"));
    var result =
        (ServiceDto.LogHistory)
            mcp.call(
                    "get_service_logs",
                    json.valueToTree(
                        Map.of(
                            "id",
                            "service",
                            "resourceId",
                            "resource",
                            "since",
                            since.toString(),
                            "until",
                            until.toString())))
                .get("logs");
    assertTrue(result.scanComplete());
    assertFalse(result.truncated());
    assertTrue(result.scannedLines() > 300);
    assertTrue(result.output().contains("500 Internal Server Error"));
    assertTrue(result.output().contains("Traceback"));
    assertFalse(result.output().contains("fixture-private-password"));
    assertTrue(result.firstTimestamp().startsWith("20"));
    var empty =
        adapter.read(device, "dashboard-codex-log-fixture", since, since.plusSeconds(1), "errors");
    assertEquals(0, empty.scannedLines());
    assertTrue(empty.scanComplete());
    assertThrows(
        WorkspaceException.class,
        () -> adapter.read(device, "dashboard-codex-log-does-not-exist", since, until, "errors"));
  }
}
