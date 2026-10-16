<#
.SYNOPSIS
  Anda Launcher QA runner. Runs on the Android EMULATOR only (never a physical phone).

.DESCRIPTION
  -Tier smoke  (~8 min)  Unit tests + SmokeTourTest (every main surface in one launcher session)
                         + log scan (Anda crashes, ANRs, StrictMode, on-device crash reports).
  -Tier deep   (~45 min) Everything in smoke, plus: lint, the full instrumented suite, monkey
                         stress runs, a performance/memory snapshot and the APK size.

  Writes qa-reports/<timestamp>-<tier>/report.md and summary.json, updates
  qa-reports/latest-<tier>.md and appends a line to qa-reports/history.csv.
  Exit code 0 = PASS, 1 = FAIL, 2 = could not run (no emulator, build broken before tests).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\qa\run-qa.ps1 -Tier smoke
#>
param(
    [ValidateSet("smoke", "deep")] [string] $Tier = "smoke",
    [string] $Avd = "C1_Pixel_9_API_37",
    [int] $MonkeySeeds = 3,
    [int] $MonkeyEvents = 8000,
    # "auto": deep runs start from a wiped emulator (reproducible, no leftovers from earlier runs);
    # smoke reuses a running emulator if it's healthy.
    [ValidateSet("auto", "yes", "no")] [string] $Fresh = "auto"
)

$ErrorActionPreference = "Continue"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $root
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$sdk = ((Get-Content local.properties | Select-String '^sdk.dir=').Line.Substring(8)) -replace '\\\\', '\' -replace '\\:', ':'
$adb = Join-Path $sdk "platform-tools\adb.exe"
$emulatorExe = Join-Path $sdk "emulator\emulator.exe"
$package = "com.anda.launcher"

$stamp = Get-Date -Format "yyyy-MM-dd_HHmm"
$out = Join-Path $root "qa-reports\$stamp-$Tier"
New-Item -ItemType Directory -Force $out | Out-Null
$started = Get-Date
$steps = New-Object System.Collections.ArrayList
$metrics = [ordered]@{}

function Add-Step([string] $name, [string] $status, [string] $detail, [double] $seconds) {
    [void] $steps.Add([pscustomobject]@{ name = $name; status = $status; detail = $detail; seconds = [math]::Round($seconds) })
    Write-Host ("[{0}] {1} - {2}" -f $status, $name, $detail)
}

function Invoke-Gradle([string] $logName, [string[]] $gradleArgs) {
    $log = Join-Path $out $logName
    & .\gradlew.bat @gradleArgs --console=plain *> $log
    return $LASTEXITCODE
}

function Get-TestResults([string[]] $dirs) {
    $total = 0; $failed = 0; $names = @()
    foreach ($d in $dirs) {
        if (-not (Test-Path -LiteralPath $d)) { continue }
        # Device result files have names like "TEST-C1_Pixel_9_API_37(AVD) - 17.xml": use -LiteralPath.
        foreach ($f in Get-ChildItem -LiteralPath $d -Recurse -File | Where-Object { $_.Extension -eq ".xml" -and $_.Name -like "TEST-*" }) {
            [xml] $x = Get-Content -LiteralPath $f.FullName -Raw
            $suites = $x.SelectNodes('//testsuite')
            if ($suites.Count -gt 0) { foreach ($s in $suites) { $total += [int] $s.tests } }
            elseif ($x.testsuites) { $total += [int] $x.testsuites.tests }
            foreach ($c in $x.SelectNodes('//testcase[failure or error]')) {
                $failed++
                $msg = ($c.InnerText -split "`n" | Where-Object { $_.Trim() } | Select-Object -First 1)
                $names += "$($c.classname.Split('.')[-1]).$($c.name): $msg"
            }
        }
    }
    return [pscustomobject]@{ total = $total; failed = $failed; names = $names }
}

function Adb([string] $command) { & $adb -s $env:ANDROID_SERIAL shell $command 2>$null }

function Find-Emulator { (& $adb devices | Select-String '^emulator-\d+\s+device' | ForEach-Object { ($_.Line -split '\s+')[0] }) | Select-Object -First 1 }

function Stop-Emulators {
    # Only emulators ("emulator-NNNN"); a phone on USB or Wi-Fi debugging is never touched.
    foreach ($s in (& $adb devices | Select-String '^emulator-\d+' | ForEach-Object { ($_.Line -split '\s+')[0] })) { & $adb -s $s emu kill 2>$null | Out-Null }
    Start-Sleep -Seconds 8
}

