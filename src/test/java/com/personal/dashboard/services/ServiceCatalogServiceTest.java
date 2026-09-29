package com.personal.dashboard.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.personal.dashboard.catalog.dto.DeviceStatus;
import com.personal.dashboard.catalog.dto.DeviceView;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.catalog.service.DeviceOperations;
import com.personal.dashboard.database.dto.DatabaseDto;
import com.personal.dashboard.database.service.DatabaseStudioService;
import com.personal.dashboard.github.dto.GithubDto;
import com.personal.dashboard.github.service.GithubService;
import com.personal.dashboard.services.dto.ServiceDto;
import com.personal.dashboard.services.repository.ServiceRepository;
import com.personal.dashboard.services.service.ServiceCatalogService;
import com.personal.dashboard.telemetry.service.TelemetryService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Binding and advisory CI health use the existing GitHub service boundary. */
class ServiceCatalogServiceTest {
  private final ServiceRepository repository = mock(ServiceRepository.class);
  private final CatalogService catalog = mock(CatalogService.class);
  private final DeviceOperations devices = mock(DeviceOperations.class);
  private final TelemetryService telemetry = mock(TelemetryService.class);
  private final GithubService github = mock(GithubService.class);
  private final DatabaseStudioService databases = mock(DatabaseStudioService.class);
  private final ServiceCatalogService service =
      new ServiceCatalogService(repository, catalog, devices, telemetry, github, databases);
  private final ServiceDto.View view =
      new ServiceDto.View("id", "API", "server", "Production", "", 1, 1);

  @Test
  void databaseBindingUsesExistingConnectionAndContextExcludesCredential() {
    var connection =
        new DatabaseDto.ConnectionView(
            "db",
            "Production DB",
            "POSTGRESQL",
            "host",
            5432,
            "production",
            "owner",
            true,
            "REQUIRE",
            "READ_ONLY",
            java.util.Map.of(),
            1,
            1);
    when(repository.service("id")).thenReturn(Optional.of(view));
    when(databases.get("db")).thenReturn(connection);
    when(databases.list()).thenReturn(List.of(connection));
    when(databases.test("db")).thenReturn(new DatabaseDto.TestResult(true, "PostgreSQL", 1, ""));
    when(repository.resources("id"))
        .thenReturn(
            List.of(),
            List.of(new ServiceDto.Resource("binding", "id", "DATABASE", "db", "", "", 1, false)));
    when(repository.activity("id")).thenReturn(List.of());
    service.bind("id", new ServiceDto.ResourceRequest("DATABASE", "db", "", ""));
    var context = service.context("id");
    assertEquals("READ_ONLY", ((java.util.Map<?, ?>) context.databases().get("db")).get("mode"));
    assertFalse(context.databases().toString().contains("credential"));
  }

  @Test
  void deletedDatabaseConnectionLeavesOrphanBinding() {
    when(repository.service("id")).thenReturn(Optional.of(view));
    when(repository.resources("id"))
        .thenReturn(
            List.of(
                new ServiceDto.Resource("binding", "id", "DATABASE", "deleted", "", "", 1, false)));
    when(databases.list()).thenReturn(List.of());
    assertTrue(service.resources("id").getFirst().orphaned());
  }

  @Test
  void githubBindingUsesExistingGithubService() {
    when(repository.service("id")).thenReturn(Optional.of(view));
    service.bind("id", new ServiceDto.ResourceRequest("GITHUB_REPOSITORY", "owner/api", "", ""));
    verify(github).repository("owner/api");
    verify(repository).add(any(ServiceDto.Resource.class));
  }

  @Test
  void dockerBindingChecksExistingDeviceContainerList() {
    when(repository.service("id")).thenReturn(Optional.of(view));
    when(devices.inspect("server", "docker"))
        .thenReturn("CONTAINER ID\tNAMES\tSTATUS\tIMAGE\nabc\tapi\tUp 1 hour\timage");
    service.bind("id", new ServiceDto.ResourceRequest("DOCKER_CONTAINER", "api", "server", ""));
    verify(catalog).requireDevice("server");
    verify(repository).add(any(ServiceDto.Resource.class));
  }

  @Test
  void failedActionIsDegradedButNotDown() {
    when(repository.service("id")).thenReturn(Optional.of(view));
    when(repository.resources("id"))
        .thenReturn(
            List.of(
                new ServiceDto.Resource(
                    "resource", "id", "GITHUB_REPOSITORY", "owner/api", "", "", 1, false)));
    when(github.workflowRuns("owner/api"))
        .thenReturn(
            List.of(
                new GithubDto.WorkflowRun(
                    1,
                    "build",
                    "completed",
                    "failure",
                    "main",
                    "push",
                    "sha",
                    "https://github.com/owner/api/actions/runs/1",
                    "2026-01-01T00:00:00Z",
                    "2026-01-01T00:01:00Z")));
    assertEquals("DEGRADED", service.health("id").state());
  }

  @Test
  void healthyActionDoesNotMaskDownRuntime() {
    when(repository.service("id")).thenReturn(Optional.of(view));
    when(repository.resources("id"))
        .thenReturn(
            List.of(
                new ServiceDto.Resource("device", "id", "DEVICE", "server", "", "", 1, false),
                new ServiceDto.Resource(
                    "repo", "id", "GITHUB_REPOSITORY", "owner/api", "", "", 1, false)));
    DeviceView device = mockDevice("server");
    when(catalog.devices()).thenReturn(List.of(device));
    when(devices.status("server"))
        .thenReturn(new DeviceStatus("UNAVAILABLE", null, null, null, "offline", 1));
    when(github.workflowRuns("owner/api"))
        .thenReturn(
            List.of(
                new GithubDto.WorkflowRun(
                    1,
                    "build",
                    "completed",
                    "success",
                    "main",
                    "push",
                    "sha",
                    "https://github.com/owner/api/actions/runs/1",
                    "2026-01-01T00:00:00Z",
                    "2026-01-01T00:01:00Z")));
    assertEquals("DOWN", service.health("id").state());
  }

  private DeviceView mockDevice(String id) {
    DeviceView view = mock(DeviceView.class);
    when(view.id()).thenReturn(id);
    return view;
  }
}
