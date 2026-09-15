'use strict';
window.WorkspaceNas=(()=>{
 let ui;
 function validUrl(value){try{const url=new URL(value);return ['http:','https:'].includes(url.protocol)&&!url.username&&!url.password&&!url.search&&!url.hash?url.href:null;}catch{return null;}}
 return {
  init(helpers){ui=helpers;},
  async open(){
   const settings=await ui.api('/cloud/nas');
   const url=validUrl(settings.publicUrl||location.origin+settings.path);
   if(!url)throw new Error('NAS_PUBLIC_URL을 올바른 HTTP/HTTPS WebDAV 주소로 설정하세요.');
   const esc=ui.escape;
   ui.editor('NAS · 네트워크 드라이브',`<div class="nas-connection"><p class="section-hint">클라우드 드라이브와 같은 파일을 기기에서 읽고 씁니다. 삭제한 파일은 휴지통에 보관됩니다.</p><p><b>${settings.enabled?'WebDAV 사용 가능':'비활성화 · NAS_ENABLED 설정 확인'}</b></p><label>서버 주소<input value="${esc(url)}" readonly data-nas-url></label><button type="button" data-nas-copy>주소 복사</button><label>사용자 이름<input value="${esc(settings.username)}" readonly></label><p>비밀번호는 대시보드 로그인 비밀번호를 사용하세요.</p><p class="section-hint">다른 기기에서는 localhost 대신 접근 가능한 서버 주소를 사용하세요. 외부에서는 서버와 기기를 Tailscale에 연결하거나 신뢰할 수 있는 HTTPS 주소로 접속하세요.</p><label>기기<select data-nas-device><option value="windows">Windows</option><option value="mac">macOS</option><option value="linux">Linux</option><option value="mobile">iPhone / Android</option></select></label><div data-nas-guide></div><p data-nas-message role="status"></p><p class="section-hint">Windows 기본 WebDAV 연결은 HTTPS와 WebClient 서비스가 필요합니다. NAS_PUBLIC_URL에 외부 HTTPS 주소를 지정할 수 있습니다. Tailscale IP를 사용할 때는 이 서비스의 8080 포트와 /dav/ 경로를 사용하세요.</p></div>`,async()=>{},'닫기');
   const root=document.querySelector('.nas-connection'),guide=root.querySelector('[data-nas-guide]');
   const guides={windows:'파일 탐색기 → 내 PC → 네트워크 위치 추가에서 위 HTTPS 주소를 입력하고 사용자 이름·비밀번호로 연결하세요. 기본 WebDAV 클라이언트의 파일 크기/자동 재연결 제한이 있을 수 있습니다.',mac:'Finder → 이동 → 서버에 연결(⌘K) → 위 주소 입력 → 등록 사용자로 인증하세요. Finder에서 네트워크 드라이브로 사용할 수 있습니다.',linux:'davfs2 또는 파일 관리자의 WebDAV 연결을 사용하세요. davfs2 마운트 예시: sudo mount -t davfs <서버 주소> <로컬 마운트 폴더>. 사용자 이름과 비밀번호는 요청될 때 입력하세요.',mobile:'WebDAV를 지원하는 파일 앱에서 서버를 추가하고 위 주소와 계정을 입력하세요. 휴대폰의 시스템 전체 마운트는 운영체제/앱에 따라 제한됩니다. iPhone 기본 파일 앱의 SMB 서버 입력란에 WebDAV 주소를 넣는 방식은 지원되지 않습니다.'};
   function show(){guide.textContent=guides[root.querySelector('[data-nas-device]').value];}root.querySelector('[data-nas-device]').onchange=show;show();
   root.querySelector('[data-nas-copy]').onclick=async()=>{try{await navigator.clipboard.writeText(url);root.querySelector('[data-nas-message]').textContent='주소를 복사했습니다.';}catch{root.querySelector('[data-nas-url]').select();root.querySelector('[data-nas-message]').textContent='주소를 선택했습니다. 직접 복사하세요.';}};
  }
 };
})();
