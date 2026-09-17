package com.personal.dashboard;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Exercises notebook hierarchy, revision protection, private images and content validation. */
@SpringBootTest(
    properties = {
      "DASHBOARD_AUTH_ID=notes-test",
      "DASHBOARD_AUTH_PASSWORD=notes-test-only",
      "workspace.root=./target/notes-files",
      "workspace.key-path=./target/notes-key",
      "DASHBOARD_DB_PATH=./target/notes-test.db"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class NotesIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  private JsonNode request(String method, String path, Object body, int status) throws Exception {
    var request =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(method), "/api/v1/notes" + path)
            .with(user("notes-test").roles("OWNER"))
            .with(csrf());
    if (body != null)
      request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
    String result =
        mvc.perform(request)
            .andExpect(status().is(status))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return result.isBlank() ? null : json.readTree(result);
  }

  private String create(String kind, String parent) throws Exception {
    var body = new HashMap<String, Object>();
    body.put("kind", kind);
    body.put("title", "조직 <img onerror=alert(1)>");
    body.put("icon", "📄");
    body.put("blocks", List.of());
    body.put("parentId", parent);
    return request("POST", "", body, 201).path("entry").path("id").asText();
  }

  @Test
  void permissionsAndCsrf() throws Exception {
    mvc.perform(get("/api/v1/notes")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/notes").with(user("guest").roles("GUEST")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/notes")
                .with(user("notes-test").roles("OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/notes/images/missing")).andExpect(status().isUnauthorized());
  }

  @Test
  void organizationProjectDocumentAndRevisionProtection() throws Exception {
    String organization = create("FOLDER", null),
        project = create("FOLDER", organization),
        document = create("DOCUMENT", project);
    var blocks =
        List.of(
            Map.of(
                "type",
                "checkListItem",
                "props",
                Map.of("checked", true),
                "content",
                List.of(Map.of("type", "text", "text", "완료한 일", "styles", Map.of()))));
    assertThat(
            request(
                    "PUT",
                    "/" + document + "/content",
                    Map.of("blocks", blocks, "revision", 0),
                    200)
                .path("revision")
                .asLong())
        .isEqualTo(1);
    request("PUT", "/" + document + "/content", Map.of("blocks", List.of(), "revision", 0), 409);
    assertThat(
            request("GET", "/" + document, null, 200)
                .path("blocks")
                .get(0)
                .path("props")
                .path("checked")
                .asBoolean())
        .isTrue();
    request(
        "PUT",
        "/" + organization,
        Map.of("title", "cycle", "icon", "", "parentId", project, "revision", 0),
        400);
    request(
        "PUT",
        "/" + project,
        Map.of("title", "bad parent", "icon", "", "parentId", document, "revision", 0),
        400);
    request("DELETE", "/" + project + "?revision=0", null, 409);
    request("DELETE", "/" + document + "?revision=0", null, 409);
    request("DELETE", "/" + document + "?revision=1", null, 204);
    request("DELETE", "/" + project + "?revision=0", null, 204);
    request("DELETE", "/" + organization + "?revision=0", null, 204);
  }

  @Test
  void unsupportedContentAndExecutableLinksAreRejected() throws Exception {
    String document = create("DOCUMENT", null);
    request("PUT", "/" + document + "/content", Map.of("blocks", List.of()), 400);
    request(
        "PUT",
        "/" + document + "/content",
        Map.of("revision", 0, "blocks", List.of(Map.of("type", "paragraph", "content", 42))),
        400);
    for (String type : List.of("script", "iframe", "video"))
      request(
          "PUT",
          "/" + document + "/content",
          Map.of("revision", 0, "blocks", List.of(Map.of("type", type))),
          400);
    for (String url :
        List.of(
            "javascript:alert(1)",
            "data:image/svg+xml,test",
            "file:///etc/passwd",
            "//example.com/track",
            "/api/v1/notes/images/missing")) {
      request(
          "PUT",
          "/" + document + "/content",
          Map.of(
              "revision",
              0,
              "blocks",
              List.of(Map.of("type", "image", "props", Map.of("url", url)))),
          400);
    }
    request(
        "PUT",
        "/" + document + "/content",
        Map.of(
            "revision",
            0,
            "blocks",
            List.of(Map.of("type", "paragraph", "content", "x".repeat(2 * 1024 * 1024)))),
        413);
    request("DELETE", "/" + document + "?revision=0", null, 204);
  }

  @Test
  void ledgerTableAndNestedTasksRoundTrip() throws Exception {
    String document = create("DOCUMENT", null);
    var text = Map.of("type", "text", "text", "수입", "styles", Map.of("bold", true));
    var table =
        Map.of(
            "type",
            "table",
            "content",
            Map.of(
                "type",
                "tableContent",
                "rows",
                List.of(
                    Map.of(
                        "cells",
                        List.of(
                            List.of(text),
                            List.of(Map.of("type", "text", "text", "지출", "styles", Map.of())))),
                    Map.of(
                        "cells",
                        List.of(
                            List.of(Map.of("type", "text", "text", "100000", "styles", Map.of())),
                            List.of())))));
    var task =
        Map.of(
            "type",
            "checkListItem",
            "props",
            Map.of("checked", true),
            "content",
            "했던 일",
            "children",
            List.of(Map.of("type", "paragraph", "content", "완료 기록")));
    request(
        "PUT",
        "/" + document + "/content",
        Map.of("blocks", List.of(table, task), "revision", 0),
        200);
    JsonNode read = request("GET", "/" + document, null, 200);
    assertThat(read.path("blocks").get(0).path("content").path("rows").size()).isEqualTo(2);
    assertThat(read.path("blocks").get(1).path("children").get(0).path("content").asText())
        .isEqualTo("완료 기록");
    request("DELETE", "/" + document + "?revision=1", null, 204);
  }

  @Test
  void uploadedImagesArePrivateOwnedAndDeletedWithDocument() throws Exception {
    String document = create("DOCUMENT", null), other = create("DOCUMENT", null);
    byte[] image =
        Base64.getDecoder()
            .decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=");
    var upload =
        mvc.perform(
                multipart("/api/v1/notes/" + document + "/images")
                    .file(new MockMultipartFile("file", "photo.png", "image/png", image))
                    .with(user("notes-test").roles("OWNER"))
                    .with(csrf()))
            .andExpect(status().isCreated())
            .andReturn();
    String url = json.readTree(upload.getResponse().getContentAsString()).path("url").asText();
    mvc.perform(get(url).with(user("notes-test").roles("OWNER")))
        .andExpect(status().isOk())
        .andExpect(content().bytes(image))
        .andExpect(header().string("Cache-Control", "no-store"));
    var blocks = List.of(Map.of("type", "image", "props", Map.of("url", url)));
    request("PUT", "/" + other + "/content", Map.of("blocks", blocks, "revision", 0), 400);
    request("PUT", "/" + document + "/content", Map.of("blocks", blocks, "revision", 0), 200);
    mvc.perform(
            multipart("/api/v1/notes/" + document + "/images")
                .file(new MockMultipartFile("file", "bad.svg", "image/png", "<svg/>".getBytes()))
                .with(user("notes-test").roles("OWNER"))
                .with(csrf()))
        .andExpect(status().isUnsupportedMediaType());
    request("DELETE", "/" + document + "?revision=1", null, 204);
    mvc.perform(get(url).with(user("notes-test").roles("OWNER"))).andExpect(status().isNotFound());
    request("DELETE", "/" + other + "?revision=0", null, 204);
  }
}
