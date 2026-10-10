package com.personal.dashboard.communication.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.integration.CommandAdapter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** Pinned SSH and account authentication protect a fixed per-user Windows named-pipe request. */
@Component
public class WindowsAgentAdapter {
  private final CommandAdapter commands;
  private final ObjectMapper json;

  public WindowsAgentAdapter(CommandAdapter commands, ObjectMapper json) {
    this.commands = commands;
    this.json = json;
  }

  public com.personal.dashboard.communication.dto.BridgeDto.WindowsSnapshot snapshot(
      DeviceRecord device) {
    // No public command, expression, executable path or pipe name is interpolated.
    String script =
        "$ErrorActionPreference='Stop';$p=[IO.Pipes.NamedPipeClientStream]::new('.','PersonalWorkspace.Communications.v1',[IO.Pipes.PipeDirection]::InOut);try{$p.Connect(3000);$w=[IO.StreamWriter]::new($p,[Text.UTF8Encoding]::new($false),1024,$true);$w.AutoFlush=$true;$w.WriteLine('{\"operation\":\"snapshot\"}');$r=[IO.StreamReader]::new($p);$t=$r.ReadLineAsync();if(-not $t.Wait(5000)){throw 'timeout'};[Console]::WriteLine($t.Result)}catch{[Console]::WriteLine('{\"state\":\"AGENT_UNAVAILABLE\",\"loginState\":\"UNKNOWN\",\"structuredMessages\":false,\"revision\":\"\",\"nodes\":[]}')}finally{$p.Dispose()}";
    String command =
        "powershell.exe -NoLogo -NoProfile -NonInteractive -EncodedCommand "
            + Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    try {
      var result = json.readTree(commands.execute(device, command));
      if (result == null || !result.has("state") || !result.path("nodes").isArray())
        throw new IllegalArgumentException();
      var states =
          java.util.Set.of(
              "AGENT_UNAVAILABLE",
              "APP_NOT_RUNNING_OR_NO_WINDOW",
              "ACCESSIBILITY_OBSERVED",
              "ACCESSIBILITY_UNAVAILABLE",
              "ACCESSIBILITY_BUSY",
              "UNAVAILABLE");
      String state = result.path("state").asText();
      if (!states.contains(state)) state = "UNAVAILABLE";
      java.util.List<com.personal.dashboard.communication.dto.BridgeDto.WindowsNode> nodes =
          new java.util.ArrayList<>();
      for (var node : result.path("nodes")) {
        if (nodes.size() >= 300) break;
        String name = node.path("name").asText();
        String role = node.path("role").asText();
        nodes.add(
            new com.personal.dashboard.communication.dto.BridgeDto.WindowsNode(
                role.substring(0, Math.min(role.length(), 100)),
                name.substring(0, Math.min(name.length(), 2000)),
                node.path("canInvoke").asBoolean(),
                node.path("canSetValue").asBoolean()));
      }
      String revision = result.path("revision").asText();
      if (!revision.matches("[A-Fa-f0-9]{64}")) revision = "";
      return new com.personal.dashboard.communication.dto.BridgeDto.WindowsSnapshot(
          state, "UNKNOWN", false, revision, nodes);
    } catch (Exception exception) {
      throw new WorkspaceException(502, "Windows Agent에 연결하지 못했습니다. 등록 장비의 SSH와 사용자 세션을 확인해 주세요.");
    }
  }
}
