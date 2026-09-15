package com.personal.dashboard;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.personal.dashboard.tailscale.adapter.TailscaleAdapter;
import com.personal.dashboard.tailscale.dto.TailscaleView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "DASHBOARD_AUTH_ID=fixture",
      "DASHBOARD_AUTH_PASSWORD=fixture-test-only",
      "workspace.root=./target/ts-files",
      "workspace.key-path=./target/ts-key",
      "DASHBOARD_DB_PATH=./target/ts-test.db"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class TailscaleIntegrationTest {
  @Autowired MockMvc mvc;
  @MockitoBean TailscaleAdapter adapter;

  @Test
  void statusRequiresOwnerAndIsNotCached() throws Exception {
    when(adapter.invoke("GET"))
        .thenReturn(new TailscaleView("NeedsLogin", "", List.of(), "", false, ""));
    mvc.perform(get("/api/v1/tailscale")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/tailscale").with(user("owner").roles("OWNER")))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.state").value("NeedsLogin"));
  }

  @Test
  void mutationsRequireCsrfAndCallOnlySelectedOperation() throws Exception {
    mvc.perform(post("/api/v1/tailscale/login").with(user("owner").roles("OWNER")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(adapter);
    when(adapter.invoke("POST"))
        .thenReturn(
            new TailscaleView(
                "NeedsLogin", "", List.of(), "https://login.tailscale.com/a/fixture", true, ""));
    mvc.perform(post("/api/v1/tailscale/login").with(user("owner").roles("OWNER")).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pending").value(true));
    mvc.perform(delete("/api/v1/tailscale/login").with(user("owner").roles("OWNER")).with(csrf()))
        .andExpect(status().isOk());
    verify(adapter).invoke("POST");
    verify(adapter).invoke("DELETE");
  }
}
