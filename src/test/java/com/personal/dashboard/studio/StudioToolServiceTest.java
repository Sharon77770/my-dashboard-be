package com.personal.dashboard.studio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.studio.adapter.StudioAdapter;
import com.personal.dashboard.studio.dto.StudioDto;
import com.personal.dashboard.studio.service.*;
import jakarta.validation.Validation;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class StudioToolServiceTest {
  @Test
  void deniesReadOnlyAndExternalUrlsAndBindsApiToOriginalProject() throws Exception {
    var json = new ObjectMapper();
    var adapter = mock(StudioAdapter.class);
    var catalog = mock(CatalogService.class);
    var api = mock(StudioApiService.class);
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var service =
          new StudioToolService(
              adapter,
              catalog,
              mock(StudioBrowserService.class),
              api,
              json,
              factory.getValidator());
      var readOnly =
          new StudioDto.Request(
              "device",
              "/project",
              "codex-run",
              json.readValue("{\"mode\":\"read-only\"}", StudioDto.Args.class));
      var write =
          new StudioDto.Request(
              "device",
              "/project",
              "codex-run",
              json.readValue("{\"mode\":\"workspace-write\"}", StudioDto.Args.class));
      var external =
          new StudioAdapter.ToolCall(
              "call",
              "studio_api",
              json.readTree("{\"method\":\"GET\",\"url\":\"https://example.com\"}"));
      assertThatThrownBy(() -> service.execute("owner", readOnly, external))
          .isInstanceOf(WorkspaceException.class);
      assertThatThrownBy(() -> service.execute("owner", write, external))
          .isInstanceOf(WorkspaceException.class);
      verifyNoInteractions(adapter, api);
      doAnswer(
              invocation -> {
                Consumer<StudioAdapter.Message> sink = invocation.getArgument(3);
                sink.accept(
                    json.readValue(
                        "{\"result\":{\"tools\":{\"ports\":[{\"port\":18765,\"project\":true}]}}}",
                        StudioAdapter.Message.class));
                return null;
              })
          .when(adapter)
          .execute(any(), any(), any(), any());
      service.execute(
          "owner",
          write,
          new StudioAdapter.ToolCall(
              "call",
              "studio_api",
              json.readTree(
                  "{\"deviceId\":\"other\",\"root\":\"/other\",\"method\":\"GET\",\"url\":\"http://127.0.0.1:18765/api\"}")));
      verify(api)
          .send(
              argThat(
                  input -> input.deviceId().equals("device") && input.root().equals("/project")));
      assertThatThrownBy(
              () ->
                  service.execute(
                      "owner",
                      write,
                      new StudioAdapter.ToolCall(
                          "call",
                          "studio_api",
                          json.readTree("{\"method\":\"GET\",\"url\":\"http://127.0.0.1:8080\"}"))))
          .isInstanceOf(WorkspaceException.class);
    }
  }
}
