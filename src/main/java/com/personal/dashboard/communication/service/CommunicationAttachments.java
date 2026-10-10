package com.personal.dashboard.communication.service;

import com.personal.dashboard.communication.domain.Communication.Upload;
import com.personal.dashboard.communication.dto.CommunicationDto;
import com.personal.dashboard.global.WorkspaceException;
import java.util.*;

/**
 * Bounded immutable attachment input, no paths, executables, archives or automatic content
 * execution.
 */
final class CommunicationAttachments {
  private CommunicationAttachments() {}

  static List<Upload> decode(List<CommunicationDto.Upload> inputs) {
    if (inputs == null) return List.of();
    if (inputs.size() > 5) throw new WorkspaceException(413, "첨부파일은 최대 5개입니다.");
    List<Upload> files = new ArrayList<>();
    long total = 0;
    for (var input : inputs) {
      if (input == null
          || input.name() == null
          || input.name().isBlank()
          || input.name().length() > 180
          || input.name().chars().anyMatch(value -> value < 32 || value == '/' || value == '\\')
          || input.data() == null
          || input.data().length() > 7000000)
        throw new WorkspaceException(400, "첨부파일 이름과 크기를 확인해 주세요.");
      if (!Set.of(
              "text/plain",
              "text/csv",
              "application/json",
              "application/pdf",
              "image/png",
              "image/jpeg")
          .contains(input.mediaType()))
        throw new WorkspaceException(400, "지원 파일 형식은 TXT/CSV/JSON/PDF/PNG/JPEG입니다.");
      byte[] data;
      try {
        data = Base64.getDecoder().decode(input.data());
      } catch (IllegalArgumentException exception) {
        throw new WorkspaceException(400, "첨부파일 인코딩을 확인해 주세요.");
      }
      total += data.length;
      if (data.length == 0 || total > 5 * 1024 * 1024)
        throw new WorkspaceException(413, "첨부파일 총 크기는 5 MiB 이하여야 합니다.");
      boolean valid =
          switch (input.mediaType()) {
            case "application/pdf" ->
                data.length >= 5
                    && new String(data, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)
                        .equals("%PDF-");
            case "image/png" ->
                data.length >= 8
                    && Arrays.equals(
                        Arrays.copyOf(data, 8),
                        new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            case "image/jpeg" ->
                data.length >= 3
                    && data[0] == (byte) 255
                    && data[1] == (byte) 216
                    && data[2] == (byte) 255;
            default -> validText(data);
          };
      if (!valid) throw new WorkspaceException(400, "파일 내용과 형식이 일치하지 않습니다.");
      files.add(new Upload(input.name(), input.mediaType(), data));
    }
    return List.copyOf(files);
  }

  private static boolean validText(byte[] data) {
    try {
      var decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder();
      String text = decoder.decode(java.nio.ByteBuffer.wrap(data)).toString();
      return text.indexOf(0) < 0;
    } catch (java.nio.charset.CharacterCodingException exception) {
      return false;
    }
  }
}
