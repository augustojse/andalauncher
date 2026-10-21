<#
.SYNOPSIS
  Copies the Dev notes (Settings -> Dev) from the connected phone to dev-notes\ in the repo.

.DESCRIPTION
  Anda Launcher copies every Dev note to Downloads/Anda Launcher/dev/<id>/ on the phone: note.txt
  (text, app version, device) plus the attached screenshots and files. This pulls that folder with
  adb into dev-notes\ (git-ignored), newest notes listed last. It only reads from the phone and
  never touches emulators unless -Serial names one.
  Exit code 0 = pulled, 1 = nothing to pull, 2 = no phone.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\dev\pull-dev-notes.ps1
#>
param(
    # Serial of the device to pull from; empty = the only non-emulator device in `adb devices`.
    [string] $Serial = ""
)

$ErrorActionPreference = "Continue"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $root
$sdk = ((Get-Content local.properties | Select-String '^sdk.dir=').Line.Substring(8)) -replace '\\\\', '\' -replace '\\:', ':'
$adb = Join-Path $sdk "platform-tools\adb.exe"
$remote = "/sdcard/Download/Anda Launcher/dev"
$out = Join-Path $root "dev-notes"

if ($Serial) {
    $phone = $Serial
} else {
    $phones = @(& $adb devices | Select-String '^(\S+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value } | Where-Object { $_ -notlike "emulator-*" })
    if ($phones.Count -eq 0) { Write-Host "No phone connected over USB or wireless debugging."; exit 2 }
    if ($phones.Count -gt 1) { Write-Host "More than one phone ($($phones -join ', ')); pass -Serial."; exit 2 }
    $phone = $phones[0]
}

$listing = (& $adb -s $phone shell "ls '$remote' 2>/dev/null" | Out-String).Trim()
if (-not $listing) { Write-Host "No Dev notes on $phone yet (Settings -> Dev)."; exit 1 }

New-Item -ItemType Directory -Force $out | Out-Null
# adb pull of a folder lands as <out>\dev\...; pull its contents so notes sit directly in dev-notes\.
foreach ($id in ($listing -split "\s+" | Where-Object { $_ })) {
    # A deleted note leaves an empty folder behind (MediaStore removes files, not folders).
    $files = (& $adb -s $phone shell "ls '$remote/$id' 2>/dev/null" | Out-String).Trim()
    if (-not $files) { continue }
    & $adb -s $phone pull "$remote/$id" "$out" | Out-Null
}

$notes = Get-ChildItem $out -Directory | Sort-Object Name
Write-Host "Pulled $($notes.Count) note(s) from $phone into dev-notes\:"
foreach ($note in $notes) {
    # note.txt: header lines, a blank line, then the note itself; show its first line.
    $lines = @(Get-Content (Join-Path $note.FullName "note.txt") -ErrorAction SilentlyContinue)
    $blank = [Array]::IndexOf($lines, "")
    $first = if ($blank -ge 0) { $lines | Select-Object -Skip ($blank + 1) -First 1 } else { "" }
    $files = @(Get-ChildItem $note.FullName -File | Where-Object { $_.Name -ne "note.txt" }).Count
    Write-Host ("  {0}  {1} file(s)  {2}" -f $note.Name, $files, $first)
}
exit 0
