package com.personal.dashboard.nas.dto;

import com.personal.dashboard.files.service.FileDownload;
import java.io.InputStream;
import java.util.Map;

public final class DavDto {
  private DavDto() {}

  public record Request(
      String method, String path, String origin, Map<String, String> headers, InputStream body) {
    public String header(String key) {
      return headers.get(key.toLowerCase(java.util.Locale.ROOT));
    }
  }

  public record Reply(
      int status,
      Map<String, String> headers,
      byte[] body,
      FileDownload file,
      long offset,
      long length) {}

  public record Settings(boolean enabled, String path, String publicUrl, String username) {}
}
