# Isolated regression test. Requires existing dashboard/tailscale/browser/SSH-fixture images.
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Net.Http
$project='dashboard-network-qa'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$temp=Join-Path $repo '.tools/network-qa.env'
$overlay=Join-Path $repo '.tools/network-qa.yaml'
$qaPassword=[guid]::NewGuid().ToString('N')
$env:QA_SSH_PASSWORD=[guid]::NewGuid().ToString('N')
[IO.File]::WriteAllLines($temp,@('DASHBOARD_AUTH_ID=network-verifier',('DASHBOARD_AUTH_PASSWORD='+$qaPassword),'DASHBOARD_PORT=18102','DASHBOARD_BIND_ADDRESS=127.0.0.1','SESSION_COOKIE_SECURE=false','TAILSCALE_AUTHKEY='))
[IO.File]::WriteAllText($overlay,"services:`n  dashboard:`n    image: my-dashboard-be-dashboard`n  tailscale:`n    image: my-dashboard-be-tailscale`n  browser:`n    image: my-dashboard-be-browser`n")
$compose=@('compose','--project-directory',$repo,'-p',$project,'--env-file',$temp,'-f',(Join-Path $repo 'compose.yaml'),'-f',$overlay)
function ComposeRun([string[]]$arguments){
  $previous=$ErrorActionPreference;$ErrorActionPreference='Continue'
  try {$null=& docker @compose @arguments 2>&1;$code=$LASTEXITCODE}finally{$ErrorActionPreference=$previous}
  if($code -ne 0){throw ('Compose failed: '+($arguments -join ' '))}
}
$handler=[System.Net.Http.HttpClientHandler]::new();$handler.UseProxy=$false;$handler.AllowAutoRedirect=$false
$client=[System.Net.Http.HttpClient]::new($handler);$client.Timeout=[TimeSpan]::FromSeconds(15)
$base='http://localhost:18102'
function Api($path,$method='GET',$body=$null){
 $request=[System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($method),$base+'/api/v1'+$path)
 if($method -ne 'GET'){$request.Headers.Add('X-CSRF-TOKEN',$script:csrf)}
 if($null -ne $body){$request.Content=[System.Net.Http.StringContent]::new(($body|ConvertTo-Json -Compress),[Text.Encoding]::UTF8,'application/json')}
 $response=$client.SendAsync($request).GetAwaiter().GetResult()
 if(-not $response.IsSuccessStatusCode){throw ('API failed '+$path+' '+[int]$response.StatusCode)}
 return $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
}
function Health {
 $response=$client.GetAsync($base+'/health').GetAwaiter().GetResult()
 if([int]$response.StatusCode -ne 200){throw 'Health failed'}
 $null=Api '/workspace'
 $null=Api '/cloud'
}
try {
 $config=(& docker @compose config --format json) | ConvertFrom-Json
 if($config.services.dashboard.network_mode -or $config.services.dashboard.depends_on){throw 'Dashboard depends on a sidecar'}
 foreach($service in @('tailscale','guacd','browser')){if($config.services.$service.network_mode -ne 'service:dashboard'){throw 'Wrong namespace owner'}}
 ComposeRun @('up','-d','--no-build','dashboard','guacd','browser')
 for($i=0;$i -lt 60;$i++){try{if([int]$client.GetAsync($base+'/health').GetAwaiter().GetResult().StatusCode -eq 200){break}}catch{};Start-Sleep -Seconds 1}
 $page=$client.GetStringAsync($base+'/login').GetAwaiter().GetResult()
 $csrf=[regex]::Match($page,'name="_csrf" value="([^"]+)"').Groups[1].Value
 $login='id=network-verifier&password='+$qaPassword+'&_csrf='+[uri]::EscapeDataString($csrf)
 $response=$client.PostAsync($base+'/login',[System.Net.Http.StringContent]::new($login,[Text.Encoding]::UTF8,'application/x-www-form-urlencoded')).GetAwaiter().GetResult()
 if([int]$response.StatusCode -ne 302){throw 'Login failed'}
 $page=$client.GetStringAsync($base+'/').GetAwaiter().GetResult()
 $script:csrf=[regex]::Match($page,'name="csrf-token" content="([^"]+)"').Groups[1].Value
 Health
 Write-Output 'PASS dashboard login, workspace and cloud without a Tailscale container'
 $null=& docker run -d --rm --name dashboard-network-ssh --network ($project+'_default') -e QA_SSH_PASSWORD my-dashboard-ssh-verifier
 if($LASTEXITCODE -ne 0){throw 'SSH fixture failed'}
 Start-Sleep -Seconds 2
 $device=Api '/devices/ssh' 'POST' @{command='ssh tester@dashboard-network-ssh';password=$env:QA_SSH_PASSWORD;networkMode='DIRECT';name='Network QA'}
 if(-not $device.id){throw 'Direct SSH enrollment failed'}
 Write-Output 'PASS real DIRECT SSH without Tailscale'
 ComposeRun @('up','-d','--no-build','tailscale')
 for($i=0;$i -lt 85;$i++){
  Health
  try{$status=Api '/tailscale';if($status.state -eq 'NeedsLogin'){break}}catch{}
  Start-Sleep -Seconds 1
 }
 if($status.state -ne 'NeedsLogin'){throw 'Expected fresh unauthenticated Tailscale state'}
 Health
 Write-Output 'PASS login and cloud with Tailscale NeedsLogin'
 ComposeRun @('stop','tailscale')
 Health
 $device=Api '/devices/ssh' 'POST' @{command='ssh tester@dashboard-network-ssh';password=$env:QA_SSH_PASSWORD;networkMode='DIRECT';name='Network QA'}
 Write-Output 'PASS dashboard and DIRECT SSH after stopping Tailscale'
} finally {
 $client.Dispose()
 $ErrorActionPreference='Continue'
 $null=& docker stop dashboard-network-ssh 2>&1
 # Only the fixed QA project and its synthetic data are removed.
 $null=& docker @compose down -v --remove-orphans 2>&1
 Remove-Item -LiteralPath $temp,$overlay -ErrorAction SilentlyContinue
 Remove-Item Env:QA_SSH_PASSWORD -ErrorAction SilentlyContinue
}
