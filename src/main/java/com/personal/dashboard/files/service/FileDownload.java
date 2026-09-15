package com.personal.dashboard.files.service;

import java.io.InputStream;

/** Streaming download result; closing the stream also closes the remote connection. */
public record FileDownload(String name, long size, InputStream stream) {}