function Start-Emulator([bool] $wipe) {
    # Host GPU: the AVD's default software renderer (swiftshader) alone can saturate the CPU.
    $flags = "-avd $Avd -no-window -no-audio -no-boot-anim -gpu host " + $(if ($wipe) { "-no-snapshot -wipe-data" } else { "-no-snapshot-save" })
    Start-Process -FilePath $emulatorExe -ArgumentList $flags -WindowStyle Hidden
    $deadline = (Get-Date).AddMinutes(8)
    $s = $null
    while ((Get-Date) -lt $deadline -and -not $s) { Start-Sleep -Seconds 10; $s = Find-Emulator }
    return $s
}

function Get-Load { [double] ((Adb "cat /proc/loadavg") -split '\s+')[0] }

# Booted, then quiet: the 1-minute load has to drop below 6 (4 vCPUs) before tests start. An
# overloaded emulator makes every app, the launcher included, miss its startup deadline, which
# shows up as false ANRs and tests that never start.
function Wait-Healthy {
    $deadline = (Get-Date).AddMinutes(6)
    while ((Get-Date) -lt $deadline -and (Adb "getprop sys.boot_completed").Trim() -ne "1") { Start-Sleep -Seconds 5 }
    if ((Adb "getprop sys.boot_completed").Trim() -ne "1") { return $false }
    $deadline = (Get-Date).AddMinutes(8)
    while ((Get-Date) -lt $deadline) {
        if ((Get-Load) -lt 6) { return $true }
        Start-Sleep -Seconds 15
    }
    return $false
}

# ---- 1. Emulator (never a physical device) --------------------------------------------------------
$t = Get-Date
$wantFresh = ($Fresh -eq "yes") -or ($Fresh -eq "auto" -and $Tier -eq "deep")
if ($wantFresh) { Stop-Emulators }
$serial = Find-Emulator
$wasFresh = $false
if (-not $serial) { $serial = Start-Emulator $wantFresh; $wasFresh = $wantFresh }
$healthy = $false
if ($serial) {
    $env:ANDROID_SERIAL = $serial   # every adb/gradle device call below targets the emulator only
    $healthy = Wait-Healthy
    if (-not $healthy -and -not $wasFresh) {
        # Long-running emulators degrade (updates, caches, leftovers): one clean restart.
        Stop-Emulators
        $serial = Start-Emulator $true
        $wasFresh = $true
        if ($serial) { $env:ANDROID_SERIAL = $serial; $healthy = Wait-Healthy }
    }
}
if (-not $serial -or $serial -notlike "emulator-*" -or -not $healthy) {
    $why = if (-not $serial) { "No emulator came up (AVD $Avd)" } else { "Emulator stayed overloaded (load $(Get-Load)); tests would not be meaningful" }
    Add-Step "emulator" "ERROR" $why ((Get-Date) - $t).TotalSeconds
    $exitCode = 2
} else {
    Adb "settings put global hide_error_dialogs 1" | Out-Null   # other apps' ANR dialogs must not cover the tests
    Adb "input keyevent KEYCODE_WAKEUP" | Out-Null
    Adb "wm dismiss-keyguard" | Out-Null
    & $adb -s $serial logcat -G 16M 2>$null | Out-Null         # the 2 MB default drops lines within minutes
    $load = Get-Load
    $metrics["emulator_load"] = $load
    Add-Step "emulator" "PASS" ("{0} ({1}, API {2}){3}, load {4}" -f $serial, $Avd, (Adb 'getprop ro.build.version.sdk').Trim(), $(if ($wasFresh) { ", fresh" } else { "" }), $load) ((Get-Date) - $t).TotalSeconds
    $exitCode = 0
}

