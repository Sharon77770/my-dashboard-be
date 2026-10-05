package com.personal.dashboard.services.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.integration.CommandAdapter;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Executes a fixed bounded log reader through the existing local/verified SSH adapter. */
@Component
public class ServiceLogAdapter {
  public record ReadResult(
      String output,
      long scannedLines,
      long matchedLines,
      boolean truncated,
      boolean scanComplete,
      String firstTimestamp,
      String lastTimestamp) {}

  private final CommandAdapter commands;
  private final ObjectMapper json;
  private final String encoded;

  public ServiceLogAdapter(CommandAdapter commands, ObjectMapper json) throws IOException {
    this.commands = commands;
    this.json = json;
    try (var input = new ClassPathResource("studio/service_logs.py").getInputStream()) {
      encoded = Base64.getEncoder().encodeToString(input.readAllBytes());
    }
  }

  public ReadResult read(
      DeviceRecord device, String container, Instant since, Instant until, String filter) {
    if (container == null || !container.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}"))
      throw new WorkspaceException(400, "컨테이너 이름을 확인해 주세요.");
    if (!java.util.Set.of("all", "errors").contains(filter))
      throw new WorkspaceException(400, "로그 필터는 all 또는 errors여야 합니다.");
    String command =
        "python3 -c 'import base64;exec(base64.b64decode(\""
            + encoded
            + "\"))' "
            + container
            + " '"
            + since
            + "' '"
            + until
            + "' "
            + filter;
    String output = commands.execute(device, command);
    try {
      var result = json.readTree(output);
      if (result == null || result.has("error") || !result.has("scanComplete"))
        throw new WorkspaceException(
            502, "Docker 로그 조회에 실패했습니다. 컨테이너·도구 설치·로그 드라이버·SSH 계정 권한을 확인하세요.");
      return json.treeToValue(result, ReadResult.class);
    } catch (WorkspaceException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new WorkspaceException(502, "로그 수집 응답을 읽지 못했습니다. 원격 Python과 Docker 설치 상태를 확인하세요.");
    }
  }
}
