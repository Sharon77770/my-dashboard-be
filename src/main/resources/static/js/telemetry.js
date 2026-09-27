'use strict';
/** OWNER interface for service keys, integration snippets and compact analytics. */
window.WorkspaceTelemetry=(()=>{
  const root=()=>document.querySelector('#telemetry-content'),escape=window.WorkspaceUI.escape;
  let services=[],selected=null,range='24h',integration='FastAPI';
  const csrf=()=>({[document.querySelector('meta[name=csrf-header]').content]:document.querySelector('meta[name=csrf-token]').content,'Content-Type':'application/json'});
  async function api(path,method='GET',body){const response=await fetch('/api/v1/telemetry'+path,{method,headers:method==='GET'?{}:csrf(),body:body?JSON.stringify(body):undefined});if(!response.ok){let message='Telemetry 요청에 실패했습니다.';try{message=(await response.json()).message||message}catch{}throw new Error(message)}return response.status===204?null:response.json()}
  function metric(value,suffix=''){return value==null?'데이터 없음':`${Number(value).toLocaleString(undefined,{maximumFractionDigits:2})}${suffix}`}
  function renderList(){selected=null;root().innerHTML=`<div class="telemetry-toolbar"><p>서비스는 API Key로 telemetry를 전송합니다. 실제 사용자 식별 정보는 보내지 마세요.</p><button class="primary" data-telemetry="create">서비스 등록</button></div><div class="telemetry-grid">${services.map(s=>`<article class="panel telemetry-card"><button class="telemetry-card-open" data-telemetry="open" data-id="${escape(s.serviceId)}"><header><span class="telemetry-dot ${s.status==='Receiving data'?'online':''}"></span><b>${escape(s.serviceName)}</b><small>${escape(s.status)}</small></header><div class="telemetry-stats"><div><small>오늘 요청</small><b>${metric(s.requestsToday)}</b></div><div><small>DAU</small><b>${metric(s.dau)}</b></div><div><small>오류율</small><b>${s.errorRate==null?'데이터 없음':metric(s.errorRate,'%')}</b></div><div><small>마지막 수신</small><b>${s.lastUsedAt?new Date(s.lastUsedAt).toLocaleString():'아직 없음'}</b></div></div></button><footer><span>${escape(s.serviceType)}</span><button data-telemetry="guide" data-id="${escape(s.serviceId)}">Integration</button></footer></article>`).join('')||'<div class="panel telemetry-empty"><h2>첫 서비스를 등록하세요</h2><p>서비스별 API Key를 발급하면 이벤트와 순간 지표를 전송할 수 있습니다.</p></div>'}</div>`;}
  async function load(){services=await api('/services');window.WorkspaceWidgets?.updateTelemetry(services);renderList()}
  async function open(id){selected=id;const result=await api(`/services/${encodeURIComponent(id)}/analytics?range=${range}`),s=result.summary;root().innerHTML=`<div class="telemetry-toolbar"><button data-telemetry="back">← 서비스</button><div class="telemetry-ranges">${['1h','24h','7d','30d'].map(v=>`<button class="${range===v?'selected':''}" data-telemetry="range" data-range="${v}">${v}</button>`).join('')}</div><button data-telemetry="guide" data-id="${escape(id)}">Integration</button></div><article class="panel telemetry-detail"><header><div><span class="eyebrow">${escape(s.serviceType||'SERVICE ANALYTICS')}</span><h2>${escape(s.serviceName)}</h2><p>${escape(s.status)} · ${s.lastUsedAt?`마지막 수신 ${new Date(s.lastUsedAt).toLocaleString()}`:'아직 telemetry가 없습니다'}</p></div><div class="actions"><button data-telemetry="toggle" data-id="${escape(id)}" data-enabled="${s.enabled}">${s.enabled?'서비스 비활성화':'서비스 활성화'}</button><button data-telemetry="key" data-id="${escape(id)}">새 API Key</button><button data-telemetry="revoke" data-id="${escape(id)}">Key 폐기</button></div></header><div class="telemetry-kpis">${[['Requests in range',s.requests],['Last minute',s.requestsLastMinute],['Last hour',s.requestsLastHour],['Today',s.requestsToday],['Errors',s.errors],['Error rate',s.errorRate==null?null:`${s.errorRate.toFixed(2)}%`],['Average latency',s.averageLatencyMs==null?null:`${s.averageLatencyMs.toFixed(1)} ms`],['p50',s.p50LatencyMs==null?null:`${s.p50LatencyMs.toFixed(1)} ms`],['p95',s.p95LatencyMs==null?null:`${s.p95LatencyMs.toFixed(1)} ms`],['p99',s.p99LatencyMs==null?null:`${s.p99LatencyMs.toFixed(1)} ms`],['Unique users',s.uniqueUsers],['Peak concurrent users',s.peakConcurrentUsers],['DAU / WAU / MAU',s.dau==null?'데이터 없음':`${s.dau} / ${s.wau} / ${s.mau}`]].map(([label,value])=>`<div><small>${label}</small><b>${value==null?'데이터 없음':typeof value==='number'?value.toLocaleString():value}</b></div>`).join('')}</div><section><h3>Request activity</h3><div class="telemetry-bars">${result.timeline.map(p=>`<div title="${new Date(p.timestamp).toLocaleString()} · ${p.requests} requests · ${p.errors} errors" style="--bar:${Math.max(3,Math.min(100,p.requests/Math.max(1,...result.timeline.map(x=>x.requests))*100))}%"><i></i></div>`).join('')||'<p>선택한 기간에 요청 데이터가 없습니다.</p>'}</div><div class="telemetry-breakdowns"><div><h3>Endpoints</h3>${rows(result.endpoints,result.endpointErrorRates)}</div><div><h3>HTTP methods</h3>${rows(result.methods)}</div><div><h3>Status codes</h3>${rows(result.statuses)}</div></div></section><section><h3>Realtime & custom metrics</h3><div class="telemetry-kpis">${Object.entries(s.gauges||{}).map(([name,value])=>`<div><small>${escape(name)}</small><b>${metric(value)}</b></div>`).join('')||'<p>이 서비스는 아직 gauge를 전송하지 않았습니다.</p>'}</div></section></article>`;}
  function rows(values,rates={}){return Object.keys(values).length?`<ul>${Object.entries(values).map(([k,v])=>`<li><span>${escape(k)}</span><b>${v.toLocaleString()}${rates[k]==null?'':` · 오류 ${rates[k].toFixed(2)}%`}</b></li>`).join('')}</ul>`:'<p>데이터 없음</p>'}
  function showKey(title,key){const dialog=document.querySelector('#telemetry-key-dialog');dialog.innerHTML=`<form method="dialog" class="panel"><h2>${title}</h2><p>이 키는 지금만 표시됩니다. 안전한 secret 저장소에 복사하세요.</p><label>API Key<input readonly value="${escape(key)}"></label><button class="primary">복사 완료</button></form>`;dialog.showModal();dialog.querySelector('input').select();}
  function guide(id){const endpoint=`${location.origin}/api/v1/telemetry`,service=services.find(s=>s.serviceId===id);const snippets={FastAPI:`# Fire-and-forget example; never await telemetry from request handling.
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
  root().innerHTML=`<div class="telemetry-toolbar"><button data-telemetry="back">← 서비스</button><h2>${escape(service?.serviceName||id)} · Integration</h2></div><article class="panel telemetry-guide"><p>전송은 응답 경로와 분리하고 짧은 timeout을 사용하세요. 오류는 무시하고 필요하면 로컬 버퍼에 모아 batch로 보냅니다.</p><p>Service ID: <code>${escape(id)}</code><br>Endpoint: <code>${escape(endpoint)}</code></p><pre> DASHBOARD_TELEMETRY_URL=${escape(endpoint)}
DASHBOARD_SERVICE_ID=${escape(id)}
DASHBOARD_API_KEY=dash_sk_&lt;store-the-key-as-a-secret&gt;</pre><nav>${Object.keys(snippets).map(k=>`<button data-telemetry="language" data-language="${k}">${k==='Raw'?'Raw HTTP':k}</button>`).join('')}</nav><pre id="telemetry-snippet">${escape(snippets[integration]||snippets.FastAPI)}</pre><button data-telemetry="copy">예제 복사</button><p>사용자 분석은 실제 ID 대신 서비스별 salt를 적용한 SHA-256 pseudonymous ID를 <code>user_activity</code> 이벤트에 넣으세요. 이메일, Discord ID, 사용자 이름은 전송하지 마세요.</p><pre>{"type":"user_activity","anonymousUserId":"&lt;sha256-pseudonymous-id&gt;"}</pre></article>`;}
  function create(){root().innerHTML=`<div class="telemetry-toolbar"><button data-telemetry="back">취소</button><h2>서비스 등록</h2></div><form class="panel telemetry-form" id="telemetry-create-form"><label>서비스 이름<input name="name" required maxlength="100"></label><label>설명<textarea name="description" maxlength="500"></textarea></label><label>서비스 유형<select name="serviceType"><option>Backend API</option><option>Web</option><option>Discord Bot</option><option>Worker</option><option>Custom</option></select></label><button class="primary">서비스 만들기 및 API Key 발급</button></form>`;}
  document.addEventListener('submit',async event=>{if(event.target.id!=='telemetry-create-form')return;event.preventDefault();try{const f=new FormData(event.target),result=await api('/services','POST',{name:f.get('name'),description:f.get('description'),serviceType:f.get('serviceType')});services.unshift({...result,requests:0});window.WorkspaceWidgets?.updateTelemetry(services);showKey('서비스 API Key',result.apiKey);guide(result.serviceId)}catch(error){window.alert(error.message)}});
  document.addEventListener('click',async event=>{const button=event.target.closest('[data-telemetry]');if(!button)return;const action=button.dataset.telemetry;try{if(action==='create')create();if(action==='back'){if(selected)await open(selected);else await load()}if(action==='open'){range='24h';await open(button.dataset.id)}if(action==='guide')guide(button.dataset.id);if(action==='range'){range=button.dataset.range;await open(selected)}if(action==='language'){integration=button.dataset.language;guide(selected)}if(action==='copy')await navigator.clipboard.writeText(document.querySelector('#telemetry-snippet').textContent);if(action==='toggle'){await api(`/services/${button.dataset.id}/enabled`,'PUT',{enabled:button.dataset.enabled!=='true'});await open(button.dataset.id)}if(action==='key'){const result=await api(`/services/${button.dataset.id}/key`,'POST',{});showKey('새 API Key',result.apiKey)}if(action==='revoke'){if(confirm('이 서비스의 현재 API Key를 즉시 폐기할까요?')){await api(`/services/${button.dataset.id}/key`,'DELETE');alert('API Key를 폐기했습니다. 다시 전송하려면 새 키를 발급하세요.')}}}catch(error){window.alert(error.message)}});
  return {open(id){if(id==='telemetry')load().catch(error=>{root().innerHTML=`<p class="error-state">${escape(error.message)}</p>`})}};
})();
