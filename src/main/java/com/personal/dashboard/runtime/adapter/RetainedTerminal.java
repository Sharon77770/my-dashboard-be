package com.personal.dashboard.runtime.adapter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Drains a Studio PTY independently of its browser socket and retains bounded reconnect output. */
public final class RetainedTerminal implements TerminalAdapter.Connection {
  private final TerminalAdapter.Connection terminal;
  private final StringBuilder replay = new StringBuilder();
  private Consumer<String> subscriber;
  private Runnable disconnected;
  private boolean started, closed;

  public RetainedTerminal(TerminalAdapter.Connection terminal) {
    this.terminal = terminal;
  }

  public synchronized void attach(Consumer<String> output, Runnable disconnect, Runnable exited) {
    if (closed) throw new IllegalStateException("Terminal has exited");
    subscriber = output;
    disconnected = disconnect;
    output.accept(replay.toString());
    if (!started) {
      started = true;
      Thread.startVirtualThread(() -> drain(exited));
    }
  }

  public synchronized void detach() {
    subscriber = null;
    disconnected = null;
  }

  private void drain(Runnable exited) {
    try (Reader reader = new InputStreamReader(terminal.output(), StandardCharsets.UTF_8)) {
      char[] buffer = new char[4096];
      int count;
      while ((count = reader.read(buffer)) != -1) {
        synchronized (this) {
          String text = new String(buffer, 0, count);
          replay.append(text);
          if (replay.length() > 65536) replay.delete(0, replay.length() - 65536);
          if (subscriber != null) {
            try {
              subscriber.accept(text);
            } catch (RuntimeException exception) {
              Runnable disconnect = disconnected;
              detach();
              if (disconnect != null) Thread.startVirtualThread(disconnect);
            }
          }
        }
      }
    } catch (IOException ignored) {
      // PTY EOF and transport failure both terminate this shell, never silently create another.
    } finally {
      exited.run();
    }
  }

  public InputStream output() {
    throw new UnsupportedOperationException("Use attach");
  }

  public OutputStream input() {
    return terminal.input();
  }

  public void resize(int columns, int rows) throws Exception {
    terminal.resize(columns, rows);
  }

  public void close() throws Exception {
    Runnable disconnect;
    synchronized (this) {
      if (closed) return;
      closed = true;
      disconnect = disconnected;
      detach();
    }
    try {
      terminal.close();
    } finally {
      if (disconnect != null) disconnect.run();
    }
  }
}
