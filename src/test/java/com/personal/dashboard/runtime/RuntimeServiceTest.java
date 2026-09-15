package com.personal.dashboard.runtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.runtime.adapter.BrowserAdapter;
import com.personal.dashboard.runtime.dto.SessionRequest;
import com.personal.dashboard.runtime.service.RuntimeService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Prevents cross-login session reuse and leaked connections when close races with connection setup.
 */
class RuntimeServiceTest {
  private RuntimeService service() {
    CatalogService catalog = mock(CatalogService.class);
    when(catalog.requireDevice("local"))
        .thenReturn(
            new DeviceRecord(
                "local",
                "Local",
                "localhost",
                22,
                "",
                "",
                "",
                "/tmp",
                "NONE",
                3389,
                "",
                "",
                "",
                "",
                true));
    return new RuntimeService(catalog, mock(BrowserAdapter.class), "localhost", 9223, 5901);
  }

  @Test
  void removedDesktopKindIsRejected() {
    RuntimeService service = service();
    assertThatThrownBy(
            () -> service.create(new SessionRequest("DESKTOP", "kakaotalk", 800, 600), "owner"))
        .isInstanceOf(WorkspaceException.class);
    service.shutdown();
  }

  @Test
  void handlesAreBoundToTheCreatingLogin() {
    RuntimeService service = service();
    var handle = service.create(new SessionRequest("TERMINAL", "local", 800, 600), "owner-session");
    assertThatThrownBy(() -> service.attach(handle.id(), "another-session"))
        .isInstanceOf(WorkspaceException.class);
    assertThat(service.attach(handle.id(), "owner-session").id).isEqualTo(handle.id());
    assertThatThrownBy(() -> service.attach(handle.id(), "owner-session"))
        .isInstanceOf(WorkspaceException.class);
    service.shutdown();
  }

  @Test
  void lateConnectionIsClosedAfterTabWasDeleted() throws Exception {
    RuntimeService service = service();
    var handle = service.create(new SessionRequest("TERMINAL", "local", 800, 600), "owner-session");
    var runtime = service.attach(handle.id(), "owner-session");
    service.closeOwned(handle.id(), "owner-session");
    AtomicBoolean closed = new AtomicBoolean();
    assertThat(service.bind(runtime, () -> closed.set(true))).isFalse();
    assertThat(closed.get()).isTrue();
  }

  @Test
  void logoutClosesOnlyItsOwnConnections() throws Exception {
    RuntimeService service = service();
    var first = service.create(new SessionRequest("TERMINAL", "local", 800, 600), "first");
    var second = service.create(new SessionRequest("TERMINAL", "local", 800, 600), "second");
    AtomicBoolean closed = new AtomicBoolean();
    service.bind(service.attach(first.id(), "first"), () -> closed.set(true));
    service.closeOwner("first");
    assertThat(closed.get()).isTrue();
    assertThat(service.owned(second.id(), "second")).isNotNull();
    service.shutdown();
  }
}
