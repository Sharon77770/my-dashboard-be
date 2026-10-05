package com.personal.dashboard.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.services.adapter.ServiceLogAdapter;
import com.personal.dashboard.services.dto.ServiceDto;
import com.personal.dashboard.services.service.ServiceCatalogService;
import com.personal.dashboard.services.service.ServiceLogService;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Invalid periods and unbound resources must fail before any SSH command is issued. */
class ServiceLogServiceTest {
  private final ServiceCatalogService services = mock(ServiceCatalogService.class);
  private final CatalogService catalog = mock(CatalogService.class);
  private final ServiceLogAdapter adapter = mock(ServiceLogAdapter.class);
  private final ServiceLogService logs = new ServiceLogService(services, catalog, adapter);
  private final String start = "2026-09-28T00:00:00+09:00";
  private final String end = "2026-10-05T00:00:00+09:00";

  @Test
  void rejectsInvalidAndUnboundedDates() {
    for (String since : List.of("2026-09-28", "2026-09-28;id", "2026-01-01T00:00:00Z", end))
      assertThrows(
          WorkspaceException.class, () -> logs.read("service", "resource", since, end, "errors"));
    assertThrows(
        WorkspaceException.class, () -> logs.read("service", "resource", start, end, "errors;id"));
    verifyNoInteractions(adapter, catalog, services);
  }

  @Test
  void refusesMissingWrongTypeAndOrphanedBindings() {
    when(services.resources("service")).thenReturn(List.of());
    assertThrows(
        WorkspaceException.class, () -> logs.read("service", "resource", start, end, null));
    for (var resource :
        List.of(
            new ServiceDto.Resource("resource", "service", "DEVICE", "server", "", "", 1, false),
            new ServiceDto.Resource(
                "resource", "service", "DOCKER_CONTAINER", "api", "server", "", 1, true))) {
      when(services.resources("service")).thenReturn(List.of(resource));
      assertThrows(
          WorkspaceException.class, () -> logs.read("service", "resource", start, end, "errors"));
    }
    verifyNoInteractions(adapter, catalog);
  }
}
