FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends chromium tigervnc-standalone-server openbox dbus-x11 socat fonts-noto-cjk ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -m -u 10001 browser \
    && mkdir -p /home/browser/profile \
    && chown -R browser:browser /home/browser
COPY start.sh /usr/local/bin/workspace-browser
RUN chmod 755 /usr/local/bin/workspace-browser
USER browser
ENV DISPLAY=:1
ENTRYPOINT ["/usr/local/bin/workspace-browser"]