if ($exitCode -eq 0) {
    # ---- 2. Unit tests -------------------------------------------------------------------------------
    $t = Get-Date
    Get-ChildItem -Recurse -Directory -Filter "test-results" -Path app, core, feature -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '\\build\\test-results$' } | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
    $code = Invoke-Gradle "unit-tests.log" @("testDebugUnitTest", ":core:model:test", "--continue")
    $r = Get-TestResults (Get-ChildItem -Recurse -Directory -Filter "test-results" -Path app, core, feature | Where-Object { $_.FullName -match '\\build\\test-results$' } | ForEach-Object { $_.FullName })
    if ($code -eq 0) { Add-Step "unit tests" "PASS" "$($r.total) tests" ((Get-Date) - $t).TotalSeconds }
    elseif ($r.total -eq 0) { Add-Step "unit tests" "ERROR" "Build failed before tests ran (see unit-tests.log)" ((Get-Date) - $t).TotalSeconds; $exitCode = 2 }
    else { Add-Step "unit tests" "FAIL" ("{0}/{1} failed: {2}" -f $r.failed, $r.total, ($r.names -join " | ")) ((Get-Date) - $t).TotalSeconds }

    # ---- 3. Smoke tour on the emulator -----------------------------------------------------------------
    $t = Get-Date
    & $adb -s $serial logcat -c 2>$null
    Remove-Item -Recurse -Force "app\build\outputs\androidTest-results", "app\build\outputs\connected_android_test_additional_output" -ErrorAction SilentlyContinue
    $code = Invoke-Gradle "smoke-tour.log" @(":app:connectedDebugAndroidTest", "-Pandroid.testInstrumentationRunnerArguments.class=com.anda.launcher.SmokeTourTest")
    $r = Get-TestResults @("app\build\outputs\androidTest-results")
    # Step results come from the file the test writes (copied back by Gradle), not from logcat.
    $stepsFile = Get-ChildItem -LiteralPath "app\build\outputs\connected_android_test_additional_output" -Recurse -File -Filter "smoke-steps.txt" -ErrorAction SilentlyContinue | Select-Object -First 1
    $smokeLines = if ($stepsFile) { @(Get-Content -LiteralPath $stepsFile.FullName -Encoding UTF8) } else { @() }
    $smokeLines | Set-Content (Join-Path $out "smoke-steps.txt") -Encoding utf8
    $passed = @($smokeLines | Where-Object { $_ -like "PASS*" }).Count
    $failedSteps = @($smokeLines | Where-Object { $_ -like "FAIL*" })
    $metrics["smoke_steps_passed"] = $passed
    $metrics["smoke_steps_failed"] = $failedSteps.Count
    # Never a silent pass: zero tests run, or zero steps recorded, is a failure in itself.
    if ($r.total -eq 0) { Add-Step "smoke tour" "FAIL" "No test ran (instrumentation found 0 tests; see smoke-tour.log)" ((Get-Date) - $t).TotalSeconds }
    elseif ($smokeLines.Count -eq 0) { Add-Step "smoke tour" "FAIL" "The test ran but recorded no steps (app process crash?) - see smoke-tour.log" ((Get-Date) - $t).TotalSeconds }
    elseif ($code -eq 0 -and $failedSteps.Count -eq 0) { Add-Step "smoke tour" "PASS" "$passed steps" ((Get-Date) - $t).TotalSeconds }
    else { Add-Step "smoke tour" "FAIL" ("{0} passed, {1} failed: {2}" -f $passed, $failedSteps.Count, ($failedSteps -join " | ")) ((Get-Date) - $t).TotalSeconds }
    & $adb -s $serial logcat -d 2>$null | Set-Content (Join-Path $out "logcat-smoke.txt") -Encoding utf8

    # ---- 4. Deep tier extras ---------------------------------------------------------------------------
    if ($Tier -eq "deep") {
        $t = Get-Date
        $code = Invoke-Gradle "lint.log" @("lintDebug", "--continue")
        $lintErrors = (Get-Content (Join-Path $out "lint.log") | Select-String 'Lint found (\d+) error' | ForEach-Object { $_.Matches[0].Groups[1].Value }) -join ","
        if ($code -eq 0) { Add-Step "lint" "PASS" "no issues (warnings are errors)" ((Get-Date) - $t).TotalSeconds }
        else { Add-Step "lint" "FAIL" "errors: $lintErrors (see lint.log)" ((Get-Date) - $t).TotalSeconds }

        $t = Get-Date
        Remove-Item -Recurse -Force "app\build\outputs\androidTest-results", "core\data\build\outputs\androidTest-results" -ErrorAction SilentlyContinue
        $code = Invoke-Gradle "instrumented.log" @("connectedDebugAndroidTest", "--continue", "-Pandroid.testInstrumentationRunnerArguments.notClass=com.anda.launcher.SmokeTourTest")
        $r = Get-TestResults @("app\build\outputs\androidTest-results", "core\data\build\outputs\androidTest-results")
        if ($r.total -eq 0) { Add-Step "instrumented suite" "FAIL" "No test ran (see instrumented.log)" ((Get-Date) - $t).TotalSeconds }
        elseif ($code -eq 0) { Add-Step "instrumented suite" "PASS" "$($r.total) tests" ((Get-Date) - $t).TotalSeconds }
        else { Add-Step "instrumented suite" "FAIL" ("{0}/{1} failed: {2}" -f $r.failed, $r.total, ($r.names -join " | ")) ((Get-Date) - $t).TotalSeconds }

        # Monkey: random input confined to Anda. Connected tests uninstall the app, so reinstall first.
        $t = Get-Date
        [void] (Invoke-Gradle "install.log" @(":app:installDebug"))
        Adb "cmd role add-role-holder android.app.role.HOME $package 0" | Out-Null
        $monkeyIssues = @()
        for ($i = 1; $i -le $MonkeySeeds; $i++) {
            $seed = [int] (Get-Date -UFormat %j) * 100 + $i     # new seeds each day, reproducible from the report
            Adb "input keyevent KEYCODE_HOME" | Out-Null
            $log = Join-Path $out "monkey-seed-$seed.txt"
            Adb "monkey -p $package -s $seed --throttle 40 --pct-syskeys 0 --pct-appswitch 0 --ignore-security-exceptions --monitor-native-crashes -v $MonkeyEvents" | Set-Content $log -Encoding utf8
            $text = Get-Content $log -Raw
            if ($text -match '// CRASH: com\.anda\.launcher') { $monkeyIssues += "seed $seed CRASH" }
            elseif ($text -match '// NOT RESPONDING: com\.anda\.launcher') { $monkeyIssues += "seed $seed ANR" }
            elseif ($text -notmatch 'Monkey finished') { $monkeyIssues += "seed $seed aborted" }
        }
        $metrics["monkey_events"] = $MonkeySeeds * $MonkeyEvents
        $metrics["monkey_issues"] = $monkeyIssues.Count
        if ($monkeyIssues.Count -eq 0) { Add-Step "monkey" "PASS" "$MonkeySeeds x $MonkeyEvents events, no crash/ANR" ((Get-Date) - $t).TotalSeconds }
        else { Add-Step "monkey" "FAIL" ($monkeyIssues -join " | ") ((Get-Date) - $t).TotalSeconds }

        # Performance snapshot: a fixed gesture journey. Emulator numbers are for trends only;
        # the budgets in docs/RND.md are measured on the Pixel with the release build.
        $t = Get-Date
        Adb "am force-stop $package" | Out-Null
        Adb "input keyevent KEYCODE_HOME" | Out-Null
        Start-Sleep -Seconds 4
        Adb "dumpsys gfxinfo $package reset" | Out-Null
        $size = (Adb "wm size") -replace '.*: ', ''
        $w = [int] ($size.Split('x')[0]); $h = [int] ($size.Split('x')[1])
        for ($i = 0; $i -lt 5; $i++) {
            Adb ("input swipe {0} {1} {2} {1} 250" -f [int]($w * 0.8), [int]($h * 0.5), [int]($w * 0.2)) | Out-Null
            Adb ("input swipe {0} {1} {2} {1} 250" -f [int]($w * 0.2), [int]($h * 0.5), [int]($w * 0.8)) | Out-Null
            Adb ("input swipe {0} {1} {0} {2} 300" -f [int]($w * 0.5), [int]($h * 0.75), [int]($h * 0.2)) | Out-Null
            Adb ("input swipe {0} {1} {0} {2} 300" -f [int]($w * 0.5), [int]($h * 0.3), [int]($h * 0.85)) | Out-Null
            Adb "input keyevent KEYCODE_BACK" | Out-Null
        }
        $gfx = Adb "dumpsys gfxinfo $package"
        $jank = ($gfx | Select-String 'Janky frames: \d+ \(([\d.]+)%\)' | Select-Object -First 1).Matches.Groups[1].Value
        $p95 = ($gfx | Select-String '95th percentile: (\d+)ms' | Select-Object -First 1).Matches.Groups[1].Value
        $pss = ((Adb "dumpsys meminfo $package") | Select-String 'TOTAL PSS:\s+(\d+)' | Select-Object -First 1).Matches.Groups[1].Value
        $apk = Get-ChildItem "app\build\outputs\apk\debug\*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
        $metrics["jank_pct"] = $jank
        $metrics["p95_ms"] = $p95
        $metrics["pss_mb"] = if ($pss) { [math]::Round([int]$pss / 1024) } else { "" }
        $metrics["debug_apk_mb"] = if ($apk) { [math]::Round($apk.Length / 1MB, 1) } else { "" }
        Add-Step "performance snapshot" "INFO" ("jank {0}%, p95 {1} ms, PSS {2} MB, debug APK {3} MB (emulator: trend only)" -f $jank, $p95, $metrics["pss_mb"], $metrics["debug_apk_mb"]) ((Get-Date) - $t).TotalSeconds
        & $adb -s $serial logcat -d 2>$null | Set-Content (Join-Path $out "logcat-deep.txt") -Encoding utf8
    }

    # ---- 5. Log scan: Anda crashes, ANRs, StrictMode, on-device crash reports -------------------------------
    $t = Get-Date
    $allLogs = Get-ChildItem $out -Filter "logcat-*.txt" | ForEach-Object { Get-Content $_.FullName }
    $andaFatal = @($allLogs | Select-String "Process: $([regex]::Escape($package)), PID").Count
    $anrs = @($allLogs | Select-String "am_anr.*$([regex]::Escape($package))|ANR in $([regex]::Escape($package))").Count
    $strict = @($allLogs | Select-String 'StrictMode policy violation' | Where-Object { $_.Line -match 'anda' }).Count
    $reports = Adb "run-as $package ls files/crashes" | Where-Object { $_ -match '\.txt' }
    $metrics["anda_fatal"] = $andaFatal
    $metrics["anda_anr"] = $anrs
    $metrics["strictmode"] = $strict
    $metrics["crash_reports"] = @($reports).Count
    if ($reports) {
        $dir = Join-Path $out "crash-reports"; New-Item -ItemType Directory -Force $dir | Out-Null
        foreach ($r in $reports) { Adb "run-as $package cat files/crashes/$($r.Trim())" | Set-Content (Join-Path $dir $r.Trim()) -Encoding utf8 }
    }
    $detail = "fatal $andaFatal, ANR $anrs, StrictMode $strict, on-device reports $(@($reports).Count)"
    if ($andaFatal -gt 0 -or $anrs -gt 0) { Add-Step "log scan" "FAIL" $detail ((Get-Date) - $t).TotalSeconds }
    elseif ($strict -gt 0 -or @($reports).Count -gt 0) { Add-Step "log scan" "WARN" $detail ((Get-Date) - $t).TotalSeconds }
    else { Add-Step "log scan" "PASS" $detail ((Get-Date) - $t).TotalSeconds }
}

