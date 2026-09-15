package com.personal.dashboard.nas.service;

import com.personal.dashboard.global.WorkspaceException;
import java.util.*;
import org.springframework.stereotype.Component;

/** Exclusive write leases shared by DAV and browser file mutations. */
@Component
public class DavLocks {
  public record Lease(String path, String token, boolean recursive, long expires) {}

  private final Map<String, Lease> leases = new LinkedHashMap<>();
  private final ThreadLocal<Set<String>> submitted = ThreadLocal.withInitial(Set::of);

  private void expire() {
    leases.values().removeIf(item -> item.expires() < System.currentTimeMillis());
  }

  private boolean under(String path, String parent) {
    return path.equals(parent) || path.startsWith(parent.equals("/") ? "/" : parent + "/");
  }

  private boolean covers(Lease item, String path) {
    return item.path().equals(path) || (item.recursive() && under(path, item.path()));
  }

  public synchronized void check(String path, boolean recursive) {
    expire();
    for (Lease item : leases.values())
      if ((covers(item, path) || (recursive && under(item.path(), path)))
          && !submitted.get().contains(item.token()))
        throw new WorkspaceException(423, "네트워크 드라이브에서 잠근 파일입니다. 해당 앱에서 닫거나 잠금 만료 후 다시 시도하세요.");
  }

  public synchronized Lease acquire(String path, boolean recursive, long seconds) {
    expire();
    if (leases.size() >= 256) throw new WorkspaceException(503, "파일 잠금 한도를 초과했습니다.");
    for (Lease item : leases.values())
      if (covers(item, path) || (recursive && under(item.path(), path)))
        throw new WorkspaceException(423, "이미 잠긴 경로입니다.");
    Lease lease =
        new Lease(
            path,
            "opaquelocktoken:" + UUID.randomUUID(),
            recursive,
            System.currentTimeMillis() + seconds * 1000);
    leases.put(lease.token(), lease);
    return lease;
  }

  public synchronized Lease refresh(String path, String token, long seconds) {
    expire();
    Lease old = leases.get(token);
    if (old == null || !old.path().equals(path))
      throw new WorkspaceException(412, "잠금이 만료되었거나 경로가 일치하지 않습니다.");
    Lease lease =
        new Lease(path, token, old.recursive(), System.currentTimeMillis() + seconds * 1000);
    leases.put(token, lease);
    return lease;
  }

  public synchronized void unlock(String path, String token) {
    expire();
    Lease item = leases.get(token);
    if (item == null || !item.path().equals(path))
      throw new WorkspaceException(409, "잠금을 찾을 수 없습니다.");
    leases.remove(token);
  }

  public synchronized boolean valid(String path, String token) {
    expire();
    Lease item = leases.get(token);
    return item != null && covers(item, path);
  }

  public synchronized List<Lease> discover(String path) {
    expire();
    return leases.values().stream().filter(item -> covers(item, path)).toList();
  }

  public synchronized void removed(String path) {
    leases.values().removeIf(item -> under(item.path(), path));
  }

  public Scope authorize(Set<String> tokens) {
    Set<String> previous = submitted.get();
    submitted.set(tokens);
    return () -> submitted.set(previous);
  }

  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
