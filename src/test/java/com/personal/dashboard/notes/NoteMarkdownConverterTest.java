package com.personal.dashboard.notes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.notes.service.NoteMarkdownConverter;
import org.junit.jupiter.api.Test;

/** Verifies MCP Markdown becomes editable notebook blocks instead of literal source text. */
class NoteMarkdownConverterTest {
  private final NoteMarkdownConverter converter = new NoteMarkdownConverter(new ObjectMapper());

  @Test
  void convertsMarkdownStructureAndFormatting() {
    var blocks =
        converter.blocks(
            "# 작업 기록\n\n첫 **굵은** 문장과 [링크](https://example.com).\n\n- [x] 완료\n- 할 일\n\n```java\nint x = 1;\n```\n");

    assertThat(blocks.get(0).path("type").asText()).isEqualTo("heading");
    assertThat(blocks.get(0).path("props").path("level").asInt()).isEqualTo(1);
    assertThat(blocks.get(1).path("content").toString())
        .contains("\"bold\":true", "\"type\":\"link\"");
    assertThat(blocks.get(2).path("type").asText()).isEqualTo("checkListItem");
    assertThat(blocks.get(2).path("props").path("checked").asBoolean()).isTrue();
    assertThat(blocks.get(2).path("content").get(0).path("text").asText()).isEqualTo("완료");
    assertThat(blocks.get(3).path("type").asText()).isEqualTo("bulletListItem");
    assertThat(blocks.get(4).path("type").asText()).isEqualTo("codeBlock");
    assertThat(blocks.get(4).path("props").path("language").asText()).isEqualTo("java");
  }

  @Test
  void plainTextAndHtmlStaySafeEditableText() {
    var blocks = converter.blocks("일반 메모\n\n<script>alert(1)</script>");

    assertThat(blocks.get(0).path("type").asText()).isEqualTo("paragraph");
    assertThat(blocks.toString()).contains("<script>alert(1)</script>");
    assertThat(blocks.toString()).doesNotContain("\"html\"");
  }

  @Test
  void convertsTableAndHttpsImage() {
    var blocks =
        converter.blocks(
            "| 이름 | 상태 |\n| --- | --- |\n| 작업 | 완료 |\n\n![화면](https://example.com/image.png)");

    assertThat(blocks.get(0).path("type").asText()).isEqualTo("table");
    assertThat(blocks.get(0).path("content").path("rows").size()).isEqualTo(2);
    assertThat(
            blocks
                .get(0)
                .path("content")
                .path("rows")
                .get(1)
                .path("cells")
                .get(0)
                .get(0)
                .path("text")
                .asText())
        .isEqualTo("작업");
    assertThat(blocks.get(1).path("type").asText()).isEqualTo("image");
    assertThat(blocks.get(1).path("props").path("url").asText())
        .isEqualTo("https://example.com/image.png");
  }
}