# ---- 6. Report -----------------------------------------------------------------------------------------
$failedCount = @($steps | Where-Object { $_.status -in @("FAIL", "ERROR") }).Count
$status = if ($exitCode -eq 2 -or @($steps | Where-Object { $_.status -eq "ERROR" }).Count -gt 0) { "ERROR" } elseif ($failedCount -gt 0) { "FAIL" } else { "PASS" }
if ($status -eq "FAIL") { $exitCode = 1 } elseif ($status -eq "ERROR") { $exitCode = 2 }
$duration = [math]::Round(((Get-Date) - $started).TotalMinutes, 1)

$md = @()
$md += "# Anda QA - $Tier - $status"
$md += ""
$md += "Started $($started.ToString('yyyy-MM-dd HH:mm')), took $duration min, emulator $env:ANDROID_SERIAL. Files: ``qa-reports/$stamp-$Tier/``."
$md += ""
$md += "| Step | Result | Details | Time |"
$md += "|---|---|---|---|"
foreach ($s in $steps) { $md += "| $($s.name) | $($s.status) | $($s.detail -replace '\|', '/') | $($s.seconds)s |" }
$md += ""
$md += "Metrics: " + (($metrics.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ", ")
$md | Set-Content (Join-Path $out "report.md") -Encoding utf8
$md | Set-Content (Join-Path $root "qa-reports\latest-$Tier.md") -Encoding utf8

[pscustomobject]@{
    tier = $Tier; status = $status; started = $started.ToString("o"); minutes = $duration
    reportDir = "qa-reports/$stamp-$Tier"; steps = $steps; metrics = $metrics
} | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $out "summary.json") -Encoding utf8

$history = Join-Path $root "qa-reports\history.csv"
if (-not (Test-Path $history)) { "date,tier,status,minutes,smoke_failed,monkey_issues,anda_fatal,anda_anr,strictmode,jank_pct,p95_ms,pss_mb,debug_apk_mb" | Set-Content $history -Encoding utf8 }
("{0},{1},{2},{3},{4},{5},{6},{7},{8},{9},{10},{11},{12}" -f $stamp, $Tier, $status, $duration, $metrics["smoke_steps_failed"], $metrics["monkey_issues"], $metrics["anda_fatal"], $metrics["anda_anr"], $metrics["strictmode"], $metrics["jank_pct"], $metrics["p95_ms"], $metrics["pss_mb"], $metrics["debug_apk_mb"]) | Add-Content $history -Encoding utf8

Write-Host ""
Write-Host "QA $Tier $status in $duration min - report: qa-reports\$stamp-$Tier\report.md"
exit $exitCode
