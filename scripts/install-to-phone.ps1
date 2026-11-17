<#
.SYNOPSIS
  Build the latest debug APK and install it on the phone over wireless debugging,
  then verify the phone is really running that exact build.

.DESCRIPTION
  1. Locates JDK (JAVA_HOME or Android Studio's JBR) and adb (from local.properties sdk.dir).
  2. Restarts the adb server if needed, finds the phone (ignores emulators), reconnecting
     via mDNS (wireless debugging) when it is missing or offline.
  3. Runs :app:assembleDebug (Gradle skips work when nothing changed, so this is always current).
  4. Installs with `adb install -r -d` targeted by serial.
  5. Verifies: the SHA-256 of the installed base.apk on the phone equals the local APK.

  Pairing (first time / after "Forget") cannot be scripted: pair once in
  Developer options > Wireless debugging > Pair device with pairing code, then run
  `adb pair <ip:port>`. After that this script reconnects on its own.

.PARAMETER Serial
  Force a specific adb serial (e.g. "192.168.2.31:33203" or the adb-XXXX._adb-tls-connect._tcp name).
.PARAMETER Launch
  Start the launcher activity after install.
.PARAMETER ForceReinstall
  If install fails with a signature mismatch, uninstall the old app (DELETES its data) and retry.
.PARAMETER SkipBuild
  Install the existing APK without running Gradle.
#>
[CmdletBinding()]
param(
    [string]$Serial,
    [switch]$Launch,
    [switch]$ForceReinstall,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$package = 'com.anda.launcher'
$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'

function Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Fail($msg) { Write-Host "ERROR: $msg" -ForegroundColor Red; exit 1 }

# --- Locate tools ------------------------------------------------------------
Step 'Locating JDK and adb'
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $jbr = @(
        'C:\Program Files\Android\Android Studio\jbr',
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
    ) | Where-Object { Test-Path (Join-Path $_ 'bin\java.exe') } | Select-Object -First 1
    if (-not $jbr) { Fail 'No JDK found. Set JAVA_HOME or install Android Studio.' }
    $env:JAVA_HOME = $jbr
}

$sdk = $env:ANDROID_HOME
if (-not $sdk -and (Test-Path 'local.properties')) {
    $line = Get-Content 'local.properties' | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if ($line) { $sdk = ($line -replace '^sdk\.dir=', '') -replace '\\\\', '\' -replace '\\:', ':' }
}
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { Fail "adb not found at $adb" }
Write-Host "JAVA_HOME=$env:JAVA_HOME`nadb=$adb"

# --- Find / connect the phone ------------------------------------------------
function Invoke-AdbTimed {
    # Runs adb with a hard timeout so a dead wireless target can never hang the script.
    param([string[]]$ArgList, [int]$TimeoutSec = 15)
    $tmp = [System.IO.Path]::GetTempFileName()
    try {
        $p = Start-Process -FilePath $adb -ArgumentList $ArgList -NoNewWindow -PassThru -RedirectStandardOutput $tmp
        if (-not $p.WaitForExit($TimeoutSec * 1000)) {
            Write-Host "  (adb $($ArgList -join ' ') timed out after ${TimeoutSec}s)" -ForegroundColor Yellow
            try { $p.Kill() } catch {}
        }
        return @(Get-Content $tmp -ErrorAction SilentlyContinue)
    } finally { Remove-Item $tmp -ErrorAction SilentlyContinue }
}

function Get-Devices {
    # Returns objects {Serial, State} for every non-emulator device adb knows about.
    (Invoke-AdbTimed @('devices')) | Select-Object -Skip 1 | ForEach-Object {
        if ($_ -match '^(\S+)\s+(device|offline|unauthorized|authorizing)\b') {
            [pscustomobject]@{ Serial = $Matches[1]; State = $Matches[2] }
        }
    } | Where-Object { $_.Serial -notlike 'emulator-*' }
}

function Get-MdnsTargets {
    (Invoke-AdbTimed @('mdns', 'services')) | ForEach-Object {
        if ($_ -match '^(\S+)\s+_adb-tls-connect\._tcp\.?\s+(\d{1,3}(?:\.\d{1,3}){3}:\d+)') {
            [pscustomobject]@{ Name = $Matches[1]; Address = $Matches[2] }
        }
    }
}

function Find-Phone {
    $devs = @(Get-Devices)
    if ($Serial) { $devs = @($devs | Where-Object { $_.Serial -eq $Serial }) }
    return $devs
}

Step 'Starting adb server'
Invoke-AdbTimed @('start-server') 30 | Out-Null

Step 'Looking for the phone'
$phone = Find-Phone | Where-Object State -eq 'device' | Select-Object -First 1

if (-not $phone) {
    # Drop stale/offline wireless entries, then reconnect through mDNS discovery.
    foreach ($d in @(Find-Phone | Where-Object State -ne 'device')) {
        Write-Host "Dropping stale entry $($d.Serial) ($($d.State))"
        Invoke-AdbTimed @('disconnect', $d.Serial) | Out-Null
    }

    for ($attempt = 1; $attempt -le 3 -and -not $phone; $attempt++) {
        $targets = @(Get-MdnsTargets)
        if ($Serial -and $Serial -match '^\d{1,3}(\.\d{1,3}){3}:\d+$') {
            $targets = @([pscustomobject]@{ Name = $Serial; Address = $Serial }) + $targets
        }
        Write-Host "Attempt ${attempt}: $($targets.Count) wireless target(s) discovered"
        foreach ($t in $targets) {
            Write-Host "  adb connect $($t.Address)"
            Invoke-AdbTimed @('connect', $t.Address) 15 | ForEach-Object { Write-Host "    $_" }
        }
        Start-Sleep -Seconds 2
        $phone = Find-Phone | Where-Object State -eq 'device' | Select-Object -First 1
        if (-not $phone -and $attempt -eq 2) {
            Write-Host 'Restarting adb server...'
            Invoke-AdbTimed @('kill-server') 10 | Out-Null
            Invoke-AdbTimed @('start-server') 30 | Out-Null
            Start-Sleep -Seconds 2
        }
    }
}

if (-not $phone) {
    $unauth = Find-Phone | Where-Object State -eq 'unauthorized'
    if ($unauth) { Fail 'Phone shows "unauthorized": accept the USB/Wi-Fi debugging prompt on the phone and re-run.' }
    Write-Host ("If the phone answers ping but 'adb connect' says 'failed to connect' immediately, the phone no longer trusts this PC " +
                "(SSLV3_ALERT_CERTIFICATE_UNKNOWN in %TEMP%\adb.log): it must be re-paired.") -ForegroundColor Yellow
    Fail ("Phone not reachable. Check: phone unlocked, on the same Wi-Fi, Developer options > Wireless debugging is ON. " +
          "If it was never paired (or you tapped Forget), pair once: 'adb pair <ip:port>' using the pairing code.")
}
$target = $phone.Serial
Write-Host "Using device: $target" -ForegroundColor Green

# --- Build -------------------------------------------------------------------
if (-not $SkipBuild) {
    Step 'Building debug APK (Gradle)'
    & .\gradlew.bat ':app:assembleDebug' '--console=plain'
    if ($LASTEXITCODE -ne 0) { Fail "Gradle build failed (exit $LASTEXITCODE). Nothing was installed." }
}
if (-not (Test-Path $apk)) { Fail "APK not found: $apk" }
$apkInfo = Get-Item $apk
$localHash = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
Write-Host ("APK: {0:N1} MB, built {1}, sha256 {2}..." -f ($apkInfo.Length / 1MB), $apkInfo.LastWriteTime, $localHash.Substring(0, 12))

# --- Install -----------------------------------------------------------------
function Install-Apk {
    $out = (& $adb -s $target install -r -d $apk) -join "`n"
    Write-Host $out
    return $out
}

Step 'Installing on phone'
$out = Install-Apk
if ($out -notmatch 'Success') {
    if ($out -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|INSTALL_FAILED_VERSION_DOWNGRADE|signatures do not match') {
        if (-not $ForceReinstall) {
            Fail 'Signature mismatch with the installed app. Re-run with -ForceReinstall to uninstall first (this DELETES the launcher''s data on the phone).'
        }
        Write-Host 'Uninstalling old copy (ForceReinstall)...' -ForegroundColor Yellow
        & $adb -s $target uninstall $package | Out-Host
        $out = Install-Apk
    }
    if ($out -notmatch 'Success') { Fail 'adb install did not report Success.' }
}

# --- Verify ------------------------------------------------------------------
Step 'Verifying installed build matches local APK'
$remotePath = ((& $adb -s $target shell pm path $package) | Where-Object { $_ -like 'package:*' } | Select-Object -First 1) -replace '^package:', ''
if (-not $remotePath) { Fail "Package $package not found on the phone after install." }
$remoteHash = (((& $adb -s $target shell sha256sum $remotePath.Trim()) -split '\s+')[0]).ToLower()
if ($remoteHash -ne $localHash) {
    Fail "Hash mismatch! phone=$remoteHash local=$localHash. The phone is NOT running the latest build."
}
$ver = (& $adb -s $target shell dumpsys package $package) | Where-Object { $_ -match 'versionName=|lastUpdateTime=' } | ForEach-Object { $_.Trim() }
$ver | ForEach-Object { Write-Host "  $_" }

if ($Launch) {
    Step 'Launching'
    & $adb -s $target shell monkey -p $package -c android.intent.category.LAUNCHER 1 | Out-Null
}

Write-Host "`nOK: latest build is installed and verified on $target." -ForegroundColor Green
