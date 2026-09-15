package com.personal.dashboard.files.controller;

import com.personal.dashboard.files.dto.*;
import com.personal.dashboard.files.service.FileService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Translates authenticated HTTP file requests into server-side file use cases. */
@RestController
@RequestMapping("/api/v1/devices/{device}/files")
public class FileController {
  private final FileService service;

  public FileController(FileService service) {
    this.service = service;
  }

  @GetMapping
  public FileListing list(
      @PathVariable String device, @RequestParam(defaultValue = "/") String path) {
    return service.list(device, path);
  }

  @PostMapping("/folders")
  public ResponseEntity<Void> mkdir(
      @PathVariable String device, @Valid @RequestBody FileMutation request) {
    service.mkdir(device, request.path(), request.name());
    return ResponseEntity.status(201).build();
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> upload(
      @PathVariable String device, @RequestParam String path, @RequestParam MultipartFile file)
      throws IOException {
    try (var input = file.getInputStream()) {
      service.upload(device, path, file.getOriginalFilename(), input);
    }
    return ResponseEntity.status(201).build();
  }

  @PatchMapping
  public ResponseEntity<Void> rename(
      @PathVariable String device, @Valid @RequestBody FileMutation request) {
    service.rename(device, request.path(), request.name());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping
  public ResponseEntity<Void> delete(@PathVariable String device, @RequestParam String path) {
    service.delete(device, path);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/content")
  public ResponseEntity<InputStreamResource> download(
      @PathVariable String device, @RequestParam String path) {
    var file = service.download(device, path);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(file.size())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(file.name(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(new InputStreamResource(file.stream()));
  }
}
