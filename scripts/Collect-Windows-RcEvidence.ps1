param(
    [Parameter(Mandatory = $true)]
    [string]$LatestLog,
    [string]$OutputDirectory = ".\change007-windows-evidence"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $LatestLog -PathType Leaf)) { throw "Latest log not found: $LatestLog" }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$os = Get-CimInstance Win32_OperatingSystem | Select-Object Caption, Version, BuildNumber, OSArchitecture,
    @{Name="TotalVisibleMemoryGiB";Expression={[math]::Round($_.TotalVisibleMemorySize / 1MB, 2)}}
$cpu = Get-CimInstance Win32_Processor | Select-Object Name, NumberOfCores, NumberOfLogicalProcessors
$gpu = Get-CimInstance Win32_VideoController | Select-Object Name, DriverVersion, AdapterRAM
$java = Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" |
    ForEach-Object {
        $safePath = $_.ExecutablePath -replace '([A-Za-z]:\\Users\\)[^\\]+', '$1REDACTED'
        [pscustomobject]@{
            ProcessId = $_.ProcessId
            ParentProcessId = $_.ParentProcessId
            Name = $_.Name
            ExecutablePath = $safePath
            WorkingSetMiB = [math]::Round($_.WorkingSetSize / 1MB, 2)
        }
    }

@{
    OperatingSystem = $os
    CPU = $cpu
    GPU = $gpu
    JavaProcesses = $java
} | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 "$OutputDirectory\system-and-java.json"

$patterns = 'java version|Temurin|windows-x86_64|lwjgl3ify|LWJGL|Distant Horizons|release candidate|build source|git commit|managed by the WDG|updater|worker|thread|LOD|SQLite|migration|save|shutdown'
Select-String -Path $LatestLog -Pattern $patterns -AllMatches |
    ForEach-Object { $_.Line -replace '([A-Za-z]:\\Users\\)[^\\]+', '$1REDACTED' } |
    Set-Content -Encoding UTF8 "$OutputDirectory\relevant-log-lines.txt"

@'
Allocated Java memory:
Launcher initial Java version:
Packaged windows-x86_64 runtime selected:
Java 21 child confirmed:
Main menu and mod list:
Warning rendered as three separate lines:
Provenance and updater-disabled lines:
Disposable world creation and LOD generation:
Idle menu FPS / in-world FPS / frame-time notes:
CPU and GPU load:
First-generation behaviour:
Generated-LOD reload behaviour:
Save and reopen:
Normal Wargames modpack compatibility:
Normal shutdown:
'@ | Set-Content -Encoding UTF8 "$OutputDirectory\manual-observations.txt"

Write-Host "Evidence written to $OutputDirectory. Review paths before sharing."
