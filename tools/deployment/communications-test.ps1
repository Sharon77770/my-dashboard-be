param([switch]$Cleanup,[switch]$IncludeWine)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$configPath=Join-Path $root '.tools/communications-qa.json'
if($Cleanup){
 if(Test-Path -LiteralPath $configPath){
  $fixture=Get-Content -LiteralPath $configPath -Raw|ConvertFrom-Json
  if($fixture.name -notmatch '^communications-qa-[a-f0-9]{8}$'){throw 'Unexpected fixture name'}
  if($fixture.includeWine){& docker rm -f ($fixture.name+'-wine') 2>&1|Out-Null; & docker volume rm ($fixture.name+'-wine-profile')|Out-Null}
  foreach($suffix in @('-browser','-guacd','-dashboard')){& docker rm -f ($fixture.name+$suffix) 2>&1|Out-Null}
  & docker volume rm ($fixture.name+'-profile') ($fixture.name+'-control')|Out-Null
  if($LASTEXITCODE -ne 0){throw 'Profile cleanup failed'}
  Remove-Item -LiteralPath $configPath
 }
 Write-Output 'Isolated communications fixture removed.'
 exit
}
if(Test-Path -LiteralPath $configPath){throw 'Clean up the previous communications fixture first.'}
$fixture=@{includeWine=[bool]$IncludeWine;name=('communications-qa-'+[guid]::NewGuid().ToString('N').Substring(0,8));username='communications-verifier';password=[guid]::NewGuid().ToString('N');url='http://127.0.0.1:18197'}
[IO.File]::WriteAllText($configPath,($fixture|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
$oldId=$env:DASHBOARD_AUTH_ID;$oldPassword=$env:DASHBOARD_AUTH_PASSWORD
try{
 $env:DASHBOARD_AUTH_ID=$fixture.username;$env:DASHBOARD_AUTH_PASSWORD=$fixture.password
 & docker volume create ($fixture.name+'-profile')|Out-Null
 & docker volume create ($fixture.name+'-control')|Out-Null
 & docker run -d --name ($fixture.name+'-dashboard') -p 127.0.0.1:18197:8080 -v ($fixture.name+'-control:/run/communication-bridge:ro') -e DASHBOARD_AUTH_ID -e DASHBOARD_AUTH_PASSWORD -e SESSION_COOKIE_SECURE=false -e BROWSER_HOST=127.0.0.1 -e GUACD_HOST=127.0.0.1 my-dashboard-communications-check:local|Out-Null
 if($LASTEXITCODE -ne 0){throw 'Dashboard fixture failed'}
 & docker run -d --name ($fixture.name+'-browser') --network ('container:'+$fixture.name+'-dashboard') -v ($fixture.name+'-profile:/home/browser/profile') -v ($fixture.name+'-control:/run/communication-bridge') -e BROWSER_LOCAL_ONLY=true my-dashboard-communications-browser-check:local|Out-Null
 if($LASTEXITCODE -ne 0){throw 'Browser fixture failed'}
 if($IncludeWine){
  & docker run -d --name ($fixture.name+'-wine') --network ('container:'+$fixture.name+'-dashboard') -v ($fixture.name+'-wine-profile:/home/browser/profile') -v ($fixture.name+'-control:/run/communication-bridge:ro') my-dashboard-communications-wine-check:local|Out-Null
  if($LASTEXITCODE -ne 0){throw 'Wine fixture failed'}
 }
 & docker run -d --name ($fixture.name+'-guacd') --network ('container:'+$fixture.name+'-dashboard') guacamole/guacd:1.6.0 guacd -f -b 127.0.0.1|Out-Null
 if($LASTEXITCODE -ne 0){throw 'Guacamole fixture failed'}
}finally{$env:DASHBOARD_AUTH_ID=$oldId;$env:DASHBOARD_AUTH_PASSWORD=$oldPassword}
Write-Output 'Isolated communications fixture ready; generated credentials stay in ignored .tools configuration.'
