package com.personal.dashboard.nas.service;

import com.personal.dashboard.nas.dto.NasSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NasSettingsService {
  private final String host;
  private final String username;

  public NasSettingsService(
      @Value("${nas.smb-host:}") String host, @Value("${nas.username:}") String username) {
    this.host = host;
    this.username = username;
  }

  public NasSettings settings() {
    return new NasSettings(host, 445, "storage", username);
  }
}
