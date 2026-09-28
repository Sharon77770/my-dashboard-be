package com.personal.dashboard.notes.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

/** Converts MCP Markdown input to the same editable block shape used by the notebook. */
@Component
public class NoteMarkdownConverter {
  private final ObjectMapper json;
  private final Parser parser =
      Parser.builder()
          .extensions(List.of(StrikethroughExtension.create(), TablesExtension.create()))
          .build();

  public NoteMarkdownConverter(ObjectMapper json) {
    this.json = json;
  }

  public JsonNode blocks(String markdown) {
    ArrayNode blocks = json.createArrayNode();
    appendBlocks(parser.parse(markdown), blocks);
    return blocks;
  }

  /** Only the exact paragraph shape written by the former MCP tools is eligible for repair. */
  public Optional<JsonNode> migrateLegacyBlocks(JsonNode storedBlocks) {
    if (!storedBlocks.isArray()) return Optional.empty();
    ArrayNode migrated = json.createArrayNode();
    boolean changed = false;
    for (JsonNode block : storedBlocks) {
      if (isLegacyMcpParagraph(block)) {
        JsonNode converted = blocks(block.get("content").get(0).get("text").asText());
        if (hasMarkdownFormatting(converted)) {
          converted.forEach(item -> migrated.add(item.deepCopy()));
          changed = true;
          continue;
        }
      }
      migrated.add(block.deepCopy());
    }
    return changed ? Optional.of(migrated) : Optional.empty();
  }

  private boolean isLegacyMcpParagraph(JsonNode block) {
    if (!block.isObject() || block.size() != 2 || !block.path("type").asText().equals("paragraph"))
      return false;
    JsonNode content = block.path("content");
    if (!content.isArray() || content.size() != 1) return false;
    JsonNode text = content.get(0);
    return text.isObject()
        && text.size() == 2
        && text.path("type").asText().equals("text")
        && text.path("text").isTextual();
  }

  private boolean hasMarkdownFormatting(JsonNode converted) {
    if (converted.size() != 1 || !converted.get(0).path("type").asText().equals("paragraph"))
      return !converted.isEmpty();
    for (JsonNode inline : converted.get(0).path("content")) {
      if (inline.path("type").asText().equals("link")) return true;
      if (inline.path("styles").isObject() && !inline.path("styles").isEmpty()) return true;
    }
    return false;
  }

  private void appendBlocks(Node parent, ArrayNode output) {
    for (Node node = parent.getFirstChild(); node != null; node = node.getNext())
      appendBlock(node, output);
  }

  private void appendBlock(Node node, ArrayNode output) {
    if (node instanceof Heading heading) {
      ObjectNode block = block("heading", inline(node));
      block.putObject("props").put("level", heading.getLevel());
      output.add(block);
    } else if (node instanceof Paragraph) {
      if (node.getFirstChild() instanceof Image image
          && image.getNext() == null
          && image.getDestination().startsWith("https://")) {
        ObjectNode block = json.createObjectNode().put("type", "image");
        ObjectNode props = block.putObject("props");
        props.put("url", image.getDestination());
        props.put("name", image.getTitle() == null ? "" : image.getTitle());
        props.put("caption", image.getFirstChild() instanceof Text alt ? alt.getLiteral() : "");
        output.add(block);
      } else output.add(block("paragraph", inline(node)));
    } else if (node instanceof BulletList || node instanceof OrderedList) {
      appendList(node, output);
    } else if (node instanceof BlockQuote) {
      appendQuote(node, output);
    } else if (node instanceof FencedCodeBlock code) {
      ObjectNode block = block("codeBlock", plain(code.getLiteral()));
      block
          .putObject("props")
          .put("language", code.getInfo() == null ? "" : code.getInfo().split("\\s+", 2)[0]);
      output.add(block);
    } else if (node instanceof IndentedCodeBlock code) {
      output.add(block("codeBlock", plain(code.getLiteral())));
    } else if (node instanceof ThematicBreak) {
      output.add(json.createObjectNode().put("type", "divider"));
    } else if (node instanceof TableBlock) {
      output.add(table(node));
    } else if (node instanceof HtmlBlock html) {
      output.add(block("paragraph", plain(html.getLiteral())));
    }
  }

