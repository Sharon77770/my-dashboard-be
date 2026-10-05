package com.personal.dashboard.studio.controller;

import com.personal.dashboard.studio.dto.AssistantDto;
import com.personal.dashboard.studio.dto.DeviceCodexDto;
import com.personal.dashboard.studio.dto.StudioDto;
import com.personal.dashboard.studio.service.StudioService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Session-owned device management jobs with OWNER authorization in the service. */
@RestController
@RequestMapping("/api/v1/devices/{deviceId}/codex/jobs")
public class DeviceCodexController {
  private final StudioService service;

  public DeviceCodexController(StudioService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public StudioDto.JobView start(
      HttpSession session,
      @PathVariable String deviceId,
      @Valid @RequestBody DeviceCodexDto.Request input) {
    return service.startDevice(
        session.getId(),
        new StudioDto.Request(deviceId, input.root(), input.action(), input.args()));
  }

  @GetMapping("/{id}")
  public StudioDto.JobView get(
      HttpSession session, @PathVariable String deviceId, @PathVariable String id) {
    return service.getDevice(session.getId(), deviceId, id);
  }

  @PostMapping("/{id}/inputs")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void input(
      HttpSession session,
      @PathVariable String deviceId,
      @PathVariable String id,
      @Valid @RequestBody AssistantDto.Control input) {
    service.controlDevice(session.getId(), deviceId, id, input);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(HttpSession session, @PathVariable String deviceId, @PathVariable String id) {
    service.cancelDevice(session.getId(), deviceId, id);
  }
}
