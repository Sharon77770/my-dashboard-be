package com.personal.dashboard.studio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.studio.adapter.StudioAdapter;
import com.personal.dashboard.studio.dto.StudioDto.*;
import com.personal.dashboard.studio.service.StudioService;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Tests login ownership, bounded concurrency and cancellation races without external credentials.
 */
class StudioServiceTest {
  private StudioService service(StudioAdapter adapter) {
    var catalog = mock(CatalogService.class);
    when(catalog.requireDevice("remote"))
        .thenReturn(
            new DeviceRecord(
                "remote",
                "Test",
                "localhost",
                22,
                "tester",
                "cipher",
                "SHA256:fixture",
                "/home/tester",
                "NONE",
                3389,
                "",
                "",
                "",
                "",
                false));
    return new StudioService(catalog, adapter);
  }

  private Request request(String action) {
    return new Request("remote", "/home/tester", action, null);
  }

  @Test
  void interactiveInputIsRestrictedToTheJobOwner() throws Exception {
    var adapter = mock(StudioAdapter.class);
    var release = new CountDownLatch(1);
    var entered = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              entered.countDown();
              release.await(3, TimeUnit.SECONDS);
              return null;
            })
        .when(adapter)
        .execute(any(), any(), any(), any());
    var service = service(adapter);
    try {
      var job = service.start("owner", request("codex-run"));
      assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
      var input =
          new com.personal.dashboard.studio.dto.AssistantDto.Control(
              "interrupt", null, null, null, null);
      assertThatThrownBy(() -> service.control("other", job.id(), input))
          .isInstanceOf(WorkspaceException.class);
      verify(adapter, never()).control(any(), any());
      service.control("owner", job.id(), input);
      verify(adapter).control(any(), eq(input));
      service.cancel("owner", job.id());
      assertThatThrownBy(() -> service.control("owner", job.id(), input))
          .isInstanceOf(WorkspaceException.class);
    } finally {
      release.countDown();
      service.shutdown();
    }
  }

  @Test
  void rejectsUnknownCommandsAndCrossSessionAccess() {
    var service = service(mock(StudioAdapter.class));
    try {
      assertThatThrownBy(() -> service.start("a", request("sh")))
          .isInstanceOf(WorkspaceException.class);
      var job = service.start("a", request("list"));
      assertThatThrownBy(() -> service.get("b", job.id())).isInstanceOf(WorkspaceException.class);
      assertThatThrownBy(() -> service.cancel("b", job.id()))
          .isInstanceOf(WorkspaceException.class);
    } finally {
      service.shutdown();
    }
  }

  @Test
  void cancelledJobCannotBeOverwrittenByLateSuccess() throws Exception {
    var adapter = mock(StudioAdapter.class);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var finished = new CountDownLatch(1);
    var message =
        new ObjectMapper().readValue("{\"result\":{\"ok\":true}}", StudioAdapter.Message.class);
    doAnswer(
            invocation -> {
              entered.countDown();
              release.await(3, TimeUnit.SECONDS);
              Consumer<StudioAdapter.Message> output = invocation.getArgument(3);
              output.accept(message);
              finished.countDown();
              return null;
            })
        .when(adapter)
        .execute(any(), any(), any(), any());
    var service = service(adapter);
    try {
      var job = service.start("a", request("codex-run"));
      assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
      service.cancel("a", job.id());
      release.countDown();
      assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(service.get("a", job.id()).state()).isEqualTo("CANCELLED");
      assertThat(service.get("a", job.id()).result()).isNull();
    } finally {
      release.countDown();
      service.shutdown();
    }
  }

  @Test
  void boundsConcurrentJobsAndRemovesJobsAtLogout() throws Exception {
    var adapter = mock(StudioAdapter.class);
    var release = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              release.await(3, TimeUnit.SECONDS);
              return null;
            })
        .when(adapter)
        .execute(any(), any(), any(), any());
    var service = service(adapter);
    try {
      var job = service.start("a", request("list"));
      for (int count = 0; count < 3; count++) service.start("a", request("list"));
      assertThatThrownBy(() -> service.start("a", request("list")))
          .isInstanceOf(WorkspaceException.class);
      service.closeOwner("a");
      assertThatThrownBy(() -> service.get("a", job.id())).isInstanceOf(WorkspaceException.class);
    } finally {
      release.countDown();
      service.shutdown();
    }
  }
}
