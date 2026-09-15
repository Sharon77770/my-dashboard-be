package com.personal.dashboard.cloud.service;

import com.personal.dashboard.cloud.adapter.CloudStorage;
import com.personal.dashboard.cloud.dto.CloudDto.*;
import com.personal.dashboard.files.service.FileDownload;
import com.personal.dashboard.global.WorkspaceException;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasRole('OWNER')")
public class CloudService {
  private final CloudStorage storage;

  public CloudService(CloudStorage storage) {
    this.storage = storage;
  }

  @FunctionalInterface
  private interface Work<T> {
    T run() throws IOException;
  }

  private <T> T call(Work<T> action) {
    try {
      return action.run();
    } catch (FileAlreadyExistsException error) {
      throw new WorkspaceException(409, "같은 이름이 이미 있습니다. 이름을 바꾸거나 업로드 덮어쓰기를 선택하세요.");
    } catch (NoSuchFileException error) {
      throw new WorkspaceException(404, "파일 또는 폴더를 찾을 수 없습니다.");
    } catch (AccessDeniedException error) {
      throw new WorkspaceException(403, "저장소 접근 권한이 없습니다.");
    } catch (IOException error) {
      throw new WorkspaceException(500, "파일 작업에 실패했습니다. 저장 공간과 권한을 확인하세요.");
    }
  }

  public Listing list(String path, String query) {
    if (query.length() > 200) throw new WorkspaceException(400, "검색어가 너무 깁니다.");
    return call(() -> storage.list(path, query));
  }

  public Entry info(String path) {
    return call(() -> storage.info(path));
  }

  public void create(Create request) {
    call(
        () -> {
          storage.create(request.path(), request.directory());
          return null;
        });
  }

  public void upload(String path, InputStream input, boolean overwrite) {
    call(
        () -> {
          storage.upload(path, input, overwrite);
          return null;
        });
  }

  public void transfer(Transfer request) {
    call(
        () -> {
          storage.transfer(request.source(), request.target(), request.copy());
          return null;
        });
  }

  public void delete(String path) {
    call(
        () -> {
          storage.delete(path);
          return null;
        });
  }

  public List<TrashItem> trash() {
    return call(storage::trash);
  }

  public void restore(String id) {
    call(
        () -> {
          storage.restore(id);
          return null;
        });
  }

  public void purge(String id) {
    call(
        () -> {
          storage.purge(id);
          return null;
        });
  }

  public FileDownload download(String path) {
    return call(() -> storage.download(path));
  }

  public FileDownload archive(List<String> paths) {
    return call(() -> storage.archive(paths));
  }

  public void save(Save input) {
    call(
        () -> {
          storage.save(input);
          return null;
        });
  }

  public Text preview(String path) {
    return call(() -> storage.preview(path));
  }
}
