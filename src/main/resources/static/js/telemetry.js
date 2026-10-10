'use strict';
/** OWNER interface for service keys, integration snippets and compact analytics. */
window.WorkspaceTelemetry=(()=>{
  const root=()=>document.querySelector('#telemetry-content'),escape=window.WorkspaceUI.escape;
  let services=[],selected=null,range='24h',integration='FastAPI',screen='list',generation=0;
  const csrf=()=>({[document.querySelector('meta[name=csrf-header]').content]:document.querySelector('meta[name=csrf-token]').content,'Content-Type':'application/json'});
  async function api(path,method='GET',body,options={}){
    const finishTask=options.quiet?()=>{}:window.WorkspaceUI.beginTask?.(method==='GET'?'통계 정보를 불러오는 중…':'통계 설정을 저장하는 중…')||(()=>{});
    try{
      const response=await fetch('/api/v1/telemetry'+path,{method,headers:method==='GET'?{}:csrf(),body:body?JSON.stringify(body):undefined});
      if(!response.ok){let message='Telemetry 요청에 실패했습니다.';try{message=(await response.json()).message||message}catch{}throw new Error(message)}
      return response.status===204?null:await response.json();
    }finally{finishTask();}
  }
  function metric(value,suffix=''){return value==null?'데이터 없음':`${Number(value).toLocaleString(undefined,{maximumFractionDigits:2})}${suffix}`}
  const paint=(html,options={})=>options.quiet&&window.WorkspaceLiveDOM?window.WorkspaceLiveDOM.patch(root(),html):root().innerHTML=html;
  function renderList(options={}){screen='list';selected=null;root().closest('#telemetry').classList.remove('telemetry-detail-open');paint(`<div class="telemetry-toolbar"><p>서비스는 API Key로 telemetry를 전송합니다. 실제 사용자 식별 정보는 보내지 마세요.</p></div><div class="telemetry-grid">${services.map(s=>`<article class="panel telemetry-card" data-live-key="${escape(s.serviceId)}"><button class="telemetry-card-open" data-telemetry="open" data-id="${escape(s.serviceId)}"><header><span class="telemetry-dot ${s.status==='Receiving data'?'online':''}"></span><b>${escape(s.serviceName)}</b><small class="ui-status" data-state="${s.status==='Receiving data'?'success':'neutral'}">${escape(s.status)}</small></header><div class="telemetry-stats"><div><small>오늘 요청</small><b>${metric(s.requestsToday)}</b></div><div><small>DAU</small><b>${metric(s.dau)}</b></div><div><small>오류율</small><b>${s.errorRate==null?'데이터 없음':metric(s.errorRate,'%')}</b></div><div><small>마지막 수신</small><b>${s.lastUsedAt?new Date(s.lastUsedAt).toLocaleString():'아직 없음'}</b></div></div></button><footer><span>${escape(s.serviceType)}</span><button data-telemetry="guide" data-id="${escape(s.serviceId)}" aria-label="${escape(s.serviceName)} 연동 가이드" title="연동 가이드">${window.WorkspaceUI.icon('info')}<span>Integration</span></button></footer></article>`).join('')||'<div class="panel telemetry-empty"><h2>첫 서비스를 등록하세요</h2><p>서비스별 API Key를 발급하면 이벤트와 순간 지표를 전송할 수 있습니다.</p></div>'}</div>`,options);}
  async function load(options={}){const version=++generation;screen='list';const next=await api('/services','GET',undefined,options);if(version!==generation)return;services=next;window.WorkspaceWidgets?.updateTelemetry(services);renderList(options)}
  async function open(id,options={}){
    const version=++generation;screen='analytics';selected=id;
    const result=await api(`/services/${encodeURIComponent(id)}/analytics?range=${range}`,'GET',undefined,options),s=result.summary;
    if(version!==generation)return;
    root().closest('#telemetry').classList.add('telemetry-detail-open');
    const hasTelemetry=s.lastUsedAt!=null;
    const lastUsed=s.lastUsedAt?new Date(s.lastUsedAt).toLocaleString():'아직 수신 없음';
    const menu=`<details class="telemetry-menu"><summary aria-label="서비스 설정" title="서비스 설정">${window.WorkspaceUI.icon('more')}</summary><div><button data-telemetry="guide" data-id="${escape(id)}">Integration 가이드</button><button data-telemetry="key" data-id="${escape(id)}">새 API Key</button><button data-telemetry="revoke" data-id="${escape(id)}">Key 폐기</button><button data-telemetry="toggle" data-id="${escape(id)}" data-enabled="${s.enabled}">${s.enabled?'서비스 비활성화':'서비스 활성화'}</button></div></details>`;
    const header=`<header class="telemetry-service-head"><button class="telemetry-back" data-telemetry="back" aria-label="Telemetry 서비스 목록으로" title="Telemetry 서비스 목록으로">${window.WorkspaceUI.icon('back')}<span>서비스</span></button><div class="telemetry-service-identity"><span class="telemetry-status ${s.enabled?'online':'offline'}"></span><div><h2>${escape(s.serviceName)}</h2><p>${escape(s.serviceType)} <span>·</span> ${escape(s.status)} <span>·</span> 마지막 수신 ${lastUsed}</p></div></div>${menu}</header>`;
    if(!hasTelemetry){paint(`<div class="telemetry-detail">${header}<section class="telemetry-onboarding"><span class="telemetry-onboarding-mark">↗</span><p class="eyebrow">GETTING STARTED</p><h2>아직 수신된 Telemetry 데이터가 없습니다.</h2><p>이 서비스에서 첫 이벤트를 보내면<br>요청량, 사용자, 오류율, latency 등의 분석이 여기에 표시됩니다.</p><div class="telemetry-empty-actions"><button class="primary" data-telemetry="guide" data-id="${escape(id)}">연동 가이드</button><button data-telemetry="guide" data-id="${escape(id)}">테스트 이벤트 보내기</button></div><small>첫 이벤트가 도착하면 이 화면이 Analytics 대시보드로 전환됩니다.</small></section></div>`,options);return;}
    const kpis=[['Requests',s.requests],['Active Users',s.uniqueUsers],['Error Rate',s.errorRate==null?null:`${s.errorRate.toFixed(2)}%`],['p95 Latency',s.p95LatencyMs==null?null:`${s.p95LatencyMs.toFixed(1)} ms`]];
    const peak=Math.max(1,...result.timeline.map(point=>point.requests||0));
    const timeline=result.timeline.map(point=>`<div class="telemetry-bar" title="${new Date(point.timestamp).toLocaleString()} · ${point.requests} requests · ${point.errors} errors" style="--bar:${Math.max(2,Math.min(100,(point.requests||0)/peak*100))}%"><i style="--error:${point.requests?Math.min(100,(point.errors||0)/point.requests*100):0}%"></i></div>`).join('');
    paint(`<div class="telemetry-detail">${header}<section class="telemetry-kpi-row">${kpis.map(([label,value])=>`<div class="telemetry-kpi"><small>${label}</small><b>${value==null?'—':typeof value==='number'?value.toLocaleString():value}</b></div>`).join('')}</section><section class="telemetry-primary"><div class="telemetry-section-head"><div><p class="eyebrow">TRAFFIC</p><h3>Request Activity</h3><p>${range==='1h'?'최근 1시간':range==='24h'?'최근 24시간':range==='7d'?'최근 7일':'최근 30일'} 요청 흐름</p></div><div class="telemetry-ranges">${[['1h','1H'],['24h','24H'],['7d','7D'],['30d','30D']].map(([v,label])=>`<button class="${range===v?'selected':''}" data-telemetry="range" data-range="${v}">${label}</button>`).join('')}<button type="button" title="사용자 지정 기간은 현재 지원되는 범위가 아닙니다." disabled class="telemetry-range-disabled">Custom</button></div></div><div class="telemetry-chart" role="img" aria-label="선택 기간의 요청 및 오류 activity">${timeline||'<p class="telemetry-inline-empty">선택한 기간에 요청 데이터가 없습니다.</p>'}</div><div class="telemetry-chart-legend"><span><i></i>Requests</span><span><i></i>Errors</span><small>${result.timeline.length?`${result.timeline.length} time intervals`:''}</small></div></section><section class="telemetry-analytics-grid"><article class="telemetry-surface telemetry-endpoints"><div class="telemetry-section-head"><div><p class="eyebrow">ROUTES</p><h3>Endpoint breakdown</h3></div></div>${rows(result.endpoints,result.endpointErrorRates,'요청 데이터가 없습니다')}</article><article class="telemetry-surface"><div class="telemetry-section-head"><div><p class="eyebrow">HEALTH</p><h3>Status distribution</h3></div><b class="telemetry-surface-value">${(s.errors||0).toLocaleString()} <small>errors</small></b></div>${rows(result.statuses,{},'요청 데이터가 없습니다')}</article><article class="telemetry-surface"><div class="telemetry-section-head"><div><p class="eyebrow">AUDIENCE</p><h3>Users</h3></div></div>${s.uniqueUsers==null||s.uniqueUsers===0?'<p class="telemetry-inline-empty">User tracking not configured</p>':`<div class="telemetry-user-stats"><div><small>Unique users</small><b>${metric(s.uniqueUsers)}</b></div><div><small>DAU / WAU / MAU</small><b>${[s.dau,s.wau,s.mau].map(v=>v==null?'—':v.toLocaleString()).join(' / ')}</b></div><div><small>Peak concurrent</small><b>${metric(s.peakConcurrentUsers)}</b></div></div>`}</article><article class="telemetry-surface"><div class="telemetry-section-head"><div><p class="eyebrow">PERFORMANCE</p><h3>Latency</h3></div></div><div class="telemetry-latency">${[['Average',s.averageLatencyMs],['p50',s.p50LatencyMs],['p95',s.p95LatencyMs],['p99',s.p99LatencyMs]].map(([name,value])=>`<div><small>${name}</small><b>${value==null?'—':`${value.toFixed(1)}<em>ms</em>`}</b></div>`).join('')}</div></article><article class="telemetry-surface telemetry-methods"><div class="telemetry-section-head"><div><p class="eyebrow">REQUEST MIX</p><h3>HTTP methods</h3></div></div>${rows(result.methods,{},'요청 데이터가 없습니다')}</article><article class="telemetry-surface telemetry-custom"><div class="telemetry-section-head"><div><p class="eyebrow">CUSTOM</p><h3>Custom metrics</h3></div></div>${Object.keys(s.gauges||{}).length?`<div class="telemetry-gauges">${Object.entries(s.gauges).map(([name,value])=>`<div><small>${escape(name)}</small><b>${metric(value)}</b></div>`).join('')}</div>`:'<p class="telemetry-inline-empty">수신된 custom metrics가 없습니다.</p>'}</article></section></div>`,options);
  }
  function rows(values,rates={},empty='데이터 없음'){return Object.keys(values||{}).length?`<ul>${Object.entries(values).map(([key,value])=>`<li><span>${escape(key)}</span><b>${value.toLocaleString()}${rates[key]==null?'':` <small>· 오류 ${rates[key].toFixed(2)}%</small>`}</b></li>`).join('')}</ul>`:`<p class="telemetry-inline-empty">${empty}</p>`}
  function showKey(title,key){const dialog=document.querySelector('#telemetry-key-dialog');dialog.innerHTML=`<form method="dialog" class="panel"><h2>${title}</h2><p>이 키는 지금만 표시됩니다. 안전한 secret 저장소에 복사하세요.</p><label>API Key<input readonly value="${escape(key)}"></label><button class="primary">복사 완료</button></form>`;dialog.showModal();dialog.querySelector('input').select();}
  function guide(id){generation++;screen='guide';const endpoint=`${location.origin}/api/v1/telemetry`,service=services.find(s=>s.serviceId===id);const snippets={FastAPI:`# Fire-and-forget example; never await telemetry from request handling.
import asyncio, os, httpx, time
async def send_telemetry(payload):
    try:
        async with httpx.AsyncClient(timeout=1.5) as client:
            await client.post(os.environ["DASHBOARD_TELEMETRY_URL"]+"/events", json=payload,
                headers={"Authorization": "Bearer "+os.environ["DASHBOARD_API_KEY"]})
    except Exception:
        pass  # telemetry must never affect the service response
from starlette.middleware.base import BaseHTTPMiddleware
class TelemetryMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request, call_next):
        started = time.perf_counter()
        response = await call_next(request)
        if not request.url.path.startswith("/api/v1/telemetry"):
            event = {"type":"request", "properties":{"endpoint":request.url.path,
                "method":request.method, "status":response.status_code,
                "latencyMs":int((time.perf_counter()-started)*1000)}}
            asyncio.create_task(send_telemetry(event))  # never await this task
        return response`,
Spring:`// In a OncePerRequestFilter, call chain.doFilter first and schedule after the response.
long started = System.nanoTime();
chain.doFilter(request, response);
if (!request.getRequestURI().startsWith("/api/v1/telemetry")) {
  Map<String,Object> event = Map.of("type", "request", "properties", Map.of(
    "endpoint", request.getRequestURI(), "method", request.getMethod(),
    "status", response.getStatus(), "latencyMs", (System.nanoTime()-started)/1_000_000));
  try { telemetryExecutor.execute(() -> { try {
      telemetryClient.post().uri(telemetryUrl+"/events").header("Authorization", "Bearer "+apiKey)
        .body(event).retrieve().toBodilessEntity();
    } catch (Exception ignored) { /* telemetry failure is intentionally ignored */ } });
  } catch (Exception ignored) { /* bounded executor may be full or shutting down */ }
}`,
Node:`// Express middleware: call next immediately; send telemetry after response finishes.
app.use((req, res, next) => {
  const started = Date.now();
  res.once("finish", () => {
    if (req.path.startsWith("/api/v1/telemetry")) return;
    const event = {type:"request", properties:{endpoint:req.path, method:req.method,
      status:res.statusCode, latencyMs:Date.now()-started}};
    void fetch(process.env.DASHBOARD_TELEMETRY_URL+"/events", {
      method:"POST", signal:AbortSignal.timeout(1500),
      headers:{"content-type":"application/json", "authorization":"Bearer "+process.env.DASHBOARD_API_KEY},
      body:JSON.stringify(event)
    }).catch(() => {});
  });
  next();
});`,
Raw:`curl --max-time 2 -X POST "$DASHBOARD_TELEMETRY_URL/events" \\
  -H "Authorization: Bearer $DASHBOARD_API_KEY" -H "Content-Type: application/json" \\
  -d '{"type":"request","properties":{"endpoint":"/api/example","method":"GET","status":200,"latencyMs":42}}'`};
  root().closest('#telemetry').classList.add('telemetry-detail-open');
  root().innerHTML=`<div class="telemetry-toolbar"><button data-telemetry="back" aria-label="서비스 목록으로" title="서비스 목록으로">${window.WorkspaceUI.icon('back')}<span>서비스</span></button><h2>${escape(service?.serviceName||id)} · Integration</h2></div><article class="panel telemetry-guide"><p>전송은 응답 경로와 분리하고 짧은 timeout을 사용하세요. 오류는 무시하고 필요하면 로컬 버퍼에 모아 batch로 보냅니다.</p><p>Service ID: <code>${escape(id)}</code><br>Endpoint: <code>${escape(endpoint)}</code></p><pre> DASHBOARD_TELEMETRY_URL=${escape(endpoint)}
DASHBOARD_SERVICE_ID=${escape(id)}
DASHBOARD_API_KEY=dash_sk_&lt;store-the-key-as-a-secret&gt;</pre><nav>${Object.keys(snippets).map(k=>`<button data-telemetry="language" data-language="${k}">${k==='Raw'?'Raw HTTP':k}</button>`).join('')}</nav><pre id="telemetry-snippet">${escape(snippets[integration]||snippets.FastAPI)}</pre><button data-telemetry="copy">예제 복사</button><p>사용자 분석은 실제 ID 대신 서비스별 salt를 적용한 SHA-256 pseudonymous ID를 <code>user_activity</code> 이벤트에 넣으세요. 이메일, Discord ID, 사용자 이름은 전송하지 마세요.</p><pre>{"type":"user_activity","anonymousUserId":"&lt;sha256-pseudonymous-id&gt;"}</pre></article>`;}
  function create(){generation++;screen='create';root().closest('#telemetry').classList.add('telemetry-detail-open');root().innerHTML=`<div class="telemetry-toolbar"><button data-telemetry="back" aria-label="서비스 등록 취소" title="서비스 등록 취소">${window.WorkspaceUI.icon('back')}<span>취소</span></button><h2>서비스 등록</h2></div><form class="panel telemetry-form" id="telemetry-create-form"><label>서비스 이름<input name="name" required maxlength="100"></label><label>설명<textarea name="description" maxlength="500"></textarea></label><label>서비스 유형<select name="serviceType"><option>Backend API</option><option>Web</option><option>Discord Bot</option><option>Worker</option><option>Custom</option></select></label><button class="primary">서비스 만들기 및 API Key 발급</button></form>`;}
  document.addEventListener('submit',async event=>{if(event.target.id!=='telemetry-create-form')return;event.preventDefault();try{const f=new FormData(event.target),result=await api('/services','POST',{name:f.get('name'),description:f.get('description'),serviceType:f.get('serviceType')});services.unshift({...result,requests:0});window.WorkspaceWidgets?.updateTelemetry(services);showKey('서비스 API Key',result.apiKey);guide(result.serviceId)}catch(error){window.alert(error.message)}});
  document.addEventListener('click',async event=>{const button=event.target.closest('[data-telemetry]');if(!button)return;const action=button.dataset.telemetry;try{if(action==='create')create();if(action==='back')await load();if(action==='open'){range='24h';await open(button.dataset.id)}if(action==='guide')guide(button.dataset.id);if(action==='range'){range=button.dataset.range;await open(selected)}if(action==='language'){integration=button.dataset.language;guide(selected)}if(action==='copy')await navigator.clipboard.writeText(document.querySelector('#telemetry-snippet').textContent);if(action==='toggle'){await api(`/services/${button.dataset.id}/enabled`,'PUT',{enabled:button.dataset.enabled!=='true'});await open(button.dataset.id)}if(action==='key'){const result=await api(`/services/${button.dataset.id}/key`,'POST',{});showKey('새 API Key',result.apiKey)}if(action==='revoke'){if(confirm('이 서비스의 현재 API Key를 즉시 폐기할까요?')){await api(`/services/${button.dataset.id}/key`,'DELETE');alert('API Key를 폐기했습니다. 다시 전송하려면 새 키를 발급하세요.')}}}catch(error){window.alert(error.message)}});
  return {refresh(){if(screen==='analytics'&&selected)return open(selected,{quiet:true});if(screen==='list')return load({quiet:true});},open(id){if(id==='telemetry'){root().innerHTML=`<div class="telemetry-loading">${window.WorkspaceUI.skeleton(3)}</div>`;load().catch(error=>{root().innerHTML=window.WorkspaceUI.emptyState('분석을 불러오지 못했습니다.',error.message,'warning')+'<button data-telemetry="back">다시 시도</button>'})}}};
})();
