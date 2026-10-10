param([switch]$Cleanup)
# Uses only generated credentials, disposable data, and loopback-published fixture ports.
$ErrorActionPreference = 'Stop'
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$fixturePath = Join-Path $repository '.tools/auth-browser-qa.json'

function Remove-Fixture($fixture) {
    if ($fixture.name -notmatch '^auth-browser-qa-[a-f0-9]{8}$') { throw 'Unexpected fixture name' }
    foreach ($suffix in @('-dashboard', '-browser', '-guacd')) {
        $null = & docker rm -f ($fixture.name + $suffix) 2>&1
    }
    & docker volume rm ($fixture.name + '-profile') | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Fixture profile cleanup failed' }
    & docker network rm $fixture.name | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Fixture network cleanup failed' }
    Remove-Item -LiteralPath $fixturePath
}

if ($Cleanup) {
    if (Test-Path -LiteralPath $fixturePath) { Remove-Fixture (Get-Content -LiteralPath $fixturePath -Raw | ConvertFrom-Json) }
    Write-Output 'Isolated authentication browser fixture removed.'
    exit
}
if (Test-Path -LiteralPath $fixturePath) { throw 'Clean up the previous authentication browser fixture first.' }
foreach ($image in @('my-dashboard-auth-check:local', 'my-dashboard-auth-browser-check:local', 'guacamole/guacd:1.6.0')) {
    & docker image inspect $image --format '{{.Id}}' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Build the documented fixture images first.' }
}
$fixture = @{name=('auth-browser-qa-'+[guid]::NewGuid().ToString('N').Substring(0,8));username='auth-browser-verifier';password=[guid]::NewGuid().ToString('N');url='http://127.0.0.1:18198';cdp='http://127.0.0.1:19298'}
$null = New-Item -ItemType Directory -Force -Path (Join-Path $repository '.tools')
[IO.File]::WriteAllText($fixturePath, ($fixture | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
& docker network create $fixture.name | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Fixture network creation failed' }
& docker volume create ($fixture.name+'-profile') | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Fixture profile creation failed' }
& docker run -d --name ($fixture.name+'-browser') --network $fixture.name --network-alias browser -p 127.0.0.1:19298:9223 -v ($fixture.name+'-profile:/home/browser/profile') my-dashboard-auth-browser-check:local | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Fixture browser creation failed' }
& docker run -d --name ($fixture.name+'-guacd') --network $fixture.name --network-alias guacd guacamole/guacd:1.6.0 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Fixture guacd creation failed' }
# Preserve any existing shell environment. Only random test credentials reach this new container.
$previousId=$env:DASHBOARD_AUTH_ID; $previousPassword=$env:DASHBOARD_AUTH_PASSWORD
try {
    $env:DASHBOARD_AUTH_ID=$fixture.username; $env:DASHBOARD_AUTH_PASSWORD=$fixture.password
    & docker run -d --name ($fixture.name+'-dashboard') --network $fixture.name --network-alias dashboard -p 127.0.0.1:18198:8080 -e DASHBOARD_AUTH_ID -e DASHBOARD_AUTH_PASSWORD -e SESSION_COOKIE_SECURE=false -e BROWSER_HOST=browser -e GUACD_HOST=guacd my-dashboard-auth-check:local | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Fixture dashboard creation failed' }
} finally { $env:DASHBOARD_AUTH_ID=$previousId; $env:DASHBOARD_AUTH_PASSWORD=$previousPassword }
Write-Output 'Isolated authentication browser fixture ready. Run node scripts/check-authentication-browser.mjs.'
