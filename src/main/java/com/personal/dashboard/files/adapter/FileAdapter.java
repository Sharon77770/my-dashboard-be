package com.personal.dashboard.files.adapter;

import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.files.dto.FileEntry;
import com.personal.dashboard.files.service.FileDownload;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.integration.SshAdapter;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.*;
import org.springframework.stereotype.Component;

/**
 * Local filesystem and SFTP adapter; every operation is resolved beneath the configured real root.
 */
@Component
public class FileAdapter {
  private final SshAdapter ssh;

  public FileAdapter(SshAdapter ssh) {
    this.ssh = ssh;
  }

  public List<FileEntry> list(DeviceRecord device, String path) throws IOException {
    List<FileEntry> entries = new ArrayList<>();
    if (device.id().equals("local")) {
      Path directory = local(device, path, false);
      try (var stream = Files.list(directory)) {
        for (Path file : stream.limit(2001).toList()) {
          if (Files.isSymbolicLink(file)) continue;
          entries.add(
              new FileEntry(
                  file.getFileName().toString(),
                  child(path, file.getFileName().toString()),
                  Files.isDirectory(file),
                  Files.size(file),
                  Files.getLastModifiedTime(file).toMillis()));
        }
      }
    } else {
      try (SSHClient client = ssh.connect(device);
          SFTPClient sftp = client.newSFTPClient()) {
        for (RemoteResourceInfo file : sftp.ls(remote(sftp, device, path, false))) {
          if (file.getName().equals(".")
              || file.getName().equals("..")
              || file.getAttributes().getType() == FileMode.Type.SYMLINK) continue;
          entries.add(
              new FileEntry(
                  file.getName(),
                  child(path, file.getName()),
                  file.isDirectory(),
                  file.getAttributes().getSize(),
                  file.getAttributes().getMtime() * 1000));
          if (entries.size() > 2000) break;
        }
      }
    }
    if (entries.size() > 2000) throw new WorkspaceException(400, "한 폴더에서 최대 2000개 항목을 조회할 수 있습니다.");
    entries.sort(
        Comparator.comparing(FileEntry::directory).reversed().thenComparing(FileEntry::name));
    return entries;
  }

  public void mkdir(DeviceRecord device, String path) throws IOException {
    if (device.id().equals("local")) Files.createDirectory(local(device, path, true));
    else
      try (SSHClient client = ssh.connect(device);
          SFTPClient sftp = client.newSFTPClient()) {
        sftp.mkdir(remote(sftp, device, path, true));
      }
  }

  public void upload(DeviceRecord device, String path, InputStream input) throws IOException {
    if (device.id().equals("local")) {
      Path target = local(device, path, true);
      if (Files.exists(target)) throw new FileAlreadyExistsException(target.toString());
      Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".part");
      try {
        try (OutputStream output = Files.newOutputStream(temporary)) {
          input.transferTo(output);
        }
        Files.move(temporary, target);
      } finally {
        Files.deleteIfExists(temporary);
      }
    } else
      try (SSHClient client = ssh.connect(device);
          SFTPClient sftp = client.newSFTPClient()) {
        String target = remote(sftp, device, path, true);
        if (sftp.statExistence(target) != null)
          throw new WorkspaceException(409, "같은 이름의 항목이 있습니다.");
        String temporary =
            target.substring(0, target.lastIndexOf('/') + 1) + ".upload-" + UUID.randomUUID();
        try {
          try (RemoteFile file =
                  sftp.open(temporary, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.EXCL));
              OutputStream output = file.new RemoteFileOutputStream()) {
            input.transferTo(output);
          }
          sftp.rename(temporary, target);
        } finally {
          if (sftp.statExistence(temporary) != null) sftp.rm(temporary);
        }
      }
  }

  public void rename(DeviceRecord device, String source, String destination) throws IOException {
    if (device.id().equals("local"))
      Files.move(local(device, source, false), local(device, destination, true));
    else
      try (SSHClient client = ssh.connect(device);
          SFTPClient sftp = client.newSFTPClient()) {
        String target = remote(sftp, device, destination, true);
        if (sftp.statExistence(target) != null)
          throw new WorkspaceException(409, "같은 이름의 항목이 있습니다.");
        sftp.rename(remote(sftp, device, source, false), target);
      }
  }

  public void delete(DeviceRecord device, String path) throws IOException {
    if (path.equals("/")) throw new WorkspaceException(400, "루트 폴더는 삭제할 수 없습니다.");
    if (device.id().equals("local")) Files.delete(local(device, path, false));
    else
      try (SSHClient client = ssh.connect(device);
          SFTPClient sftp = client.newSFTPClient()) {
        String target = remote(sftp, device, path, false);
        if (sftp.stat(target).getType() == FileMode.Type.DIRECTORY) sftp.rmdir(target);
        else sftp.rm(target);
      }
  }

  public FileDownload download(DeviceRecord device, String path) throws IOException {
    if (device.id().equals("local")) {
      Path file = local(device, path, false);
      if (!Files.isRegularFile(file)) throw new WorkspaceException(400, "일반 파일만 다운로드할 수 있습니다.");
      return new FileDownload(
          file.getFileName().toString(), Files.size(file), Files.newInputStream(file));
    }
    SSHClient client = ssh.connect(device);
    SFTPClient sftp = null;
    RemoteFile file = null;
    try {
      sftp = client.newSFTPClient();
      String target = remote(sftp, device, path, false);
      if (sftp.stat(target).getType() != FileMode.Type.REGULAR)
        throw new WorkspaceException(400, "일반 파일만 다운로드할 수 있습니다.");
      file = sftp.open(target);
      long size = file.length();
      SFTPClient finalSftp = sftp;
      RemoteFile finalFile = file;
      InputStream stream =
          new FilterInputStream(file.new RemoteFileInputStream()) {
            @Override
            public void close() throws IOException {
              try {
                super.close();
              } finally {
                try {
                  finalFile.close();
                } finally {
                  try {
                    finalSftp.close();
                  } finally {
                    client.close();
                  }
                }
              }
            }
          };
      return new FileDownload(path.substring(path.lastIndexOf('/') + 1), size, stream);
    } catch (Exception exception) {
      if (file != null) file.close();
      if (sftp != null) sftp.close();
      client.close();
      throw exception;
    }
  }

  private Path local(DeviceRecord device, String path, boolean creating) throws IOException {
    Path root = Path.of(device.rootPath()).toRealPath();
    Path target = root.resolve(path.substring(1)).normalize();
    if (!target.startsWith(root)) throw new WorkspaceException(403, "허용된 파일 경로를 벗어났습니다.");
    Path resolved =
        creating
            ? target.getParent().toRealPath().resolve(target.getFileName())
            : target.toRealPath();
    if (!resolved.startsWith(root) || Files.isSymbolicLink(target))
      throw new WorkspaceException(403, "허용된 파일 경로를 벗어났습니다.");
    return resolved;
  }

  private String remote(SFTPClient sftp, DeviceRecord device, String path, boolean creating)
      throws IOException {
    String root = sftp.canonicalize(device.rootPath());
    String target = (root.equals("/") ? "" : root) + path;
    String resolved =
        creating
            ? sftp.canonicalize(target.substring(0, target.lastIndexOf('/')))
                + target.substring(target.lastIndexOf('/'))
            : sftp.canonicalize(target);
    if (!root.equals("/") && !resolved.equals(root) && !resolved.startsWith(root + "/"))
      throw new WorkspaceException(403, "허용된 파일 경로를 벗어났습니다.");
    return resolved;
  }

  private String child(String path, String name) {
    return (path.equals("/") ? "" : path) + "/" + name;
  }
}
