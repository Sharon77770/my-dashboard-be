$ErrorActionPreference='Stop'
$agentPath=(Resolve-Path (Join-Path $PSScriptRoot 'bin/Debug/net10.0-windows/WindowsRuntimeAgent.exe')).Path
$agent=Start-Process -FilePath $agentPath -WindowStyle Hidden -PassThru
function Request-Agent($operation){
 $pipe=[IO.Pipes.NamedPipeClientStream]::new('.','PersonalWorkspace.Communications.v1',[IO.Pipes.PipeDirection]::InOut)
 try{
  $pipe.Connect(5000)
  $writer=[IO.StreamWriter]::new($pipe,[Text.UTF8Encoding]::new($false),1024,$true);$writer.AutoFlush=$true
  $writer.WriteLine((@{operation=$operation}|ConvertTo-Json -Compress))
  $reader=[IO.StreamReader]::new($pipe);$read=$reader.ReadLineAsync()
  if(-not $read.Wait(8000)){throw 'Agent response timed out'}
  return $read.Result|ConvertFrom-Json
 }finally{$pipe.Dispose()}
}
try{
 $snapshot=Request-Agent 'snapshot'
 if($snapshot.structuredMessages -ne $false -or $snapshot.loginState -ne 'UNKNOWN'){throw 'Agent guessed unsupported message semantics'}
 $invalid=Request-Agent 'invoke'
 if($invalid.state -ne 'UNAVAILABLE'){throw 'Agent accepted an unsupported operation'}
 Write-Output ('PASS local Windows named-pipe Agent: snapshot state='+$snapshot.state+'; nodes='+$snapshot.nodes.Count+'; unsupported write rejected. No message content logged.')
}finally{if(-not $agent.HasExited){Stop-Process -Id $agent.Id}}
