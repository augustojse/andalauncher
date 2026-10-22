<#
.SYNOPSIS
  Clones or updates the launcher source trees in inspiration\repos (git-ignored).

.DESCRIPTION
  Each repo is a shallow clone (depth 1) of its default branch, used only for reading.
  A missing repo is cloned; an existing one is fast-forwarded. The commit each one ends on is
  printed, so the snapshot table in inspiration\README.md can be updated.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File inspiration\fetch-repos.ps1
#>

$ErrorActionPreference = "Stop"
$repos = [ordered]@{
    "lawnchair" = "https://github.com/LawnchairLauncher/lawnchair.git"
    "kvaesitso" = "https://github.com/MM2-0/Kvaesitso.git"
}
$out = Join-Path $PSScriptRoot "repos"
New-Item -ItemType Directory -Force $out | Out-Null

foreach ($name in $repos.Keys) {
    $dir = Join-Path $out $name
    if (Test-Path (Join-Path $dir ".git")) {
        Write-Host "Updating $name..."
        git -C $dir fetch --depth 1 origin
        git -C $dir reset --hard FETCH_HEAD
    } else {
        Write-Host "Cloning $name..."
        git clone --depth 1 $repos[$name] $dir
    }
    $head = git -C $dir log -1 --format="%h (%cs)"
    Write-Host "$name is at $head"
}
