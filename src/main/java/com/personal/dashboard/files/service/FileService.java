package com.personal.dashboard.files.service;

import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.files.adapter.FileAdapter;
import com.personal.dashboard.files.dto.*;
import com.personal.dashboard.global.WorkspaceException;
import java.io.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Owns file validation, root-relative navigation and recent-location recording. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class FileService {
  private final CatalogService catalog;
  private final FileAdapter adapter;

  public FileService(CatalogService catalog, FileAdapter adapter) {
    this.catalog = catalog;
    this.adapter = adapter;
  }

  public FileListing list(String device, String path) {
    validate(path);
    try {
      var profile = catalog.requireDevice(device);
      var entries = adapter.list(profile, path);
      catalog.record("FILES", device, profile.name() + " · 파일", path);
      return new FileListing(path, entries);
    } catch (IOException exception) {
      throw failure();
    }
  }

  public void mkdir(String device, String path, String name) {
    validate(path);
    basename(name);
    try {
      adapter.mkdir(catalog.requireDevice(device), child(path, name));
    } catch (IOException exception) {
      throw failure();
    }
  }

  public void upload(String device, String path, String name, InputStream input) {
    validate(path);
    basename(name);
    try {
      adapter.upload(catalog.requireDevice(device), child(path, name), input);
    } catch (IOException exception) {
      throw failure();
    }
  }

  public void rename(String device, String path, String name) {
    validate(path);
    basename(name);
    if (path.equals("/")) throw new WorkspaceException(400, "루트는 변경할 수 없습니다.");
    try {
      adapter.rename(
          catalog.requireDevice(device),
          path,
          child(path.substring(0, path.lastIndexOf('/')), name));
    } catch (IOException exception) {
      throw failure();
    }
  }

  public void delete(String device, String path) {
    validate(path);
    try {
      adapter.delete(catalog.requireDevice(device), path);
    } catch (IOException exception) {
      throw failure();
    }
  }

  public FileDownload download(String device, String path) {
    validate(path);
    try {
      return adapter.download(catalog.requireDevice(device), path);
    } catch (IOException exception) {
      throw failure();
    }
  }

  private void validate(String path) {
    if (path == null
        || !path.startsWith("/")
        || path.contains("\\")
        || path.contains("\u0000")
        || java.util.Arrays.asList(path.split("/")).contains("..")
        || path.length() > 1024) throw new WorkspaceException(400, "파일 경로를 확인해 주세요.");
  }

  private void basename(String name) {
    if (name == null
        || name.isBlank()
        || name.equals(".")
        || name.equals("..")
        || name.contains("/")
        || name.contains("\\")
        || name.contains("\u0000")
        || name.length() > 255) throw new WorkspaceException(400, "파일 이름을 확인해 주세요.");
  }

  private String child(String path, String name) {
    return (path.equals("/") ? "" : path) + "/" + name;
  }

  private WorkspaceException failure() {
    return new WorkspaceException(409, "파일 작업 실패: 경로·권한·중복 이름 또는 폴더가 비어 있는지 확인해 주세요.");
  }
}
