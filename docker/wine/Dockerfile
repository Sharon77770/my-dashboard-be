FROM ubuntu:24.04 AS gdiplus
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl cabextract \
    && curl --fail --location --retry 3 --connect-timeout 15 --max-time 600 --proto '=https' --proto-redir '=https' \
      https://catalog.s.download.windowsupdate.com/msdownload/update/software/svpk/2011/02/windows6.1-kb976932-x86_c3516bc5c9e69fee6d9ac4f981f5b95977a8a2fa.exe -o /tmp/windows-sp1.exe \
    && echo 'e5449839955a22fc4dd596291aff1433b998f9797e1c784232226aba1f8abd97  /tmp/windows-sp1.exe' | sha256sum -c - \
    && mkdir /native \
    && cabextract -q -L -d /native -F 'x86_microsoft.windows.gdiplus_6595b64144ccf1df_1.1.7601.17514_none_72d18a4386696c80/gdiplus.dll' /tmp/windows-sp1.exe

FROM ubuntu:24.04
ENV DEBIAN_FRONTEND=noninteractive
RUN dpkg --add-architecture i386 \
    && apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl locales \
    && mkdir -p /etc/apt/keyrings \
    && curl --fail --location --proto '=https' https://dl.winehq.org/wine-builds/winehq.key -o /etc/apt/keyrings/winehq-archive.key \
    && curl --fail --location --proto '=https' https://dl.winehq.org/wine-builds/ubuntu/dists/noble/winehq-noble.sources -o /etc/apt/sources.list.d/winehq.sources \
    && apt-get update \
    && apt-get install -y --install-recommends winehq-stable \
    && apt-get install -y --no-install-recommends tigervnc-standalone-server openbox dbus-x11 tini util-linux x11-utils x11-apps xdotool xterm zenity fcitx5 fcitx5-hangul fonts-nanum fonts-noto-cjk \
    && locale-gen ko_KR.UTF-8 \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -m -u 10001 -s /bin/bash wine \
    && mkdir -p /opt/kakaotalk /home/wine/.config \
    && chown -R wine:wine /home/wine
ARG KAKAO_INSTALLER_SHA256=d63c10a1530aafef1f485436aa4ec93322a0ee1e14ea32f97119222d559ccd96
RUN apt-get update \
    && apt-get install -y --no-install-recommends xcompmgr xvfb x11vnc \
    && rm -rf /var/lib/apt/lists/*
RUN test -n "$KAKAO_INSTALLER_SHA256" \
    && curl --fail --location --retry 3 --connect-timeout 15 --max-time 300 --proto '=https' --proto-redir '=https' https://app-pc.kakaocdn.net/talk/win32/KakaoTalk_Setup.exe -o /opt/kakaotalk/setup.exe \
    && echo "$KAKAO_INSTALLER_SHA256  /opt/kakaotalk/setup.exe" | sha256sum -c -
COPY start.sh /usr/local/bin/wine-desktop
COPY --from=gdiplus /native/x86_microsoft.windows.gdiplus_6595b64144ccf1df_1.1.7601.17514_none_72d18a4386696c80/gdiplus.dll /opt/kakaotalk/gdiplus.dll
COPY launch-kakao.sh /usr/local/bin/launch-kakao
COPY config/ /opt/wine-config/
RUN chmod 755 /usr/local/bin/wine-desktop /usr/local/bin/launch-kakao
USER wine
ENV HOME=/home/wine DISPLAY=:2 LANG=ko_KR.UTF-8 LC_ALL=ko_KR.UTF-8 \
    WINEPREFIX=/home/wine/prefix WINEARCH=win64 WINEDEBUG=-all \
    XMODIFIERS=@im=fcitx GTK_IM_MODULE=fcitx QT_IM_MODULE=fcitx XDG_RUNTIME_DIR=/tmp/runtime-wine
ENTRYPOINT ["/usr/bin/tini", "-g", "--", "dbus-run-session", "--", "/usr/local/bin/wine-desktop"]
