$ErrorActionPreference = 'Stop'
$child = Get-CimInstance Win32_Process -Filter 'ProcessId = 10756'
$expected = '"C:\Users\vakir\miniconda3\python.exe" tools/native-grid/run_task.py tools/native-grid-city/migrate.py --plan ../research/native-grid-plan compose'
if ($null -eq $child -or $child.CommandLine -cne $expected -or $child.ExecutablePath -cne 'C:\Users\vakir\miniconda3\python.exe') { throw 'PID is not the exact owned FULL compose child' }
$research = 'C:\Users\vakir\.codex\worktrees\3f07\DW-native-grid-city-20261022\research'
$stackPath = Join-Path $research 'native-grid-plan\full\compose-python-stack.txt'
$stack = Get-Content -LiteralPath $stackPath -Raw -Encoding UTF8
if (!$stack.Contains('preflight_functional_guests (migrate.py:378)') -or !$stack.Contains('Process 10756:')) { throw 'Expected concrete owner SELECT hotspot is not proven' }
$receipt = [ordered]@{
    status = 'UNACCEPTED_AUTHORING_STOP_FOR_PROVEN_OWNER_INDEX_HINT'
    pid = 10756
    creationUtc = $child.CreationDate.ToUniversalTime().ToString('o')
    commandLine = $child.CommandLine
    stackPath = $stackPath
    stackSha256 = (Get-FileHash -LiteralPath $stackPath -Algorithm SHA256).Hash.ToLowerInvariant()
    reason = 'Owner INSERT SELECT uses covering fragment PK on dim only despite available fragment_owner(dim,uuid); active stack pinpoints line 378'
    mapWrites = 0
    sameDatabaseRetained = $true
    acceptedPilotAndFunctionsRetained = $true
    stopped = $false
}
$out = Join-Path $research 'native-grid-plan\full\compose-owner-select-stop.json'
$receipt | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $out -Encoding UTF8
Stop-Process -Id 10756 -Force -ErrorAction Stop
$receipt.stopped = $true
$receipt.stoppedUtc = [DateTime]::UtcNow.ToString('o')
$receipt | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $out -Encoding UTF8
