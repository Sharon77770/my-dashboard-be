package com.personal.dashboard.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.notes.domain.NoteKind;
import com.personal.dashboard.notes.dto.NoteDto;
import com.personal.dashboard.notes.service.NoteMarkdownConverter;
import com.personal.dashboard.notes.service.NoteService;
import com.personal.dashboard.planner.dto.PlannerDto.EventRequest;
import com.personal.dashboard.planner.service.PlannerService;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Exposes a narrow, validated subset of dashboard use cases through Model Context Protocol tools.
 */
@Service
public class AssistantMcpService {
  private static final Set<String> ROUTES =
      Set.of(
          "home",
          "calendar",
          "timetable",
          "notes",
          "cloud",
          "files",
          "devices",
          "apps",
          "logs",
          "terminal",
          "remote",
          "studio",
          "recent",
          "clipboard");
  private final PlannerService planner;
  private final CatalogService catalog;
  private final NoteService notes;
  private final NoteMarkdownConverter markdown;
  private final AssistantEvents events;
  private final Validator validator;
  private final ObjectMapper json;

  public AssistantMcpService(
      PlannerService planner,
      CatalogService catalog,
      NoteService notes,
      NoteMarkdownConverter markdown,
      AssistantEvents events,
      Validator validator,
      ObjectMapper json) {
    this.planner = planner;
    this.catalog = catalog;
    this.notes = notes;
    this.markdown = markdown;
    this.events = events;
    this.validator = validator;
    this.json = json;
  }

  public List<Map<String, Object>> tools() {
    return List.of(
        tool(
            "open_page",
            "Open a dashboard page in the user's browser.",
            schema(List.of("route"), Map.of("route", enumeration(ROUTES))),
            false),
        tool(
            "list_apps",
            "List the user's registered external applications.",
            schema(List.of(), Map.of()),
            true),
        tool(
            "open_app",
            "Open a registered external application using the user's browser settings.",
            schema(List.of("id"), Map.of("id", string(36))),
            false),
        tool(
            "list_calendar_events",
            "List events already stored in this dashboard's calendar; no external calendar connection is required. "
                + "Use ISO dates and a half-open local date range [from, to). For October of year YYYY, "
                + "use from=YYYY-10-01 and to=YYYY-11-01. The to date is excluded. "
                + "Always call this tool when the user asks to see or explain calendar events; "
                + "an empty events array means there are no saved events in that range.",
            schema(List.of("from", "to"), Map.of("from", date(), "to", date())),
            true),
        tool(
            "create_calendar_event",
            "Create a calendar event. start and end are local ISO date-times; use midnight"
                + " boundaries for all-day events.",
            schema(
                List.of("title", "start", "end"),
                Map.of(
                    "title", string(120),
                    "start", dateTime(),
                    "end", dateTime(),
                    "allDay", Map.of("type", "boolean"),
                    "location", string(200),
                    "notes", string(4000),
                    "color", Map.of("type", "string", "pattern", "^#[0-9a-fA-F]{6}$"))),
            false),
        tool(
            "list_notes",
            "List notebook folders and documents with their IDs and parent folders.",
            schema(List.of(), Map.of()),
            true),
        tool(
            "read_note",
            "Read a notebook document and its editable content blocks.",
            schema(List.of("id"), Map.of("id", string(36))),
            true),
        tool(
            "create_note_folder",
            "Create a notebook folder under the selected parent, or at the notebook root.",
            schema(List.of("title"), Map.of("title", string(200), "parentId", nullableString(36))),
            false),
        tool(
            "create_note",
            "Create a notebook document from Markdown under the selected parent folder.",
            schema(
                List.of("title", "text"),
                Map.of(
                    "title", string(200), "text", string(100000), "parentId", nullableString(36))),
            false),
        tool(
            "append_note",
            "Append Markdown blocks to an existing notebook document using its current revision.",
            schema(
                List.of("id", "text"),
                Map.of("id", string(36), "text", string(100000), "revision", integer())),
            false));
  }

  public Map<String, Object> call(String name, JsonNode args) {
    if (args == null || !args.isObject())
      throw new WorkspaceException(400, "MCP 도구 입력은 JSON 객체여야 합니다.");
    return switch (name) {
      case "open_page" -> openPage(args);
      case "list_apps" -> Map.of("applications", catalog.applications());
      case "open_app" -> openApplication(args);
      case "list_calendar_events" ->
          Map.of(
              "events",
              planner.events(
                  LocalDate.parse(requiredText(args, "from", 10)),
                  LocalDate.parse(requiredText(args, "to", 10))));
      case "create_calendar_event" -> createEvent(args);
      case "list_notes" -> Map.of("entries", notes.entries());
      case "read_note" -> {
        var document = notes.document(requiredText(args, "id", 36));
        yield Map.of("entry", document.entry(), "blocks", document.blocks());
      }
      case "create_note_folder" -> createFolder(args);
      case "create_note" -> createNote(args);
      case "append_note" -> appendNote(args);
      default -> throw new WorkspaceException(404, "지원하지 않는 MCP 도구입니다.");
    };
  }