  private ObjectNode table(Node table) {
    ObjectNode block = json.createObjectNode().put("type", "table");
    ObjectNode content = block.putObject("content").put("type", "tableContent");
    ArrayNode rows = content.putArray("rows");
    for (Node section = table.getFirstChild(); section != null; section = section.getNext()) {
      for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
        if (!(row instanceof TableRow)) continue;
        ArrayNode cells = rows.addObject().putArray("cells");
        for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
          if (cell instanceof TableCell) cells.add(inline(cell));
        }
      }
    }
    return block;
  }

  private void appendList(Node list, ArrayNode output) {
    int number = list instanceof OrderedList ordered ? ordered.getStartNumber() : 1;
    for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
      if (!(item instanceof ListItem)) continue;
      Node first = item.getFirstChild();
      ArrayNode content = first instanceof Paragraph ? inline(first) : json.createArrayNode();
      String type = list instanceof OrderedList ? "numberedListItem" : "bulletListItem";
      boolean checked = false;
      if (list instanceof BulletList
          && !content.isEmpty()
          && content.get(0).path("type").asText().equals("text")) {
        String value = content.get(0).path("text").asText();
        if (value.startsWith("[ ] ") || value.startsWith("[x] ") || value.startsWith("[X] ")) {
          type = "checkListItem";
          checked = !value.startsWith("[ ] ");
          ((ObjectNode) content.get(0)).put("text", value.substring(4));
        }
      }
      ObjectNode block = block(type, content);
      if (list instanceof OrderedList) block.putObject("props").put("start", number++);
      if (type.equals("checkListItem")) block.putObject("props").put("checked", checked);
      ArrayNode children = json.createArrayNode();
      for (Node child = first; child != null; child = child.getNext()) {
        if (child != first || !(first instanceof Paragraph)) appendBlock(child, children);
      }
      if (!children.isEmpty()) block.set("children", children);
      output.add(block);
    }
  }

  private void appendQuote(Node quote, ArrayNode output) {
    Node first = quote.getFirstChild();
    if (first instanceof Paragraph) {
      ObjectNode block = block("quote", inline(first));
      ArrayNode children = json.createArrayNode();
      for (Node next = first.getNext(); next != null; next = next.getNext())
        appendBlock(next, children);
      if (!children.isEmpty()) block.set("children", children);
      output.add(block);
    } else appendBlocks(quote, output);
  }

  private ObjectNode block(String type, ArrayNode content) {
    ObjectNode block = json.createObjectNode().put("type", type);
    block.set("content", content);
    return block;
  }

  private ArrayNode plain(String value) {
    ArrayNode content = json.createArrayNode();
    content
        .addObject()
        .put("type", "text")
        .put("text", value)
        .set("styles", json.createObjectNode());
    return content;
  }

  private ArrayNode inline(Node parent) {
    ArrayNode content = json.createArrayNode();
    appendInline(parent, content, Map.of());
    return content;
  }

  private void appendInline(Node parent, ArrayNode output, Map<String, Boolean> styles) {
    for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
      if (node instanceof Text text) addText(output, text.getLiteral(), styles);
      else if (node instanceof Code code)
        addText(output, code.getLiteral(), withStyle(styles, "code"));
      else if (node instanceof HtmlInline html) addText(output, html.getLiteral(), styles);
      else if (node instanceof SoftLineBreak || node instanceof HardLineBreak)
        addText(output, "\n", styles);
      else if (node instanceof Link link) {
        ObjectNode linkContent =
            json.createObjectNode().put("type", "link").put("href", link.getDestination());
        ArrayNode nested = json.createArrayNode();
        appendInline(link, nested, styles);
        linkContent.set("content", nested);
        output.add(linkContent);
      } else if (node instanceof StrongEmphasis)
        appendInline(node, output, withStyle(styles, "bold"));
      else if (node instanceof Emphasis) appendInline(node, output, withStyle(styles, "italic"));
      else if (node instanceof Strikethrough)
        appendInline(node, output, withStyle(styles, "strike"));
      else appendInline(node, output, styles);
    }
  }

  private Map<String, Boolean> withStyle(Map<String, Boolean> styles, String name) {
    var updated = new java.util.HashMap<>(styles);
    updated.put(name, true);
    return updated;
  }

  private void addText(ArrayNode output, String value, Map<String, Boolean> styles) {
    ObjectNode text = json.createObjectNode().put("type", "text").put("text", value);
    text.set("styles", json.valueToTree(styles));
    output.add(text);
  }
}
