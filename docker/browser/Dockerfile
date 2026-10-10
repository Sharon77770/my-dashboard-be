FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends chromium tigervnc-standalone-server openbox dbus-x11 socat fonts-noto-cjk ca-certificates curl util-linux python3 \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -m -u 10001 browser \
    && mkdir -p /home/browser/profile \
    && mkdir -p /run/communication-bridge \
    && chown browser:browser /run/communication-bridge \
    && chmod 700 /run/communication-bridge \
    && chown -R browser:browser /home/browser
COPY start.sh /usr/local/bin/workspace-browser
COPY communication_bridge.py /usr/local/lib/communication_bridge.py
COPY shutdown.py /usr/local/lib/workspace-browser-shutdown.py
RUN chmod 755 /usr/local/bin/workspace-browser
USER browser
ENV DISPLAY=:1
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 CMD curl -fsS --max-time 2 http://127.0.0.1:9223/json/version >/dev/null || exit 1
ENTRYPOINT ["/usr/local/bin/workspace-browser"]