  private Map<String, Object> openPage(JsonNode args) {
    String route = requiredText(args, "route", 40);
    if (!ROUTES.contains(route)) throw new WorkspaceException(400, "열 수 없는 대시보드 페이지입니다.");
    var event = events.navigate(route);
    return Map.of("route", route, "opened", true, "sequence", event.sequence());
  }

  private Map<String, Object> openApplication(JsonNode args) {
    String id = requiredText(args, "id", 36);
    var app = catalog.requireApplication(id);
    var event = events.openApplication(id);
    return Map.of(
        "id",
        app.id(),
        "name",
        app.name(),
        "url",
        app.url(),
        "opened",
        true,
        "sequence",
        event.sequence());
  }

  private Map<String, Object> createEvent(JsonNode args) {
    String title = requiredText(args, "title", 120);
    var input =
        new EventRequest(
            title,
            LocalDateTime.parse(requiredText(args, "start", 32)),
            LocalDateTime.parse(requiredText(args, "end", 32)),
            args.path("allDay").asBoolean(false),
            optionalText(args, "location", 200),
            optionalText(args, "notes", 4000),
            args.hasNonNull("color") ? requiredText(args, "color", 7) : "#6b8afd");
    validate(input);
    return Map.of("event", planner.saveEvent(null, input));
  }

  private Map<String, Object> createFolder(JsonNode args) {
    var input =
        new NoteDto.Create(
            NoteKind.FOLDER,
            optionalText(args, "parentId", 36),
            requiredText(args, "title", 200),
            "📁",
            json.createArrayNode());
    validate(input);
    return Map.of("folder", notes.create(input).entry());
  }

  private Map<String, Object> createNote(JsonNode args) {
    var input =
        new NoteDto.Create(
            NoteKind.DOCUMENT,
            optionalText(args, "parentId", 36),
            requiredText(args, "title", 200),
            "📝",
            markdown.blocks(requiredText(args, "text", 100000)));
    validate(input);
    var created = notes.create(input);
    return Map.of("entry", created.entry(), "blocks", created.blocks());
  }

  private Map<String, Object> appendNote(JsonNode args) {
    String id = requiredText(args, "id", 36);
    if (!args.path("revision").canConvertToLong() || args.path("revision").asLong() < 0)
      throw new WorkspaceException(400, "입력값을 확인해 주세요: revision");
    var document = notes.document(id);
    if (args.path("revision").asLong() != document.entry().revision())
      throw new WorkspaceException(409, "메모가 다른 곳에서 변경되었습니다. 최신 내용을 다시 읽어 주세요.");
    var combined = json.createArrayNode();
    if (document.blocks().isArray())
      document.blocks().forEach(block -> combined.add(block.deepCopy()));
    markdown
        .blocks(requiredText(args, "text", 100000))
        .forEach(block -> combined.add(block.deepCopy()));
    var saved = notes.save(id, new NoteDto.Content(combined, document.entry().revision()));
    return Map.of("entry", saved, "appended", true);
  }

  private <T> void validate(T input) {
    var violations = validator.validate(input);
    if (!violations.isEmpty())
      throw new WorkspaceException(
          400, "입력값을 확인해 주세요: " + violations.iterator().next().getPropertyPath());
  }

  private String requiredText(JsonNode args, String name, int limit) {
    JsonNode value = args.get(name);
    if (value == null
        || !value.isTextual()
        || value.asText().isBlank()
        || value.asText().length() > limit)
      throw new WorkspaceException(400, "입력값을 확인해 주세요: " + name);
    return value.asText();
  }

  private String optionalText(JsonNode args, String name, int limit) {
    JsonNode value = args.get(name);
    if (value == null || value.isNull()) return null;
    if (!value.isTextual() || value.asText().length() > limit)
      throw new WorkspaceException(400, "입력값을 확인해 주세요: " + name);
    return value.asText();
  }

  private Map<String, Object> tool(
      String name, String description, Map<String, Object> schema, boolean readOnly) {
    return Map.of(
        "name", name,
        "description", description,
        "inputSchema", schema,
        "annotations",
            Map.of(
                "title",
                name.replace('_', ' '),
                "readOnlyHint",
                readOnly,
                "destructiveHint",
                false,
                "openWorldHint",
                false));
  }

  private Map<String, Object> schema(List<String> required, Map<String, Object> properties) {
    return Map.of(
        "type",
        "object",
        "properties",
        properties,
        "required",
        required,
        "additionalProperties",
        false);
  }

  private Map<String, Object> string(int max) {
    return Map.of("type", "string", "maxLength", max);
  }

  private Map<String, Object> nullableString(int max) {
    return Map.of("type", List.of("string", "null"), "maxLength", max);
  }

  private Map<String, Object> date() {
    return Map.of("type", "string", "format", "date");
  }

  private Map<String, Object> dateTime() {
    return Map.of("type", "string", "format", "date-time");
  }

  private Map<String, Object> integer() {
    return Map.of("type", "integer", "minimum", 0);
  }

  private Map<String, Object> enumeration(Set<String> values) {
    return Map.of("type", "string", "enum", values.stream().sorted().toList());
  }
}
