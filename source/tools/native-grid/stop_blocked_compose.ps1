$ErrorActionPreference = 'Stop'
$project = 'C:\Users\vakir\.codex\worktrees\3f07\DW-native-grid-city-20261022\source'
$research = Join-Path (Split-Path $project -Parent) 'research'
$proofPath = Join-Path $research 'native-grid-plan\context-extension\committed-snapshot.json'
$proof = Get-Content -LiteralPath $proofPath -Raw | ConvertFrom-Json
if ($proof.functionalRetentions.previouslyConvertedOverlap -ne 48) { throw 'Expected concrete 48-owner completeness blocker' }
$child = Get-CimInstance Win32_Process -Filter 'ProcessId = 10840'
$expected = '"C:\Users\vakir\miniconda3\python.exe" "tools/native-grid/run_task.py" "tools/native-grid-city/migrate.py" --plan "../research/native-grid-plan" compose --bounds 800 1119 -48 271 --additional-owners "../research/function-qa/satellites.json"'
if ($null -eq $child -or $child.CommandLine -cne $expected -or $child.ExecutablePath -cne 'C:\Users\vakir\miniconda3\python.exe') { throw 'PID no longer belongs to the exact owned compose child' }
if (-not $child.CreationDate.ToUniversalTime().ToString('o').StartsWith('2026-10-22T01:54:14.')) { throw 'Owned process creation time differs' }
$receipt = [ordered]@{
    status = 'FAIL_PROVEN_COMPLETENESS_GUARD'
    action = 'Stop only the owned authoring compose process; preserve same database and all prior map journals'
    reason = '48 previously converted owners were locally retained only because finite passive-carrier state decisions were missing; completion must reject this calculation'
    pid = 10840
    processCreationUtc = $child.CreationDate.ToUniversalTime().ToString('o')
    commandLine = $child.CommandLine
    proof = $proofPath
    proofSha256 = [BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash([IO.File]::ReadAllBytes($proofPath))).Replace('-', '').ToLowerInvariant()
    mapWrites = 0
    phase1MapAndLightPreserved = $true
    sourceArchive = (Join-Path $research 'processes\20261022T015414-migrate.inputs')
    stopped = $false
}
$output = Join-Path $research 'native-grid-plan\context-extension\blocked-compose-stop.json'
$receipt | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $output -Encoding UTF8
Stop-Process -Id 10840 -Force -ErrorAction Stop
$receipt.stopped = $true
$receipt.stoppedUtc = [DateTime]::UtcNow.ToString('o')
$receipt | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $output -Encoding UTF8
