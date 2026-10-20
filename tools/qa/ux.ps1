<#
.SYNOPSIS
  Helpers for the weekly simulated-user UX round (docs/qa/feature-test-guide.md). EMULATOR ONLY.

.DESCRIPTION
  Every command targets an "emulator-NNNN" serial; a phone on USB or Wi-Fi debugging is never used.
  Evidence goes to the round folder given by -Out (default: newest qa-reports/*-ux).

    prepare             Wait for any run-qa.ps1 to finish, boot/verify the emulator, install the debug
                        build, reset Anda to factory state (-KeepState to skip), make it the home app,
                        reset font scale/density/theme, create qa-reports/<stamp>-ux/.
    shot   -Name X      Settled screenshot (retakes until two frames 600 ms apart match) + UI dump
                        + layout audit. Writes shots/X.png, ui/X.xml, prints the audit.
    crop   -Name X -Rect "x,y,w,h"   Enlarge a region of shots/X.png 2x (for small details).
    find   -Text T      List on-screen nodes whose text or content-desc contains T, with tap centres.
    tap    -Text T      Tap the first node matching T (-Index n for the n-th). -Long for a long press.
    audit  -Name X      Re-run the layout audit on ui/X.xml.
    logs   -Name X      Save Anda-scoped logcat + crash buffer to logs/X.txt and print error lines.
    record -Name X      30 s screen recording to shots/X.mp4 (for motion issues).
    finish              Pull on-device crash reports, restore font scale/density/theme, print summary.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\qa\ux.ps1 prepare
  powershell -ExecutionPolicy Bypass -File tools\qa\ux.ps1 shot -Name H-01-a
#>
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidateSet("prepare", "shot", "crop", "find", "tap", "audit", "logs", "record", "finish")] [string] $Command,
    # crop: "x,y,w,h" in device pixels; the crop is saved 2x enlarged as shots/<Name>-zoom.png.
    [string] $Rect = "",
    [string] $Name = "",
    [string] $Text = "",
    [int] $Index = 0,
    [switch] $Long,
    [string] $Out = "",
    [string] $Avd = "C1_Pixel_9_API_37",
    [switch] $NoInstall,
    # prepare: keep Anda's current settings and layout instead of resetting to factory state.
    [switch] $KeepState,
    # prepare: how long to wait for a running run-qa.ps1 (deep tier) to release the emulator.
    [int] $WaitForQaMinutes = 120
)

$ErrorActionPreference = "Continue"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $root
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$sdk = ((Get-Content local.properties | Select-String '^sdk.dir=').Line.Substring(8)) -replace '\\\\', '\' -replace '\\:', ':'
$adb = Join-Path $sdk "platform-tools\adb.exe"
$package = "com.anda.launcher"

function Find-Emulator { (& $adb devices | Select-String '^emulator-\d+\s+device' | ForEach-Object { ($_.Line -split '\s+')[0] }) | Select-Object -First 1 }
$serial = Find-Emulator
if (-not $serial -and $Command -ne "prepare") { Write-Error "No emulator running. Run: ux.ps1 prepare"; exit 2 }
if ($serial) { $env:ANDROID_SERIAL = $serial }   # gradle installs and every adb call hit the emulator only

function Sh([string] $c) { & $adb -s $serial shell $c 2>$null }

if (-not $Out) {
    $latest = Get-ChildItem (Join-Path $root "qa-reports") -Directory -Filter "*-ux" -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1
    if ($latest) { $Out = $latest.FullName }
}

function Get-Screen {
    $size = ((Sh "wm size") | Select-Object -Last 1) -replace '.*: ', ''
    $dens = [int] (((Sh "wm density") | Select-Object -Last 1) -replace '.*: ', '')
    [pscustomobject]@{ w = [int] $size.Split('x')[0]; h = [int] $size.Split('x')[1]; density = $dens; dp = $dens / 160.0 }
}

# System bar heights in px, from the window manager's inset sources (fallbacks: 24 dp / 24 dp).
function Get-Bars($scr) {
    $dump = Sh "dumpsys window windows"
    $status = ($dump | Select-String 'type=statusBars.*?frame=\[\d+,\d+\]\[\d+,(\d+)\]' | Select-Object -First 1)
    $nav = ($dump | Select-String 'type=navigationBars.*?frame=\[\d+,(\d+)\]\[\d+,\d+\]' | Select-Object -First 1)
    $top = if ($status) { [int] $status.Matches[0].Groups[1].Value } else { [int] (24 * $scr.dp) }
    $bottomStart = if ($nav) { [int] $nav.Matches[0].Groups[1].Value } else { $scr.h - [int] (24 * $scr.dp) }
    [pscustomobject]@{ statusBottom = $top; navTop = $bottomStart }
}

function Get-Nodes([string] $xmlPath) {
    [xml] $x = Get-Content -LiteralPath $xmlPath -Raw -Encoding UTF8
    foreach ($n in $x.SelectNodes('//node')) {
        if ($n.bounds -notmatch '\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]') { continue }
        $l = [int] $Matches[1]; $t = [int] $Matches[2]; $r = [int] $Matches[3]; $b = [int] $Matches[4]
        [pscustomobject]@{
            text = $n.text; desc = $n.'content-desc'; pkg = $n.package; cls = $n.class
            clickable = ($n.clickable -eq 'true' -or $n.'long-clickable' -eq 'true'); scrollable = ($n.scrollable -eq 'true')
            l = $l; t = $t; r = $r; b = $b; cx = [int](($l + $r) / 2); cy = [int](($t + $b) / 2)
        }
    }
}

function Save-Ui([string] $path) {
    for ($i = 0; $i -lt 3; $i++) {
        Sh "uiautomator dump --compressed /sdcard/ux.xml" | Out-Null
        & $adb -s $serial pull /sdcard/ux.xml $path 2>$null | Out-Null
        if ((Test-Path $path) -and (Get-Item $path).Length -gt 200) { return $true }
        Start-Sleep -Milliseconds 700
    }
    return $false
}

function Label($n) { if ($n.text) { $n.text } elseif ($n.desc) { $n.desc } else { "<$($n.cls.Split('.')[-1])>" } }

# Heuristic layout audit. Every line is a LEAD to verify against the screenshot, not a finding.
function Invoke-Audit([string] $xmlPath) {
    $scr = Get-Screen; $bars = Get-Bars $scr
    $nodes = @(Get-Nodes $xmlPath | Where-Object { $_.pkg -eq $package -and $_.r -gt $_.l -and $_.b -gt $_.t })
    $leads = @()
    $min = [int] (48 * $scr.dp) - 2
    foreach ($n in $nodes) {
        $w = $n.r - $n.l; $h = $n.b - $n.t
        if ($n.clickable -and ($w -lt $min -or $h -lt $min) -and ($n.text -or $n.desc)) {
            $leads += "TOUCH   '$(Label $n)' is {0}x{1} dp (< 48 dp)" -f [math]::Round($w / $scr.dp), [math]::Round($h / $scr.dp)
        }
        if (($n.text -or $n.desc) -and ($n.l -lt 0 -or $n.r -gt $scr.w -or $n.b -gt $scr.h -or $n.t -lt 0)) {
            $leads += "OFFSCR  '$(Label $n)' bounds [$($n.l),$($n.t)][$($n.r),$($n.b)] outside $($scr.w)x$($scr.h)"
        }
        if ($n.text -and $n.t -lt $bars.statusBottom -and $n.b -gt 0) {
            $leads += "STATUS  '$(Label $n)' top $($n.t) px is under the status bar (ends $($bars.statusBottom) px)"
        }
        if (($n.text -or $n.clickable) -and $n.b -gt $bars.navTop -and $n.t -lt $scr.h -and -not $n.scrollable) {
            $leads += "NAVBAR  '$(Label $n)' bottom $($n.b) px reaches the navigation bar (starts $($bars.navTop) px)"
        }
    }
    # Overlapping text: two different text-bearing leaf nodes whose boxes intersect by > 30 % of the smaller.
    $texts = @($nodes | Where-Object { $_.text })
    for ($i = 0; $i -lt $texts.Count; $i++) {
        for ($j = $i + 1; $j -lt $texts.Count; $j++) {
            $a = $texts[$i]; $b = $texts[$j]
            $ix = [math]::Min($a.r, $b.r) - [math]::Max($a.l, $b.l); $iy = [math]::Min($a.b, $b.b) - [math]::Max($a.t, $b.t)
            if ($ix -le 0 -or $iy -le 0) { continue }
            $small = [math]::Min(($a.r - $a.l) * ($a.b - $a.t), ($b.r - $b.l) * ($b.b - $b.t))
            $contained = ($a.l -le $b.l -and $a.r -ge $b.r -and $a.t -le $b.t -and $a.b -ge $b.b) -or ($b.l -le $a.l -and $b.r -ge $a.r -and $b.t -le $a.t -and $b.b -ge $a.b)
            if (-not $contained -and ($ix * $iy) -gt 0.3 * $small) { $leads += "OVERLAP '$($a.text)' and '$($b.text)' intersect" }
        }
    }
    $leads = @($leads | Select-Object -Unique)
    if ($leads.Count -eq 0) { Write-Host "audit: no layout leads ($($nodes.Count) Anda nodes)" }
    else { Write-Host "audit: $($leads.Count) lead(s) to VERIFY on the screenshot:"; $leads | ForEach-Object { Write-Host "  $_" } }
}

function Get-ScreenHash([string] $path) {
    # PowerShell 5.1 '>' re-encodes binary output; cmd's redirect keeps the PNG byte-exact.
    cmd /c "`"$adb`" -s $serial exec-out screencap -p > `"$path`"" | Out-Null
    (Get-FileHash $path -Algorithm MD5).Hash
}

switch ($Command) {
    "prepare" {
        # The deep tier (run-qa.ps1) wipes and restarts the emulator: sharing it corrupts both runs.
        $deadline = (Get-Date).AddMinutes($WaitForQaMinutes)
        while ($true) {
            $qa = @(Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" | Where-Object { $_.CommandLine -match 'run-qa\.ps1' })
            if ($qa.Count -eq 0) { break }
            if ((Get-Date) -gt $deadline) { Write-Error "run-qa.ps1 (PID $($qa[0].ProcessId)) still owns the emulator after $WaitForQaMinutes min"; exit 3 }
            Write-Host "waiting: run-qa.ps1 (PID $($qa[0].ProcessId)) is using the emulator"
            Start-Sleep 60
        }
        $serial = Find-Emulator
        if (-not $serial) {
            $emu = Join-Path $sdk "emulator\emulator.exe"
            Start-Process -FilePath $emu -ArgumentList "-avd $Avd -no-audio -no-boot-anim -gpu host -no-snapshot-save" -WindowStyle Minimized
            $deadline = (Get-Date).AddMinutes(8)
            while ((Get-Date) -lt $deadline -and -not $serial) { Start-Sleep 10; $serial = Find-Emulator }
            if (-not $serial) { Write-Error "Emulator $Avd did not come up"; exit 2 }
            $env:ANDROID_SERIAL = $serial
        }
        $deadline = (Get-Date).AddMinutes(6)
        while ((Get-Date) -lt $deadline -and (Sh "getprop sys.boot_completed").Trim() -ne "1") { Start-Sleep 5 }
        $deadline = (Get-Date).AddMinutes(8)
        while ((Get-Date) -lt $deadline -and [double] ((Sh "cat /proc/loadavg") -split '\s+')[0] -ge 6) { Start-Sleep 15 }
        $load = ((Sh "cat /proc/loadavg") -split '\s+')[0]
        if (-not $NoInstall) {
            & .\gradlew.bat :app:installDebug --console=plain *> (Join-Path $env:TEMP "anda-ux-install.log")
            if ($LASTEXITCODE -ne 0) { Write-Error "installDebug failed, see $env:TEMP\anda-ux-install.log"; exit 2 }
        }
        # Known baseline: factory settings and default layout (the first Home shows the Welcome card, case P-01).
        if (-not $KeepState) { Sh "pm clear $package" | Out-Null }
        Sh "cmd role add-role-holder android.app.role.HOME $package 0" | Out-Null
        Sh "settings put global hide_error_dialogs 1" | Out-Null
        Sh "settings put system font_scale 1.0" | Out-Null
        Sh "wm density reset" | Out-Null
        Sh "cmd uimode night no" | Out-Null
        Sh "settings put system screen_off_timeout 1800000" | Out-Null
        Sh "input keyevent KEYCODE_WAKEUP" | Out-Null
        Sh "wm dismiss-keyguard" | Out-Null
        & $adb -s $serial logcat -G 16M 2>$null | Out-Null
        & $adb -s $serial logcat -c 2>$null
        $stamp = Get-Date -Format "yyyy-MM-dd_HHmm"
        $Out = Join-Path $root "qa-reports\$stamp-ux"
        foreach ($d in "shots", "ui", "logs") { New-Item -ItemType Directory -Force (Join-Path $Out $d) | Out-Null }
        Sh "input keyevent KEYCODE_HOME" | Out-Null; Start-Sleep 2; Sh "input keyevent KEYCODE_HOME" | Out-Null
        $scr = Get-Screen
        $ver = (Sh "dumpsys package $package" | Select-String 'versionName=' | Select-Object -First 1).Line.Trim()
        Write-Host "READY $serial API $((Sh 'getprop ro.build.version.sdk').Trim()) $($scr.w)x$($scr.h) @$($scr.density)dpi load $load $ver"
        Write-Host "OUT $Out"
    }
    "shot" {
        if (-not $Name) { Write-Error "-Name required"; exit 1 }
        $png = Join-Path $Out "shots\$Name.png"; $tmp = Join-Path $Out "shots\.settle.png"
        $prev = ""; $settled = $false
        for ($i = 0; $i -lt 6; $i++) {
            Start-Sleep -Milliseconds 600
            $h = Get-ScreenHash $tmp
            if ($h -eq $prev) { $settled = $true; break }
            $prev = $h
        }
        Move-Item -Force $tmp $png
        $xml = Join-Path $Out "ui\$Name.xml"
        $ok = Save-Ui $xml
        Write-Host ("shot {0} ({1})" -f $png, $(if ($settled) { "settled" } else { "STILL ANIMATING after 6 tries: a moving element or a looping animation" }))
        if ($ok) { Invoke-Audit $xml } else { Write-Host "ui dump failed (screen idle never reached?)" }
    }
    "audit" { Invoke-Audit (Join-Path $Out "ui\$Name.xml") }
    "crop" {
        Add-Type -AssemblyName System.Drawing
        $v = $Rect.Split(',') | ForEach-Object { [int] $_ }
        $src = [System.Drawing.Image]::FromFile((Join-Path $Out "shots\$Name.png"))
        $dst = New-Object System.Drawing.Bitmap ($v[2] * 2), ($v[3] * 2)
        $g = [System.Drawing.Graphics]::FromImage($dst)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $g.DrawImage($src, (New-Object System.Drawing.Rectangle 0, 0, ($v[2] * 2), ($v[3] * 2)), (New-Object System.Drawing.Rectangle $v[0], $v[1], $v[2], $v[3]), [System.Drawing.GraphicsUnit]::Pixel)
        $zoom = Join-Path $Out "shots\$Name-zoom.png"
        $dst.Save($zoom, [System.Drawing.Imaging.ImageFormat]::Png)
        $g.Dispose(); $dst.Dispose(); $src.Dispose()
        Write-Host "zoom $zoom"
    }
    { $_ -in "find", "tap" } {
        $xml = Join-Path $env:TEMP "anda-ux-find.xml"
        if (-not (Save-Ui $xml)) { Write-Error "ui dump failed"; exit 1 }
        $hits = @(Get-Nodes $xml | Where-Object { ($_.text -and $_.text -like "*$Text*") -or ($_.desc -and $_.desc -like "*$Text*") })
        if ($Command -eq "find") {
            if ($hits.Count -eq 0) { Write-Host "no node matches '$Text'" }
            $i = 0; foreach ($n in $hits) { Write-Host ("[{0}] '{1}' center {2},{3} bounds [{4},{5}][{6},{7}]{8}" -f $i, (Label $n), $n.cx, $n.cy, $n.l, $n.t, $n.r, $n.b, $(if ($n.clickable) { " clickable" } else { "" })); $i++ }
        } else {
            if ($hits.Count -le $Index) { Write-Error "no node #$Index matches '$Text'"; exit 1 }
            $n = $hits[$Index]
            if ($Long) { Sh ("input swipe {0} {1} {0} {1} 900" -f $n.cx, $n.cy) | Out-Null } else { Sh ("input tap {0} {1}" -f $n.cx, $n.cy) | Out-Null }
            Write-Host ("{0} '{1}' at {2},{3}" -f $(if ($Long) { "long-pressed" } else { "tapped" }), (Label $n), $n.cx, $n.cy)
        }
    }
    "logs" {
        $f = Join-Path $Out "logs\$(if ($Name) { $Name } else { 'round' }).txt"
        $andaPid = (Sh "pidof $package").Trim()
        $lines = & $adb -s $serial logcat -d -v time 2>$null
        $crash = & $adb -s $serial logcat -d -b crash -v time 2>$null
        ($lines + "---- crash buffer ----" + $crash) | Set-Content $f -Encoding utf8
        $bad = @($lines | Where-Object { ($andaPid -and $_ -match "\(\s*$andaPid\)" -and $_ -match '^\S+ \S+ [EF]/') -or $_ -match "ANR in $package|Process: $package|FATAL EXCEPTION" })
        $bad += @($crash | Where-Object { $_ -match $package })
        Write-Host "logs -> $f ; Anda error/fatal lines: $($bad.Count)"
        $bad | Select-Object -First 15 | ForEach-Object { Write-Host "  $_" }
    }
    "record" {
        Sh "screenrecord --time-limit 30 /sdcard/ux.mp4" | Out-Null
        & $adb -s $serial pull /sdcard/ux.mp4 (Join-Path $Out "shots\$Name.mp4") 2>$null | Out-Null
        Write-Host "recorded shots\$Name.mp4"
    }
    "finish" {
        $reports = Sh "run-as $package ls files/crashes" | Where-Object { $_ -match '\.txt' }
        if ($reports) {
            $dir = Join-Path $Out "logs\crash-reports"; New-Item -ItemType Directory -Force $dir | Out-Null
            foreach ($r in $reports) { Sh "run-as $package cat files/crashes/$($r.Trim())" | Set-Content (Join-Path $dir $r.Trim()) -Encoding utf8 }
        }
        Sh "settings put system font_scale 1.0" | Out-Null
        Sh "wm density reset" | Out-Null
        Sh "cmd uimode night no" | Out-Null
        Write-Host "finish: on-device crash reports $(@($reports).Count); shots $(@(Get-ChildItem (Join-Path $Out 'shots') -Filter *.png).Count); folder $Out"
    }
}
