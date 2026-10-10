FROM ubuntu:24.04
ENV DEBIAN_FRONTEND=noninteractive
RUN dpkg --add-architecture i386 && apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl locales python3 \
    && mkdir -p /etc/apt/keyrings \
    && curl --fail --location --proto '=https' https://dl.winehq.org/wine-builds/winehq.key -o /etc/apt/keyrings/winehq-archive.key \
    && curl --fail --location --proto '=https' https://dl.winehq.org/wine-builds/ubuntu/dists/noble/winehq-noble.sources -o /etc/apt/sources.list.d/winehq.sources \
    && apt-get update && apt-get install -y --install-recommends winehq-stable \
    && apt-get install -y --no-install-recommends tigervnc-standalone-server openbox dbus-x11 tini util-linux x11-utils xdotool fonts-nanum fonts-noto-cjk \
    && locale-gen ko_KR.UTF-8 && rm -rf /var/lib/apt/lists/* \
    && useradd -m -u 10001 browser \
    && mkdir -p /home/browser/profile /run/communication-bridge /opt/kakaotalk \
    && chown -R browser:browser /home/browser /run/communication-bridge
# Official installer, reviewed SHA-256. Update deliberately when the vendor replaces it.
RUN curl --fail --location --retry 3 --connect-timeout 15 --max-time 300 --proto '=https' --proto-redir '=https' \
    https://app-pc.kakaocdn.net/talk/win32/KakaoTalk_Setup.exe -o /opt/kakaotalk/setup.exe \
    && echo 'f3b21b92dbe17f13e8606a91df2e2e32ef4ffd4054b007242e6be32e45bf7e2c  /opt/kakaotalk/setup.exe' | sha256sum -c -
COPY docker/browser/communication_bridge.py /usr/local/lib/communication_bridge.py
COPY docker/wine/wine_session.py /usr/local/lib/wine_session.py
USER browser
ENV HOME=/home/browser LANG=ko_KR.UTF-8 LC_ALL=ko_KR.UTF-8 WINEDEBUG=-all COMMUNICATION_RUNTIME=wine
ENTRYPOINT ["/usr/bin/tini", "-g", "--", "python3", "/usr/local/lib/communication_bridge.py"]
