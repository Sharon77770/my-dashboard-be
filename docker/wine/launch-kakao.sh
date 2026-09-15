#!/bin/bash
set -eu
umask 077
exec 9>"$HOME/.kakao-launch.lock"
flock -n 9 || exit 0
kakao="$WINEPREFIX/drive_c/Program Files (x86)/Kakao/KakaoTalk/KakaoTalk.exe"
if [ ! -f "$kakao" ]; then
    kakao="$WINEPREFIX/drive_c/Program Files/Kakao/KakaoTalk/KakaoTalk.exe"
fi
if [ ! -f "$kakao" ]; then
    # Official installer runs inside the user's persistent Wine prefix.
    wine /opt/kakaotalk/setup.exe /S >"$HOME/install.log" 2>&1 || true
    # Finish the installer's Wine session before provisioning native DLLs.
    wineserver -k
    wineserver -w
    kakao="$WINEPREFIX/drive_c/Program Files (x86)/Kakao/KakaoTalk/KakaoTalk.exe"
fi
if [ ! -f "$kakao" ]; then
    kakao="$WINEPREFIX/drive_c/Program Files/Kakao/KakaoTalk/KakaoTalk.exe"
fi
if [ -f "$kakao" ]; then
    # The 32-bit native GDI+ renders KakaoTalk's layered popup controls.
    for gdiplus in "$WINEPREFIX/drive_c/windows/syswow64/gdiplus.dll" "$(dirname "$kakao")/gdiplus.dll"; do
        if ! cmp -s /opt/kakaotalk/gdiplus.dll "$gdiplus"; then
            if [ -L "$gdiplus" ]; then rm "$gdiplus"; fi
            cp /opt/kakaotalk/gdiplus.dll "$gdiplus"
        fi
    done
    # Let KakaoTalk's own single-instance handler restore a closed/minimized window.
    flock -u 9
    exec 9>&-
    WINEDLLOVERRIDES='gdiplus=n' wine "$kakao" >/dev/null 2>&1
else
    zenity --error --title='카카오톡 설치' --text='자동 설치를 완료하지 못했습니다. 바탕화면을 오른쪽 클릭한 뒤 카카오톡 수동 설치를 선택해 주세요.'
fi
