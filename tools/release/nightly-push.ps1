<#
.SYNOPSIS
  Nightly push: installs the latest stable Anda Launcher release build on the connected phone.

.DESCRIPTION
  "Stable" means all of these pass, in order; the first failure stops the run and nothing is installed:
    1. QA smoke tier on the emulator (tools/qa/run-qa.ps1 -Tier smoke: unit tests, SmokeTourTest, log scan)
    2. lint (warnings are errors) and assembleRelease
    3. a changed APK has a new version (the top entry of CHANGELOG.md)
  Then installs app-release.apk on the physical phone with `adb install -r`, which upgrades in place
  and keeps the home layout and settings. It never uninstalls and never touches emulators.
  If the APK is byte-identical to the last one pushed, the install is skipped.

  Writes qa-reports/nightly/<timestamp>.md and qa-reports/nightly/latest.md.
  Exit code 0 = pushed (or already up to date), 1 = not stable, 2 = could not push (no phone, install failed).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\release\nightly-push.ps1
#>
param(
    # Serial of the phone to install on; empty = the only non-emulator device in `adb devices`.
    [string] $Serial = ""
)

$ErrorActionPreference = "Continue"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $root
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$sdk = ((Get-Content local.properties | Select-String '^sdk.dir=').Line.Substring(8)) -replace '\\\\', '\' -replace '\\:', ':'
$adb = Join-Path $sdk "platform-tools\adb.exe"
$package = "com.anda.launcher"
$apk = "app\build\outputs\apk\release\app-release.apk"

$stamp = Get-Date -Format "yyyy-MM-dd_HHmm"
$out = Join-Path $root "qa-reports\nightly"
New-Item -ItemType Directory -Force $out | Out-Null
$lastSha = Join-Path $out "last-pushed.sha256"
$lastVersion = Join-Path $out "last-pushed.version"
$lines = New-Object System.Collections.ArrayList

function Note([string] $status, [string] $step, [string] $detail) {
    [void] $lines.Add("| $step | $status | $($detail -replace '\|', '/') |")
    Write-Host "[$status] $step - $detail"
}

function Finish([int] $code, [string] $result) {
    $md = @("# Anda nightly push - $result", "", "Started $stamp.", "", "| Step | Result | Details |", "|---|---|---|") + $lines
    $md | Set-Content (Join-Path $out "$stamp.md") -Encoding utf8
    $md | Set-Content (Join-Path $out "latest.md") -Encoding utf8
    Write-Host ""
    Write-Host "Nightly push $result - report: qa-reports\nightly\$stamp.md"
    exit $code
}

# ---- 1. QA smoke on the emulator ------------------------------------------------------------------
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root "tools\qa\run-qa.ps1") -Tier smoke | Out-Host
$qa = $LASTEXITCODE
$qaReport = (Get-Content (Join-Path $root "qa-reports\latest-smoke.md") -TotalCount 1 -ErrorAction SilentlyContinue)
if ($qa -ne 0) { Note "FAIL" "QA smoke" "exit $qa - $qaReport (see qa-reports\latest-smoke.md)"; Finish 1 "NOT STABLE" }
Note "PASS" "QA smoke" "$qaReport"

# ---- 2. Lint + release build ----------------------------------------------------------------------
$log = Join-Path $out "$stamp-build.log"
& .\gradlew.bat lint assembleRelease --console=plain *> $log
if ($LASTEXITCODE -ne 0) {
    $why = (Get-Content $log | Select-String '^e: |Lint found|What went wrong' -Context 0, 1 | Select-Object -First 3 | ForEach-Object { $_.ToString().Trim() }) -join " "
    Note "FAIL" "lint + assembleRelease" "$why (see qa-reports\nightly\$stamp-build.log)"
    Finish 1 "NOT STABLE"
}
if (-not (Test-Path $apk)) { Note "FAIL" "lint + assembleRelease" "build passed but $apk is missing"; Finish 1 "NOT STABLE" }
$sha = (Get-FileHash $apk -Algorithm SHA256).Hash
Note "PASS" "lint + assembleRelease" ("{0:N1} MB, sha256 {1}" -f ((Get-Item $apk).Length / 1MB), $sha.Substring(0, 12))

# ---- 2b. Every update needs a new version (CHANGELOG.md) ------------------------------------------
$meta = Get-Content "app\build\outputs\apk\release\output-metadata.json" -Raw -ErrorAction SilentlyContinue | ConvertFrom-Json
$apkVersion = if ($meta) { "$($meta.elements[0].versionName) ($($meta.elements[0].versionCode))" } else { "unknown" }
$changedApk = -not ((Test-Path $lastSha) -and ((Get-Content $lastSha -Raw).Trim() -eq $sha))
if ($changedApk -and (Test-Path $lastVersion) -and ((Get-Content $lastVersion -Raw).Trim() -eq $apkVersion)) {
    Note "FAIL" "version" "The APK changed but is still $apkVersion, the last pushed version: bump the version in CHANGELOG.md"
    Finish 1 "NOT STABLE"
}
Note "PASS" "version" $apkVersion

# ---- 3. Find the phone (never an emulator) --------------------------------------------------------
$phones = @(& $adb devices | Select-String '^(\S+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value } | Where-Object { $_ -notlike "emulator-*" })
if ($Serial) { $phones = @($phones | Where-Object { $_ -eq $Serial }) }
if ($phones.Count -eq 0) { Note "SKIP" "phone" "No phone connected over USB or wireless debugging; nothing installed"; Finish 2 "NOT PUSHED" }
if ($phones.Count -gt 1) { Note "SKIP" "phone" "More than one phone ($($phones -join ', ')); pass -Serial"; Finish 2 "NOT PUSHED" }
$phone = $phones[0]
$model = (& $adb -s $phone shell getprop ro.product.model 2>$null | Out-String).Trim()
Note "PASS" "phone" "$phone ($model)"

# ---- 4. Install -----------------------------------------------------------------------------------
$installed = (& $adb -s $phone shell pm path $package 2>$null | Out-String).Trim()
if ($installed -and (Test-Path $lastSha) -and ((Get-Content $lastSha -Raw).Trim() -eq $sha)) {
    Note "PASS" "install" "Already up to date (same APK as the last push)"
    Finish 0 "UP TO DATE"
}
$result = (& $adb -s $phone install -r $apk 2>&1 | Out-String).Trim()
if ($result -notmatch 'Success') {
    # Never uninstall to work around a signature mismatch: that would wipe the home layout.
    Note "FAIL" "install" ($result -split "`n" | Select-Object -Last 1)
    Finish 2 "NOT PUSHED"
}
$sha | Set-Content $lastSha -Encoding ascii
$apkVersion | Set-Content $lastVersion -Encoding ascii
$version = (& $adb -s $phone shell dumpsys package $package 2>$null | Select-String 'versionName=(\S+)' | Select-Object -First 1).Matches.Groups[1].Value
Note "PASS" "install" "Installed $package $version on $model"
Finish 0 "PUSHED"
