package com.personal.dashboard.cloud;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "DASHBOARD_AUTH_ID=fixture",
      "DASHBOARD_AUTH_PASSWORD=fixture-cloud-only",
      "DASHBOARD_DB_PATH=./target/cloud-test.db",
      "workspace.root=./target/cloud-workspace",
      "workspace.key-path=./target/cloud-key",
      "cloud.root=./target/cloud-http"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class CloudIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void privateUploadDownloadTrashAndRestore() throws Exception {
    mvc.perform(
            multipart("/api/v1/cloud/uploads")
                .param("path", "/missing-file")
                .with(user("owner").roles("OWNER"))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    String path = "/" + UUID.randomUUID() + "/한글.txt";
    mvc.perform(get("/api/v1/cloud")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/cloud/entries")
                .with(user("owner").roles("OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"/blocked\",\"directory\":true}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            multipart("/api/v1/cloud/uploads")
                .file(new MockMultipartFile("file", "한글.txt", "text/plain", "fixture".getBytes()))
                .param("path", path)
                .with(user("owner").roles("OWNER"))
                .with(csrf()))
        .andExpect(status().isCreated());
    mvc.perform(get("/api/v1/cloud/content").param("path", path).with(user("owner").roles("OWNER")))
        .andExpect(status().isOk())
        .andExpect(content().string("fixture"))
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(
            delete("/api/v1/cloud/entries")
                .param("path", path)
                .with(user("owner").roles("OWNER"))
                .with(csrf()))
        .andExpect(status().isNoContent());
    var response =
        mvc.perform(get("/api/v1/cloud/trash").with(user("owner").roles("OWNER")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = null;
    for (var item : json.readTree(response))
      if (item.get("path").asText().equals(path)) id = item.get("id").asText();
    org.assertj.core.api.Assertions.assertThat(id).isNotNull();
    mvc.perform(
            post("/api/v1/cloud/trash/" + id + "/restoration")
                .with(user("owner").roles("OWNER"))
                .with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/cloud/preview").param("path", path).with(user("owner").roles("OWNER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").value("fixture"));
    mvc.perform(
            get("/api/v1/cloud").param("path", "/../outside").with(user("owner").roles("OWNER")))
        .andExpect(status().isBadRequest());
  }
}
