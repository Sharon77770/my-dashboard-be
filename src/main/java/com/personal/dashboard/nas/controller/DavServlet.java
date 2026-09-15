package com.personal.dashboard.nas.controller;

import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.nas.adapter.DavXml;
import com.personal.dashboard.nas.dto.DavDto.*;
import com.personal.dashboard.nas.service.DavService;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.web.util.UriUtils;

/** HTTP/WebDAV transport only; custom methods bypass HttpServlet's built-in method dispatch. */
public class DavServlet extends HttpServlet {
  private final DavService service;

  public DavServlet(DavService service) {
    this.service = service;
  }

  @Override
  public void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");
    try {
      String uri = request.getRequestURI().substring(request.getContextPath().length());
      String path =
          UriUtils.decode(uri.length() == 4 ? "/" : uri.substring(4), StandardCharsets.UTF_8);
      Map<String, String> headers = new HashMap<>();
      var names = request.getHeaderNames();
      while (names.hasMoreElements()) {
        String name = names.nextElement();
        headers.put(name.toLowerCase(Locale.ROOT), request.getHeader(name));
      }
      String origin =
          request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
      // Match common absolute Destination headers that omit default HTTP/HTTPS ports.
      if ((request.getScheme().equals("http") && request.getServerPort() == 80)
          || (request.getScheme().equals("https") && request.getServerPort() == 443))
        origin = request.getScheme() + "://" + request.getServerName();
      Reply reply =
          service.execute(
              new Request(request.getMethod(), path, origin, headers, request.getInputStream()));
      response.setStatus(reply.status());
      reply.headers().forEach(response::setHeader);
      if (reply.file() != null) {
        try (var input = reply.file().stream()) {
          input.skipNBytes(reply.offset());
          long remaining = reply.length();
          byte[] buffer = new byte[65536];
          while (remaining > 0) {
            int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (count < 0) break;
            response.getOutputStream().write(buffer, 0, count);
            remaining -= count;
          }
        }
      } else if (reply.body() != null && !request.getMethod().equals("HEAD")) {
        response.setContentLength(reply.body().length);
        response.getOutputStream().write(reply.body());
      }
    } catch (WorkspaceException error) {
      if (!response.isCommitted()) {
        response.resetBuffer();
        response.setStatus(error.status());
        response.setContentType("application/xml; charset=utf-8");
        response
            .getWriter()
            .write(
                "<d:error xmlns:d=\"DAV:\"><d:responsedescription>"
                    + DavXml.escape(error.getMessage())
                    + "</d:responsedescription></d:error>");
      }
    } catch (IllegalArgumentException error) {
      if (!response.isCommitted()) response.sendError(400);
    }
  }
}
