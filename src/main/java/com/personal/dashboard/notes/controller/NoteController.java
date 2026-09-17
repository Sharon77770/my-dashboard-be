package com.personal.dashboard.notes.controller;

import com.personal.dashboard.notes.dto.NoteDto.*;
import com.personal.dashboard.notes.service.NoteService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Session-authenticated notebook resources; mutating requests require the shared CSRF token. */
@RestController
@RequestMapping("/api/v1/notes")
public class NoteController {
  private final NoteService service;

  public NoteController(NoteService service) {
    this.service = service;
  }

  @GetMapping
  public List<Entry> entries() {
    return service.entries();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Document create(@Valid @RequestBody Create input) {
    return service.create(input);
  }

  @GetMapping("/{id}")
  public Document document(@PathVariable String id) {
    return service.document(id);
  }

  @PutMapping("/{id}")
  public Entry metadata(@PathVariable String id, @Valid @RequestBody Metadata input) {
    return service.metadata(id, input);
  }

  @PutMapping("/{id}/content")
  public Entry save(@PathVariable String id, @Valid @RequestBody Content input) {
    return service.save(id, input);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id, @RequestParam long revision) {
    service.delete(id, revision);
  }

  @PostMapping("/{id}/images")
  @ResponseStatus(HttpStatus.CREATED)
  public ImageView upload(@PathVariable String id, @RequestParam MultipartFile file)
      throws IOException {
    return service.upload(id, file);
  }

  @GetMapping("/images/{id}")
  public ResponseEntity<byte[]> image(@PathVariable String id) {
    ImageContent image = service.image(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.mediaType()))
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(image.data());
  }
}
