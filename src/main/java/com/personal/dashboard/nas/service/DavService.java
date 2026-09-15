package com.personal.dashboard.nas.service;

import com.personal.dashboard.cloud.adapter.CloudStorage;
import com.personal.dashboard.cloud.dto.CloudDto.Entry;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.nas.adapter.DavXml;
import com.personal.dashboard.nas.dto.DavDto.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.util.UriUtils;
import org.w3c.dom.*;

/** WebDAV use cases use the same locked, private storage as the browser drive. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class DavService {
  private static final String ALLOW =
      "OPTIONS, GET, HEAD, PROPFIND, PROPPATCH, PUT, MKCOL, COPY, MOVE, DELETE, LOCK, UNLOCK";
  private static final List<String> PROPS =
      List.of(
          "displayname",
          "resourcetype",
          "getcontentlength",
          "getlastmodified",
          "creationdate",
          "getcontenttype",
          "getetag",
          "supportedlock",
          "lockdiscovery");
  private final CloudStorage storage;
  private final DavLocks locks;
  private final boolean enabled;
  private final String publicUrl;
  private final long maxUpload;

  public DavService(
      CloudStorage storage,
      DavLocks locks,
      @Value("${nas.enabled:true}") boolean enabled,
      @Value("${nas.public-url:}") String publicUrl,
      @Value("${UPLOAD_MAX_SIZE:1GB}") String maxUpload) {
    this.storage = storage;
    this.locks = locks;
    this.enabled = enabled;
    if (!publicUrl.isBlank()) {
      URI address = URI.create(publicUrl);
      if (!Set.of("http", "https").contains(address.getScheme())
          || address.getHost() == null
          || address.getUserInfo() != null
          || address.getQuery() != null
          || address.getFragment() != null
          || !Set.of("/dav", "/dav/").contains(address.getPath()))
        throw new IllegalArgumentException(
            "NAS_PUBLIC_URL must be an HTTP(S) /dav/ URL without credentials");
    }
    this.publicUrl = publicUrl;
    this.maxUpload = DataSize.parse(maxUpload).toBytes();
  }

  public Settings settings(String username) {
    return new Settings(enabled, "/dav/", publicUrl, username);
  }

  private Entry existing(String path) throws IOException {
    try {
      return storage.info(path);
    } catch (NoSuchFileException error) {
      return null;
    }
  }

  public Reply execute(Request request) {
    if (!enabled) throw new WorkspaceException(503, "네트워크 드라이브가 비활성화되어 있습니다.");
    synchronized (storage) {
      try {
        String path = storage.davPath(request.path());
        Entry current = existing(path);
        Set<String> tokens = conditions(request, path, current);
        try (var scope = locks.authorize(tokens)) {
          return switch (request.method()) {
            case "OPTIONS" ->
                empty(200, Map.of("Allow", ALLOW, "DAV", "1, 2", "MS-Author-Via", "DAV"));
            case "PROPFIND" -> propfind(request, path, require(current));
            case "PROPPATCH" -> proppatch(request, path, require(current));
            case "GET", "HEAD" -> read(request, path, require(current));
            case "PUT" -> put(request, path, current);
            case "MKCOL" -> {
              if (current != null) throw new WorkspaceException(405, "이미 존재하는 경로입니다.");
              if (body(request).length > 0)
                throw new WorkspaceException(415, "MKCOL 본문은 지원하지 않습니다.");
              parent(path);
              storage.create(path, true);
              yield empty(201);
            }
            case "DELETE" -> {
              require(current);
              storage.delete(path);
              locks.removed(path);
              yield empty(204);
            }
            case "COPY", "MOVE" -> transfer(request, path, require(current));
            case "LOCK" -> lock(request, path, current, tokens);
            case "UNLOCK" -> {
              String token = request.header("lock-token");
              if (token == null || !token.matches("<opaquelocktoken:[a-f0-9-]{36}>"))
                throw new WorkspaceException(400, "Lock-Token이 필요합니다.");
              locks.unlock(path, token.substring(1, token.length() - 1));
              yield empty(204);
            }
            default -> empty(405, Map.of("Allow", ALLOW));
          };
        }
      } catch (WorkspaceException error) {
        throw error;
      } catch (NoSuchFileException error) {
        throw new WorkspaceException(404, "파일을 찾을 수 없습니다.");
      } catch (FileAlreadyExistsException error) {
        throw new WorkspaceException(412, "같은 이름이 이미 존재합니다.");
      } catch (AccessDeniedException error) {
        throw new WorkspaceException(403, "파일 접근 권한이 없습니다.");
      } catch (IOException error) {
        throw new WorkspaceException(507, "저장 공간이나 파일 접근 상태를 확인하세요.");
      } catch (IllegalArgumentException error) {
        throw new WorkspaceException(400, "잘못된 WebDAV 요청입니다.");
      }
    }
  }

  private Entry require(Entry entry) {
    if (entry == null) throw new WorkspaceException(404, "파일을 찾을 수 없습니다.");
    return entry;
  }

  private void parent(String path) throws IOException {
    String parent = path.substring(0, path.lastIndexOf('/'));
    Entry entry = existing(parent.isEmpty() ? "/" : parent);
    if (entry == null || !entry.directory()) throw new WorkspaceException(409, "상위 폴더가 없습니다.");
  }

  private byte[] body(Request request) throws IOException {
    byte[] bytes = request.body().readNBytes(65537);
    if (bytes.length > 65536) throw new WorkspaceException(413, "WebDAV XML 본문이 너무 큽니다.");
    return bytes;
  }

  private Set<String> conditions(Request request, String path, Entry current) throws IOException {
    String etag = current == null ? null : storage.davEtag(path);
    String match = request.header("if-match"), none = request.header("if-none-match");
    if (match != null
        && !(current != null
            && (match.trim().equals("*")
                || Arrays.asList(match.split("\\s*,\\s*")).contains(etag))))
      throw new WorkspaceException(412, "파일이 변경되었습니다.");
    if (none != null
        && current != null
        && (none.trim().equals("*") || Arrays.asList(none.split("\\s*,\\s*")).contains(etag))
        && !Set.of("GET", "HEAD").contains(request.method()))
      throw new WorkspaceException(412, "파일이 이미 존재하거나 변경되었습니다.");
    String header = request.header("if");
    if (header == null) return Set.of();
    if (header.length() > 8192) throw new WorkspaceException(400, "If 헤더가 너무 깁니다.");
    // Accept positive token lists only; unsupported/negative conditions fail closed.
    var matcher =
        java.util.regex.Pattern.compile(
                "\\s*(?:<([^>]+)>\\s*)?\\(\\s*<(opaquelocktoken:[a-f0-9-]{36})>\\s*\\)\\s*")
            .matcher(header);
    Set<String> tokens = new HashSet<>();
    int end = 0;
    while (matcher.find()) {
      if (matcher.start() != end) throw new WorkspaceException(412, "지원되지 않는 If 조건입니다.");
      String resource = matcher.group(1) == null ? path : destination(request, matcher.group(1));
      if (!locks.valid(resource, matcher.group(2)))
        throw new WorkspaceException(412, "유효하지 않은 잠금 조건입니다.");
      tokens.add(matcher.group(2));
      end = matcher.end();
    }
    if (end != header.length() || tokens.isEmpty())
      throw new WorkspaceException(412, "지원되지 않는 If 조건입니다.");
    return tokens;
  }

  private String destination(Request request, String header) throws IOException {
    if (header == null) throw new WorkspaceException(400, "Destination이 필요합니다.");
    URI uri = URI.create(header);
    if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null)
      throw new WorkspaceException(400, "잘못된 대상 주소입니다.");
    if (uri.isAbsolute()) {
      URI origin = URI.create(publicUrl.isBlank() ? request.origin() : publicUrl);
      if (!Set.of("http", "https").contains(uri.getScheme())
          || !Objects.equals(uri.getRawAuthority(), origin.getRawAuthority()))
        throw new WorkspaceException(502, "다른 서버로의 복사는 지원하지 않습니다.");
    } else if (uri.getRawAuthority() != null) throw new WorkspaceException(400, "잘못된 대상 주소입니다.");
    String path = UriUtils.decode(uri.getRawPath(), StandardCharsets.UTF_8);
    if (!path.equals("/dav") && !path.startsWith("/dav/"))
      throw new WorkspaceException(403, "드라이브 밖의 대상입니다.");
    return storage.davPath(path.length() == 4 ? "/" : path.substring(4));
  }

  private Reply put(Request request, String path, Entry current) throws IOException {
    if (current != null && current.directory())
      throw new WorkspaceException(405, "폴더에 PUT할 수 없습니다.");
    parent(path);
    if (request.header("content-range") != null)
      throw new WorkspaceException(400, "부분 PUT은 지원하지 않습니다.");
    String length = request.header("content-length");
    if (length != null && Long.parseLong(length) > maxUpload)
      throw new WorkspaceException(413, "업로드 크기 제한을 초과했습니다.");
    InputStream limited =
        new FilterInputStream(request.body()) {
          long total;

          private void count(int size) {
            if (size > 0 && (total += size) > maxUpload)
              throw new WorkspaceException(413, "업로드 크기 제한을 초과했습니다.");
          }

          @Override
          public int read() throws IOException {
            int value = in.read();
            count(value < 0 ? 0 : 1);
            return value;
          }

          @Override
          public int read(byte[] buffer, int offset, int length) throws IOException {
            int value = in.read(buffer, offset, length);
            count(value);
            return value;
          }
        };
    storage.upload(path, limited, true);
    return empty(current == null ? 201 : 204, Map.of("ETag", storage.davEtag(path)));
  }

  private Reply transfer(Request request, String path, Entry source) throws IOException {
    String target = destination(request, request.header("destination"));
    if (target.equals(path)
        || target.startsWith(path.equals("/") ? "/" : path + "/")
        || path.startsWith(target.equals("/") ? "/" : target + "/"))
      throw new WorkspaceException(403, "겹치는 경로로 이동·복사할 수 없습니다.");
    boolean copy = request.method().equals("COPY");
    String depth = request.header("depth");
    if (depth != null && !depth.equals("infinity") && !(copy && depth.equals("0")))
      throw new WorkspaceException(400, "지원되지 않는 Depth입니다.");
    String overwrite = request.header("overwrite");
    if (overwrite != null && !Set.of("T", "F").contains(overwrite))
      throw new WorkspaceException(400, "잘못된 Overwrite입니다.");
    Entry previous = existing(target);
    if (previous != null && "F".equals(overwrite))
      throw new WorkspaceException(412, "대상 파일이 이미 있습니다.");
    parent(target);
    locks.check(target, true);
    if (!copy) locks.check(path, true);
    String deleted = null;
    try {
      if (previous != null) deleted = storage.delete(target);
      if (copy && source.directory() && "0".equals(depth)) storage.create(target, true);
      else storage.transfer(path, target, copy);
    } catch (IOException | RuntimeException error) {
      if (deleted != null) {
        try {
          storage.restore(deleted);
        } catch (Exception rollback) {
          error.addSuppressed(rollback);
        }
      }
      throw error;
    }
    if (!copy) locks.removed(path);

    return empty(previous == null ? 201 : 204);
  }

  private Reply read(Request request, String path, Entry entry) throws IOException {
    String etag = storage.davEtag(path), none = request.header("if-none-match");
    if (none != null && (none.equals("*") || Arrays.asList(none.split("\\s*,\\s*")).contains(etag)))
      return empty(304, Map.of("ETag", etag));
    if (entry.directory())
      return request.method().equals("HEAD")
          ? empty(200)
          : xml(
              200,
              "<d:collection xmlns:d=\"DAV:\"><d:href>"
                  + href(path, true)
                  + "</d:href></d:collection>");
    long start = 0, end = entry.size() - 1;
    boolean partial = false;
    String range = request.header("range");
    String ifRange = request.header("if-range");
    if (range != null && (ifRange == null || ifRange.equals(etag))) {
      var match = java.util.regex.Pattern.compile("bytes=(\\d*)-(\\d*)").matcher(range);
      if (!match.matches() || entry.size() == 0)
        return empty(416, Map.of("Content-Range", "bytes */" + entry.size()));
      try {
        if (match.group(1).isEmpty()) {
          long suffix = Long.parseLong(match.group(2));
          if (suffix <= 0) throw new NumberFormatException();
          start = Math.max(0, entry.size() - suffix);
        } else {
          start = Long.parseLong(match.group(1));
          if (!match.group(2).isEmpty()) end = Math.min(end, Long.parseLong(match.group(2)));
        }
        if (start > end || start < 0) throw new NumberFormatException();
        partial = true;
      } catch (NumberFormatException error) {
        return empty(416, Map.of("Content-Range", "bytes */" + entry.size()));
      }
    }
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/octet-stream");
    headers.put("ETag", etag);
    headers.put("Accept-Ranges", "bytes");
    headers.put("Last-Modified", httpDate(entry.modified()));
    headers.put("Content-Length", String.valueOf(end - start + 1));
    if (partial) headers.put("Content-Range", "bytes " + start + "-" + end + "/" + entry.size());
    return new Reply(
        partial ? 206 : 200,
        headers,
        null,
        request.method().equals("HEAD") ? null : storage.download(path),
        start,
        end - start + 1);
  }

  private Reply lock(Request request, String path, Entry current, Set<String> tokens)
      throws IOException {
    byte[] body = body(request);
    long seconds = 600;
    String timeout = request.header("timeout");
    if (timeout != null && timeout.matches("Second-[0-9]+")) {
      try {
        seconds = Math.max(1, Math.min(3600, Long.parseLong(timeout.substring(7))));
      } catch (NumberFormatException ignored) {
        seconds = 3600;
      }
    }
    DavLocks.Lease lease;
    if (body.length == 0) {
      if (tokens.size() != 1) throw new WorkspaceException(400, "잠금 갱신 토큰이 필요합니다.");
      lease = locks.refresh(path, tokens.iterator().next(), seconds);
    } else {
      Document document = DavXml.parse(body);
      if (!"lockinfo".equals(document.getDocumentElement().getLocalName())
          || document.getElementsByTagNameNS("DAV:", "exclusive").getLength() != 1
          || document.getElementsByTagNameNS("DAV:", "write").getLength() != 1
          || document.getElementsByTagNameNS("DAV:", "shared").getLength() != 0)
        throw new WorkspaceException(400, "독점 쓰기 잠금만 지원합니다.");
      String depth = request.header("depth");
      if (depth != null && !Set.of("0", "infinity").contains(depth))
        throw new WorkspaceException(400, "잠금 Depth는 0 또는 infinity여야 합니다.");
      if (current == null) parent(path);
      lease = locks.acquire(path, !"0".equals(depth), seconds);
      if (current == null) {
        try (var scope = locks.authorize(Set.of(lease.token()))) {
          storage.create(path, false);
        } catch (IOException | RuntimeException error) {
          locks.unlock(path, lease.token());
          throw error;
        }
      }
    }
    String xml =
        "<d:prop xmlns:d=\"DAV:\"><d:lockdiscovery>"
            + lockXml(lease)
            + "</d:lockdiscovery></d:prop>";
    Reply reply = xml(current == null ? 201 : 200, xml);
    reply.headers().put("Lock-Token", "<" + lease.token() + ">");
    return reply;
  }

  private String lockXml(DavLocks.Lease lease) {
    return "<d:activelock><d:locktype><d:write/></d:locktype><d:lockscope><d:exclusive/></d:lockscope><d:depth>"
        + (lease.recursive() ? "infinity" : "0")
        + "</d:depth><d:timeout>Second-"
        + Math.max(1, (lease.expires() - System.currentTimeMillis()) / 1000)
        + "</d:timeout><d:locktoken><d:href>"
        + lease.token()
        + "</d:href></d:locktoken><d:lockroot><d:href>"
        + href(lease.path(), false)
        + "</d:href></d:lockroot></d:activelock>";
  }

  private Reply propfind(Request request, String path, Entry current) throws IOException {
    String depth = request.header("depth");
    if (depth == null || depth.equals("infinity"))
      throw new WorkspaceException(403, "PROPFIND Depth는 0 또는 1이어야 합니다.");
    if (!Set.of("0", "1").contains(depth)) throw new WorkspaceException(400, "잘못된 Depth입니다.");
    Document document = DavXml.parse(body(request));
    List<javax.xml.namespace.QName> properties = new ArrayList<>();
    boolean namesOnly = false;
    if (document != null) {
      if (!"propfind".equals(document.getDocumentElement().getLocalName()))
        throw new WorkspaceException(400, "PROPFIND XML이 필요합니다.");
      NodeList props = document.getElementsByTagNameNS("DAV:", "prop");
      if (props.getLength() > 0)
        for (Node child = props.item(0).getFirstChild();
            child != null;
            child = child.getNextSibling())
          if (child instanceof Element element)
            properties.add(
                new javax.xml.namespace.QName(
                    element.getNamespaceURI() == null ? "" : element.getNamespaceURI(),
                    element.getLocalName()));
      namesOnly = document.getElementsByTagNameNS("DAV:", "propname").getLength() > 0;
    }
    if (properties.size() > 64) throw new WorkspaceException(413, "요청한 속성이 너무 많습니다.");
    if (properties.isEmpty())
      for (String property : PROPS) properties.add(new javax.xml.namespace.QName("DAV:", property));
    List<Entry> entries = new ArrayList<>();
    entries.add(current);
    if (depth.equals("1") && current.directory()) {
      var listing = storage.list(path, "");
      if (listing.truncated()) throw new WorkspaceException(507, "폴더 목록 한도를 초과했습니다.");
      entries.addAll(listing.entries());
    }
    StringBuilder xml = new StringBuilder("<d:multistatus xmlns:d=\"DAV:\">");
    for (Entry entry : entries) {
      StringBuilder found = new StringBuilder(), missing = new StringBuilder();
      for (var property : properties) {
        boolean known =
            property.getNamespaceURI().equals("DAV:") && PROPS.contains(property.getLocalPart());
        String value = known && !namesOnly ? property(entry, property.getLocalPart()) : "";
        (known ? found : missing)
            .append(DavXml.element(property.getNamespaceURI(), property.getLocalPart(), value));
      }
      xml.append("<d:response><d:href>")
          .append(href(entry.path(), entry.directory()))
          .append("</d:href>");
      if (!found.isEmpty()) xml.append(propstat(found.toString(), 200));
      if (!missing.isEmpty()) xml.append(propstat(missing.toString(), 404));
      xml.append("</d:response>");
      if (xml.length() > 8 * 1024 * 1024)
        throw new WorkspaceException(507, "속성 응답이 너무 큽니다. 폴더를 나눠 조회하세요.");
    }
    return xml(207, xml.append("</d:multistatus>").toString());
  }

  private String property(Entry entry, String property) throws IOException {
    return switch (property) {
      case "displayname" -> DavXml.escape(entry.path().equals("/") ? "내 드라이브" : entry.name());
      case "resourcetype" -> entry.directory() ? "<d:collection/>" : "";
      case "getcontentlength" -> String.valueOf(entry.size());
      case "getlastmodified" -> httpDate(entry.modified());
      case "creationdate" -> storage.davCreated(entry.path());
      case "getcontenttype" ->
          entry.directory() ? "httpd/unix-directory" : "application/octet-stream";
      case "getetag" -> DavXml.escape(storage.davEtag(entry.path()));
      case "supportedlock" ->
          "<d:lockentry><d:lockscope><d:exclusive/></d:lockscope><d:locktype><d:write/></d:locktype></d:lockentry>";
      case "lockdiscovery" ->
          locks.discover(entry.path()).stream().map(this::lockXml).reduce("", String::concat);
      default -> "";
    };
  }

  private Reply proppatch(Request request, String path, Entry current) throws IOException {
    locks.check(path, false);
    Document document = DavXml.parse(body(request));
    if (document == null) throw new WorkspaceException(400, "속성 변경 XML이 필요합니다.");
    StringBuilder properties = new StringBuilder();
    NodeList props = document.getElementsByTagNameNS("DAV:", "prop");
    if (document.getElementsByTagName("*").getLength() > 128)
      throw new WorkspaceException(413, "속성 변경 요청이 너무 큽니다.");
    for (int i = 0; i < props.getLength(); i++)
      for (Node child = props.item(i).getFirstChild();
          child != null;
          child = child.getNextSibling())
        if (child instanceof Element element)
          properties.append(
              DavXml.element(
                  element.getNamespaceURI() == null ? "" : element.getNamespaceURI(),
                  element.getLocalName(),
                  ""));
    if (properties.isEmpty()) throw new WorkspaceException(400, "변경할 속성이 없습니다.");
    return xml(
        207,
        "<d:multistatus xmlns:d=\"DAV:\"><d:response><d:href>"
            + href(path, current.directory())
            + "</d:href>"
            + propstat(properties.toString(), 403)
            + "</d:response></d:multistatus>");
  }

  private String propstat(String value, int status) {
    return "<d:propstat><d:prop>"
        + value
        + "</d:prop><d:status>HTTP/1.1 "
        + status
        + " "
        + (status == 200 ? "OK" : status == 404 ? "Not Found" : "Forbidden")
        + "</d:status></d:propstat>";
  }

  private String href(String path, boolean directory) {
    String result = "/dav" + UriUtils.encodePath(path, StandardCharsets.UTF_8);
    return DavXml.escape(result + (directory && !result.endsWith("/") ? "/" : ""));
  }

  private String httpDate(long millis) {
    return DateTimeFormatter.RFC_1123_DATE_TIME.format(
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC));
  }

  private Reply empty(int status) {
    return empty(status, Map.of());
  }

  private Reply empty(int status, Map<String, String> headers) {
    return new Reply(status, new LinkedHashMap<>(headers), null, null, 0, 0);
  }

  private Reply xml(int status, String body) {
    return new Reply(
        status,
        new LinkedHashMap<>(Map.of("Content-Type", "application/xml; charset=utf-8")),
        ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + body).getBytes(StandardCharsets.UTF_8),
        null,
        0,
        0);
  }
}
