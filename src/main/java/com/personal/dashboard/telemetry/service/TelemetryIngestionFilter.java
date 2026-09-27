package com.personal.dashboard.telemetry.service;

import com.personal.dashboard.global.WorkspaceException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates telemetry before JSON parsing and bounds request frequency and declared body size.
 */
@Component
public class TelemetryIngestionFilter extends OncePerRequestFilter {
  private final TelemetryService service;
  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  private record Window(long minute, AtomicInteger count) {}

  public TelemetryIngestionFilter(TelemetryService service) {
    this.service = service;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().matches(".*/api/v1/telemetry/(events|gauges|batch)");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      if (request.getContentLengthLong() > 65_536) {
        response.sendError(413, "Telemetry payload exceeds 64 KiB.");
        return;
      }
      String id = service.authenticate(request.getHeader("Authorization"));
      long minute = System.currentTimeMillis() / 60_000;
      Window window =
          windows.compute(
              id,
              (key, current) ->
                  current == null || current.minute() != minute
                      ? new Window(minute, new AtomicInteger())
                      : current);
      if (window.count().incrementAndGet() > 120) {
        response.sendError(429, "Telemetry rate limit exceeded.");
        return;
      }
      request.setAttribute("telemetryServiceId", id);
      byte[] body = request.getInputStream().readNBytes(65_537);
      if (body.length > 65_536) {
        response.sendError(413, "Telemetry payload exceeds 64 KiB.");
        return;
      }
      chain.doFilter(new BoundedBodyRequest(request, body), response);
    } catch (WorkspaceException error) {
      response.sendError(error.status(), error.getMessage());
    }
  }

  private static final class BoundedBodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    private BoundedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream input = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return input.read();
        }

        @Override
        public boolean isFinished() {
          return input.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          throw new UnsupportedOperationException("Async body reads are not used for telemetry.");
        }
      };
    }

    @Override
    public java.io.BufferedReader getReader() {
      return new java.io.BufferedReader(
          new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
  }
}
