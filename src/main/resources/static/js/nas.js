window.WorkspaceNas=(()=>{
 let dependencies;
 function init(value){dependencies=value;}
 async function open(){
  const {api,escape:esc,editor,toast}=dependencies;
  try{
   const settings=await api('/cloud/nas');
   const host=settings.host||'NAS_HOST';
   const unc=`\\\\${host}\\${settings.share}`;
   editor('NAS · SMB 네트워크 드라이브',`<div class="nas-connection"><p class="section-hint">NAS 저장 공간을 SMB3 파일시스템으로 연결합니다. 대시보드 로그인과 별도 NAS 계정을 사용합니다.</p><label>Windows 경로<input value="${esc(unc)}" readonly data-nas-path></label><button type="button" data-nas-copy>경로 복사</button><label>사용자 이름<input value="${esc(settings.username)}" readonly></label><label>기기<select data-nas-device><option value="windows">Windows</option><option value="linux">Linux</option><option value="android">Android</option></select></label><div data-nas-guide></div><p data-nas-message role="status"></p><p class="section-hint">SMB는 LAN 또는 VPN에서만 사용하세요. 인터넷에 TCP 445를 공개하지 마세요. 연결 정보는 NAS_SMB_HOST 및 별도 NAS 계정으로 관리합니다.</p></div>`,async()=>{},'닫기');
   const root=document.querySelector('.nas-connection'),guide=root.querySelector('[data-nas-guide]');
   const guides={windows:`파일 탐색기의 주소창에 ${unc}을 입력해 연결하세요. 네트워크 드라이브로 등록하려면 이 PC → 네트워크 드라이브 연결에서 드라이브 문자를 선택하고 폴더에 경로를 입력한 뒤 “다른 자격 증명을 사용하여 연결”을 선택하세요. 로그인 시 다시 연결을 켜면 재부팅 후 자동 연결됩니다.`,linux:`CIFS 유틸리티를 설치하고 다음처럼 마운트하세요: sudo mount -t cifs //${host}/${settings.share} /mnt/nas -o username=${settings.username},vers=3.1.1. 자동 마운트는 root 소유 권한 0600인 credentials 파일을 사용하고, fstab에는 credentials=/etc/samba/credentials-nas,vers=3.1.1,_netdev,nofail을 지정하세요.`,android:`SMB3를 지원하는 파일 관리자에서 호스트 ${host}, 포트 445, 공유 ${settings.share}, 사용자 ${settings.username}, NAS 비밀번호로 연결하세요. Android 전용 서버 구성은 필요하지 않습니다.`};
   function show(){guide.textContent=guides[root.querySelector('[data-nas-device]').value];}root.querySelector('[data-nas-device]').onchange=show;show();
   root.querySelector('[data-nas-copy]').onclick=async()=>{try{await navigator.clipboard.writeText(unc);root.querySelector('[data-nas-message]').textContent='경로를 복사했습니다.';}catch{root.querySelector('[data-nas-path]').select();root.querySelector('[data-nas-message]').textContent='경로를 선택했습니다. 직접 복사하세요.';}};
  }catch(error){toast(error.message||'NAS 설정을 불러오지 못했습니다.');}
 }
 return{init,open};
})();
