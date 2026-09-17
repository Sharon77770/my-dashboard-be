package com.personal.dashboard.global;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Normalizes API failures without reflecting credentials, commands or upstream response bodies. */
@RestControllerAdvice(
    basePackages = {
      "com.personal.dashboard.catalog.controller",
      "com.personal.dashboard.files.controller",
      "com.personal.dashboard.cloud.controller",
      "com.personal.dashboard.nas.controller",
      "com.personal.dashboard.runtime.controller",
      "com.personal.dashboard.planner.controller",
      "com.personal.dashboard.notes.controller",
      "com.personal.dashboard.studio.controller"
    })
public class WorkspaceErrors {
  public record ErrorResponse(String message) {}

  @ExceptionHandler(WorkspaceException.class)
  public ResponseEntity<ErrorResponse> workspace(WorkspaceException exception) {
    return ResponseEntity.status(exception.status())
        .body(new ErrorResponse(exception.getMessage()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.multipart.support.MissingServletRequestPartException.class,
    IllegalArgumentException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class
  })
  public ResponseEntity<ErrorResponse> invalid(Exception exception) {
    return ResponseEntity.badRequest().body(new ErrorResponse("입력 형식과 필수 항목을 확인해 주세요."));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> upload(Exception exception) {
    return ResponseEntity.status(413).body(new ErrorResponse("업로드 허용 크기를 초과했습니다."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> unexpected(Exception exception) {
    return ResponseEntity.internalServerError()
        .body(new ErrorResponse("요청 처리에 실패했습니다. 설정과 연결 상태를 확인해 주세요."));
  }
}
