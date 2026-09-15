package com.personal.dashboard.cloud.controller;

import com.personal.dashboard.cloud.dto.CloudDto.*;
import com.personal.dashboard.cloud.service.CloudService;
import com.personal.dashboard.files.service.FileDownload;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/cloud")
public class CloudController {
  private final CloudService service;

  public CloudController(CloudService service) {
    this.service = service;
  }

  @GetMapping
  public Listing list(
      @RequestParam(defaultValue = "/") String path,
      @RequestParam(defaultValue = "") String query) {
    return service.list(path, query);
  }

  @GetMapping("/info")
  public Entry info(@RequestParam String path) {
    return service.info(path);
  }

  @PostMapping("/entries")
  @ResponseStatus(HttpStatus.CREATED)
  public void create(@Valid @RequestBody Create input) {
    service.create(input);
  }

  @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public void upload(
      @RequestParam String path,
      @RequestParam(defaultValue = "false") boolean overwrite,
      @RequestParam MultipartFile file)
      throws IOException {
    try (var input = file.getInputStream()) {
      service.upload(path, input, overwrite);
    }
  }

  @PostMapping("/transfers")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void transfer(@Valid @RequestBody Transfer input) {
    service.transfer(input);
  }

  @DeleteMapping("/entries")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@RequestParam String path) {
    service.delete(path);
  }

  @GetMapping("/trash")
  public List<TrashItem> trash() {
    return service.trash();
  }

  @PostMapping("/trash/{id}/restoration")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void restore(@PathVariable String id) {
    service.restore(id);
  }

  @DeleteMapping("/trash/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void purge(@PathVariable String id) {
    service.purge(id);
  }

  @GetMapping("/content")
  public ResponseEntity<InputStreamResource> download(@RequestParam String path) {
    return download(service.download(path));
  }

  @GetMapping("/archive")
  public ResponseEntity<InputStreamResource> archive(@RequestParam("path") List<String> paths) {
    return download(service.archive(paths));
  }

  @PutMapping("/text")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void save(@Valid @RequestBody Save input) {
    service.save(input);
  }

  @GetMapping("/preview")
  public Text preview(@RequestParam String path) {
    return service.preview(path);
  }

  private ResponseEntity<InputStreamResource> download(FileDownload file) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
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
